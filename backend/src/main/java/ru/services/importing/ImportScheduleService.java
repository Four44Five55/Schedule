package ru.services.importing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Auditorium;
import ru.entity.StudyPeriod;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.PlacementSource;
import ru.enums.SessionStatus;
import ru.repository.AuditoriumRepository;
import ru.repository.StudyPeriodRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;
import ru.services.ScheduleSynchronizer;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ImportPlanService.PlanResolution;
import ru.services.importing.ImportPlanService.ResolvedLesson;
import ru.services.session.ScheduleSessionGate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Запись импортированного расписания в новую сессию.
 *
 * <h2>Что здесь НЕ делается — и почему</h2>
 * <p>Форма строки и дверь взяты у ручной раскладки ({@code ManualPlacementService.place}), а
 * проверки — нет. Разница принципиальная: <b>импорт фиксирует факт, а не просит разрешения</b>
 * (И-9). В живых данных 179 ячеек с двойным бронированием и 366 занятий с перебором вместимости —
 * это привезённая боль, и показать её должны датчики, а не отвергнуть запись.</p>
 *
 * <table>
 *   <caption>Чем запись импорта отличается от ручной раскладки</caption>
 *   <tr><th>Что</th><th>Ручная раскладка</th><th>Импорт</th></tr>
 *   <tr><td>Сессия</td><td>дверь на каждый вызов</td><td>та же дверь, <b>один раз на прогон</b></td></tr>
 *   <tr><td>Проверка места</td><td>{@code findPlacementOption} → 409</td><td>не спрашиваем вовсе</td></tr>
 *   <tr><td>Workspace</td><td>пересоздаётся (120–165 мс)</td><td>не создаём: он нужен только проверкам</td></tr>
 *   <tr><td>Аудитория</td><td>подбирается</td><td>берётся из файла</td></tr>
 *   <tr><td>Запись</td><td>по одной</td><td>{@code saveAll} пачками</td></tr>
 *   <tr><td>События</td><td>на каждое размещение</td><td>ни одного: проекция — одним проходом</td></tr>
 * </table>
 *
 * <h2>Сессия создаётся ПОСЛЕ разбора</h2>
 * <p>Разбор и сведение — чистые функции без базы, и падают они до всякой записи (И-15). Так пустых
 * импортных сессий не бывает в принципе, а признак «сессия импортная» — это её размещения с
 * {@code source = IMPORTED}, а не отдельная колонка, которая может соврать.</p>
 *
 * <h2>Проекция — параметр, а не умолчание</h2>
 * <p>Увидеть импорт в обычной сетке можно только спроецировав его. В отдельном (экспериментальном)
 * периоде это безопасно и полезно (И-15). В живом — <b>нельзя</b>, пока {@code schedule_view} не
 * несёт {@code session_id}: две сессии одного периода смешаются в {@code /query/all}, а
 * {@code deleteByPeriod} при генерации снесёт строки импорта. Поэтому решает вызывающий, а
 * умолчание — не проецировать.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportScheduleService {

    private final ImportPlanService planService;
    private final StudyPeriodRepository periodRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final ScheduleSessionRepository sessionRepository;
    private final LessonPlacementRepository placementRepository;
    private final ScheduleSessionGate sessionGate;
    private final ScheduleSynchronizer synchronizer;

    /** Размер пачки записи: round-trip на каждое размещение съел бы прогон на тысячах занятий. */
    private static final int BATCH = 500;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /**
     * Пишет план и расписание одним проходом.
     *
     * @param sheets     разобранные файлы
     * @param periodId   период импорта — обязателен: от него семестр курса и границы выбросов
     * @param locationId локация прогона; {@code null} — комнаты не разрешаются вовсе, и всё встанет
     *                   без аудиторий (И-17: корпус «3» законно существует в нескольких кампусах)
     * @param style      написание номера группы
     * @param project    писать ли read-модель (см. «Проекция — параметр»)
     * @param user       автор (аудит размещений)
     */
    @Transactional
    public ScheduleWriteReport write(List<ParsedSheet> sheets, Integer periodId, Integer locationId,
                                     SuffixStyle style, boolean project, String user) {
        StudyPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Периода с id " + periodId + " нет"));

        // Сначала план — размещение ссылается на назначение, а не на дисциплину и группу.
        PlanResolution plan = planService.createForSchedule(sheets, periodId, style);

        ScheduleSession session = openSession(period, user);
        List<Auditorium> rooms = auditoriumRepository.findAll();

        List<LessonPlacement> batch = new ArrayList<>(BATCH);
        int written = 0;
        int withoutRoom = 0;
        int outsidePeriod = 0;

        for (ResolvedLesson resolved : plan.resolved()) {
            MergedLesson lesson = resolved.lesson();
            if (lesson.outsidePeriod()) {
                // И-8: чужое «не смог разместить» заменяется нашим родным состоянием —
                // неразмещённым занятием. План у него есть, места нет; доска покажет его в очереди.
                outsidePeriod++;
                continue;
            }

            LessonPlacement placement = new LessonPlacement(resolved.assignment(),
                    lesson.date(), lesson.slot(), session, user, PlacementSource.IMPORTED, true);
            Set<Auditorium> assigned = resolve(rooms, lesson.rooms(), locationId);
            if (assigned.isEmpty()) {
                withoutRoom++;
            } else {
                placement.getAssignedAuditoriums().addAll(assigned);
            }

            batch.add(placement);
            written++;
            if (batch.size() >= BATCH) {
                placementRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            placementRepository.saveAll(batch);
        }

        int projected = project ? synchronizer.reprojectSession(session.getId()) : 0;

        log.info("📥 Импорт расписания в сессию {} ({}): размещений {}, без аудитории {}, "
                        + "вне периода {}, не разрешено {}, спроецировано {}",
                session.getId(), session.getName(), written, withoutRoom, outsidePeriod,
                plan.report().lessons() - plan.report().resolved(), projected);

        return new ScheduleWriteReport(session.getId(), session.getName(), written, withoutRoom,
                outsidePeriod, plan.report().lessons() - plan.report().resolved(), projected,
                plan.report(), plan.report().blockers());
    }

    /**
     * Новая сессия прогона, взятая на запись через единую дверь.
     *
     * <p>Дверь здесь не формальность: путей записи в {@code lesson_placement} было восемь, и импорт
     * не должен стать девятым (И-9). Она же поднимает поколение сессии на коммите — иначе открытые
     * вкладки и будущий кэш workspace не узнали бы, что расписание появилось.</p>
     *
     * <p>Сессия <b>всегда новая</b>: живое расписание не двигается, а неудачный прогон сносится
     * одной командой.</p>
     */
    private ScheduleSession openSession(StudyPeriod period, String user) {
        ScheduleSession session = new ScheduleSession(
                "Импорт: " + period.getName() + " · " + LocalDateTime.now().format(STAMP), user);
        session.setStudyPeriod(period);
        session.updateStatus(SessionStatus.READY_FOR_EDIT, user);
        ScheduleSession saved = sessionRepository.save(session);
        return sessionGate.forWrite(saved.getId(), null);
    }

    /**
     * Комнаты занятия: из файла, а не подбором.
     *
     * <p>Несколько комнат на одном занятии — законный случай (§9), поэтому берём все, что
     * разрешились. Неразрешённая комната занятие <b>не отменяет</b>: время в нём верное, а пустая
     * аудитория честнее чужой — её покажет и датчик, и отчёт прогона.</p>
     */
    private Set<Auditorium> resolve(List<Auditorium> all, List<String> raw, Integer locationId) {
        Set<Auditorium> found = new LinkedHashSet<>();
        if (locationId == null) {
            return found;
        }
        for (String room : raw) {
            Optional<Auditorium> resolved = AuditoriumResolver.unique(all, room, locationId);
            resolved.ifPresent(found::add);
        }
        return found;
    }
}
