package ru.services.importing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.StudyStream;
import ru.entity.write.ScheduleSession;
import ru.enums.PlacementSource;
import ru.repository.AssignmentRepository;
import ru.repository.CurriculumSlotRepository;
import ru.repository.DisciplineCourseRepository;
import ru.repository.StudyStreamRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Откат импорта: снос сессий и снос плана периода.
 *
 * <h2>Почему откат — часть записи, а не «потом»</h2>
 * <p>Первый прогон заведомо кривой, а без отката второго не будет: имя потока уникально
 * <b>глобально</b>, и повтор упрётся в занятое. Поэтому откат пишется вместе с записью, а не
 * когда-нибудь после (И-15, И-18).</p>
 *
 * <h2>Две команды, а не одна — и это не деление ради удобства</h2>
 * <ul>
 *   <li><b>Снести сессию</b> ({@code DELETE /sessions/{id}}, уже существует) — уносит размещения
 *       каскадом и строки проекции (FK миграции 017). Расписание исчезает, <b>план остаётся</b>.
 *       Этого хватает, чтобы переложить расписание, не переводя справочники.</li>
 *   <li><b>Снести курсы периода</b> — уносит план: курс → слот → назначение → размещение (все три
 *       FK каскадные). Нужен, когда кривым оказался сам разбор плана, а не раскладка.</li>
 * </ul>
 *
 * <p><b>Правило И-9 это не нарушает.</b> Снос курсов — операция над планом, а не девятый путь
 * записи в {@code lesson_placement}: размещения уходят каскадом схемы, а не нашим кодом.</p>
 *
 * <h2>Цена называется заранее</h2>
 * <p>{@link #impact(Integer)} считает, что именно исчезнет. Это то же правило, что у удаления
 * аудитории и подразделения: {@code RESTRICT} и каскады без названной заранее цены превращаются
 * либо в сырой 500, либо в тихо снесённое расписание.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportRollbackService {

    private final DisciplineCourseRepository courseRepository;
    private final CurriculumSlotRepository slotRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudyStreamRepository streamRepository;
    private final LessonPlacementRepository placementRepository;
    private final ScheduleSessionRepository sessionRepository;

    /**
     * Что исчезнет при сносе плана периода.
     *
     * @param periodId учебный период
     */
    @Transactional(readOnly = true)
    public RollbackImpact impact(Integer periodId) {
        return new RollbackImpact(
                courseRepository.countByStudyPeriodId(periodId),
                slotRepository.countByDisciplineCourse_StudyPeriod_Id(periodId),
                assignmentRepository.countByCurriculumSlot_DisciplineCourse_StudyPeriod_Id(periodId),
                placementRepository.countByPeriod(periodId),
                streamRepository.findOrphans().size());
    }

    /**
     * Сносит план периода целиком — и убирает за собой осиротевшие потоки.
     *
     * <p>Порядок обязателен: сначала курсы (каскад уносит слоты, назначения и размещения), потом
     * потоки. Наоборот нельзя — под {@code RESTRICT} поток, на который ещё ссылается назначение,
     * не удалится, и уборка молча ничего не сделает.</p>
     *
     * <p><b>Дисциплины остаются.</b> Дисциплина без курсов безвредна и удаляема обычным путём, а
     * снос её отсюда был бы правкой справочника из команды отката — не то же самое, о чём просили.
     * Так же остаются преподаватели, группы, аудитории и подразделения: их заводил другой шаг, и
     * убирать их должен тоже он.</p>
     *
     * @param periodId учебный период
     * @return что фактически снесено
     */
    @Transactional
    public RollbackImpact rollbackPlan(Integer periodId) {
        RollbackImpact planned = impact(periodId);

        List<DisciplineCourse> courses = courseRepository.findByStudyPeriodId(periodId);
        courseRepository.deleteAll(courses);
        // Flush до уборки потоков: пока удаление курсов не доехало до базы, назначения ещё видны,
        // и «поток без назначений» не нашёлся бы ни один.
        courseRepository.flush();

        List<StudyStream> orphans = streamRepository.findOrphans();
        streamRepository.deleteAll(orphans);

        log.info("🧹 Откат плана периода {}: курсов {}, слотов {}, назначений {}, размещений {}, "
                        + "осиротевших потоков {}",
                periodId, planned.courses(), planned.slots(), planned.assignments(),
                planned.placements(), orphans.size());

        return new RollbackImpact(planned.courses(), planned.slots(), planned.assignments(),
                planned.placements(), orphans.size());
    }

    /**
     * Импортные сессии периода: те, в которых есть размещения с {@code source = IMPORTED}.
     *
     * <p>Без этого списка кнопка удаления теряет объект после перезагрузки вкладки: id сессии жил
     * только в состоянии фронта, а сессия оставалась в базе (И-15).</p>
     */
    @Transactional(readOnly = true)
    public List<ImportedSession> importedSessions(Integer periodId) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Object[] row : placementRepository.countBySourceAndPeriod(PlacementSource.IMPORTED, periodId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        List<ImportedSession> found = new ArrayList<>();
        for (ScheduleSession session : sessionRepository.findAllById(counts.keySet())) {
            found.add(new ImportedSession(session.getId(), session.getName(),
                    session.getCreatedAt(), counts.getOrDefault(session.getId(), 0L)));
        }
        found.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
        return found;
    }

    /**
     * Цена отката плана периода.
     *
     * @param courses      курсов периода
     * @param slots        слотов под ними
     * @param assignments  назначений
     * @param placements   размещений — то есть занятий расписания, которые исчезнут
     * @param orphanStreams потоков, которые останутся без назначений и будут убраны
     */
    public record RollbackImpact(long courses, long slots, long assignments, long placements,
                                 long orphanStreams) {
    }

    /**
     * Импортная сессия периода.
     *
     * @param placements сколько размещений с {@code source = IMPORTED} в ней лежит
     */
    public record ImportedSession(UUID id, String name, java.time.LocalDateTime createdAt, long placements) {
    }
}
