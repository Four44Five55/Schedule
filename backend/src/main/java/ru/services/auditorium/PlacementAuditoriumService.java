package ru.services.auditorium;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.constraints.ConstraintKindRef;
import ru.dto.auditorium.AuditoriumOptionDto;
import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.events.PlacementChangedEvent;
import ru.exceptions.LessonMoveConflictException;
import ru.exceptions.NotFoundException;
import ru.repository.write.LessonPlacementRepository;
import ru.services.WorkspaceRecreationService;
import ru.services.session.ScheduleSessionGate;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.model.AuditoriumResource;
import ru.services.workspace.WorkspaceProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Смена аудитории у стоящего занятия — вручную, решением диспетчера (Command Side).
 *
 * <p><b>Зачем понадобилось.</b> Комнату нельзя было выбрать вообще: её всегда и только назначал
 * алгоритм. Диспетчер мог двигать занятие во времени, закреплять, снимать — но не сказать «пусть
 * идёт в 205-3». При этом контракт делал вид, что операция есть: {@code MoveLessonRequest} несёт
 * поле {@code newAuditoriumIds}, фронт его шлёт, а бэк игнорирует.</p>
 *
 * <p><b>Почему набор комнат, а не одна.</b> У занятия их может быть несколько, и случаи разной
 * природы: экзамен с рассадкой по двум аудиториям, деление группы на полупотоки, «возможны ещё
 * другие». Вывести число автоматически нельзя — из вместимости следует только «не влезли», а
 * рассадка на экзамене к размеру группы отношения не имеет. Поэтому число комнат называет
 * человек, а система проверяет физику. Это же снимает вопрос, который иначе пришлось бы решать
 * гаданием: {@code requiredAuditoriumCount = 1} в подборе остаётся правдой про <i>автоматику</i>,
 * а не про домен.</p>
 *
 * <p><b>Что проверяется, а что нет.</b> Занята — нельзя, это физика (проверяет
 * {@link #applyRooms}). Тесно — можно, это суждение: перебор на пару человек рабочая ситуация, и
 * в живой базе таких 134 занятия. Никакого флага «подтверждаю тесноту» нет намеренно: тесноту
 * видно в списке вариантов ДО выбора, а второе подтверждение того же самого — шум.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementAuditoriumService {

    private final LessonPlacementRepository placementRepo;
    private final WorkspaceProvider workspaceProvider;
    private final WorkspaceRecreationService workspaceRecreationService;
    private final ScheduleSessionGate sessionGate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Все комнаты со статусом для этого занятия — годные первыми.
     *
     * <p>Отдаём и занятые тоже: диспетчеру нужно понимать, ПОЧЕМУ нельзя, а не обнаруживать
     * отсутствие строки. Занявшего называем по имени.</p>
     *
     * @param placementId размещение
     * @return все комнаты со статусом; свободные и вмещающие — сверху
     */
    @Transactional(readOnly = true)
    public List<AuditoriumOptionDto> options(UUID placementId) {
        LessonPlacement placement = placementRepo.findById(placementId)
                .orElseThrow(() -> new NotFoundException("Размещение не найдено: " + placementId));

        CellForLesson cell = new CellForLesson(placement.getScheduledDate(), placement.getScheduledSlot());
        int headcount = placement.getAssignment().getStudyStream().calculateTotalSize();
        Set<Integer> currentIds = placement.getAssignedAuditoriums().stream()
                .map(Auditorium::getId)
                .collect(java.util.stream.Collectors.toSet());

        // Снимок берём через провайдер — как подбор ячеек и палитра раскладки. Это чтение, и
        // пересоздавать ради него workspace (≈300 мс, ~70 SQL) незачем: диспетчер открывает выбор
        // комнаты ровно в том же состоянии сессии, в котором только что смотрел «куда перенести».
        // Наружу отдаём готовые DTO, а не сам workspace: снимок общий и изменяемый, поэтому
        // провайдер одалживает его строго на время вызова.
        return workspaceProvider.withWorkspaceOfPlacement(placementId, recreated -> {
            ScheduleWorkspace workspace = recreated.workspace();
            Lesson targetLesson = recreated.lessonByPlacementId().get(placementId);
            if (targetLesson == null) {
                // Размещение мы уже прочитали выше, значит оно есть, а занятия для него нет —
                // это испорченное состояние, а не «комнат не нашлось». Пустой список тут был бы
                // утверждением «выбирать не из чего», по которому человек принимает решение.
                throw new IllegalStateException("Не удалось восстановить занятие для размещения " + placementId);
            }

            List<AuditoriumOptionDto> options = new ArrayList<>();
            for (AuditoriumResource room : workspace.getResourceManager().allAuditoriumResources()) {
                options.add(describe(room, cell, targetLesson, headcount, currentIds.contains(room.getId())));
            }

            // Годные первыми, среди годных — куда влезают, потом меньшая из достаточных. Тот же
            // порядок предпочтения, что у автоматического подбора: человеку и машине незачем
            // расходиться в том, какая комната лучше.
            options.sort(Comparator
                    .comparingInt((AuditoriumOptionDto o) -> o.status() == AuditoriumOptionDto.Status.FREE ? 0 : 1)
                    .thenComparingInt(AuditoriumOptionDto::shortfall)
                    .thenComparingInt(AuditoriumOptionDto::capacity)
                    .thenComparing(AuditoriumOptionDto::name, Comparator.nullsLast(String::compareTo)));
            return options;
        });
    }

    /**
     * Текущая версия сессии-владельца — для тела 409, чтобы клиент мог освежиться и повторить.
     *
     * <p>Нужен потому, что команда принимает только {@code placementId}: сессию клиент не
     * присылает и не должен (надёжный якорь — размещение, см. {@code LessonMoveService}).
     * Живёт здесь, а не в контроллере, чтобы тот не ходил в репозиторий сам.</p>
     *
     * @param placementId размещение
     * @return версия сессии или {@code null}, если размещения уже нет
     */
    @Transactional(readOnly = true)
    public Long currentSessionVersion(UUID placementId) {
        return placementRepo.findById(placementId)
                .map(p -> p.getSession().getVersion())
                .orElse(null);
    }

    /**
     * Назначить занятию комнаты вместо нынешних.
     *
     * @param placementId     размещение
     * @param auditoriumIds   новые комнаты (одна или несколько); пустой набор запрещён
     * @param expectedVersion версия сессии (optimistic lock)
     * @param user            автор (для аудита)
     * @return сессия-владелец с новой версией
     * @throws LessonMoveConflictException комната занята / набор пуст / комнаты нет
     */
    @Transactional
    public ScheduleSession changeAuditoriums(UUID placementId, Set<Integer> auditoriumIds,
                                             Long expectedVersion, String user) {
        if (auditoriumIds == null || auditoriumIds.isEmpty()) {
            throw new LessonMoveConflictException("занятию нужна хотя бы одна аудитория");
        }

        LessonPlacement placement = placementRepo.findById(placementId)
                .orElseThrow(() -> new NotFoundException("Размещение не найдено: " + placementId));

        // Единая дверь: сверка версии + подъём поколения на коммите. Смена комнаты — такая же
        // мутация расписания, как перенос: соседняя вкладка обязана о ней узнать.
        ScheduleSession session = sessionGate.forWriteOf(placement, expectedVersion);

        // Здесь workspace строится ЗАНОВО, мимо провайдера, и это намеренно: смена комнаты —
        // мутация, ей нужен свежий авторитетный снимок, который она к тому же необратимо меняет.
        // Кэш такой путь не ускорил бы — он всё равно сбрасывает ключ поднятием версии сессии.
        // Правило общее для мутаторов: перенос и ручная установка строят снимок так же.
        var recreated = workspaceRecreationService.recreateWorkspaceFromSession(session.getId());
        ScheduleWorkspace workspace = recreated.workspace();
        Lesson targetLesson = recreated.lessonByPlacementId().get(placementId);
        if (targetLesson == null) {
            throw new IllegalStateException("Не удалось восстановить занятие для размещения " + placementId);
        }

        CellForLesson cell = new CellForLesson(placement.getScheduledDate(), placement.getScheduledSlot());
        Set<Auditorium> rooms = applyRooms(workspace, targetLesson, cell, auditoriumIds);

        placement.updatePlacement(placement.getScheduledDate(), placement.getScheduledSlot(), rooms, user);
        placementRepo.save(placement);
        eventPublisher.publishEvent(new PlacementChangedEvent(session.getId(), placementId, placement));

        log.info("🚪 Смена аудитории: placementId={}, комнат={}, {}",
                placementId, rooms.size(),
                rooms.stream().map(Auditorium::getName).toList());
        return session;
    }

    /**
     * Проверить и собрать комнаты. Единственный жёсткий отказ — комната занята кем-то ещё.
     *
     * <p>Тесноту здесь НЕ проверяем: диспетчер видел её в списке вариантов и решил. Своё
     * собственное занятие занятостью не считаем — иначе нельзя было бы оставить одну из двух
     * комнат на месте, поменяв вторую.</p>
     */
    private Set<Auditorium> applyRooms(ScheduleWorkspace workspace, Lesson targetLesson,
                                       CellForLesson cell, Set<Integer> auditoriumIds) {
        Set<Auditorium> rooms = new LinkedHashSet<>();
        for (Integer roomId : auditoriumIds) {
            AuditoriumResource resource = workspace.getResourceManager().getAuditoriumResource(roomId);
            if (resource == null) {
                throw new LessonMoveConflictException("аудитория не найдена: id=" + roomId);
            }
            if (resource.hasConstraint(cell)) {
                throw new LessonMoveConflictException("аудитория " + resource.getName() + " закрыта ограничением");
            }
            Lesson occupant = resource.getLessonInCell(cell);
            if (occupant != null && !occupant.equals(targetLesson)) {
                throw new LessonMoveConflictException(
                        "аудитория " + resource.getName() + " занята: " + describeOccupant(occupant));
            }
            rooms.add(resource.auditorium());
        }
        return rooms;
    }

    /** Статус комнаты для этого занятия в его ячейке. */
    private static AuditoriumOptionDto describe(AuditoriumResource room, CellForLesson cell,
                                                Lesson targetLesson, int headcount, boolean current) {
        int shortfall = room.shortfall(headcount);

        if (room.hasConstraint(cell)) {
            return new AuditoriumOptionDto(room.getId(), room.getName(), room.capacity(),
                    AuditoriumOptionDto.Status.CONSTRAINED, shortfall,
                    room.getConstraint(cell).map(ConstraintKindRef::code).orElse("ограничение"), current);
        }

        // Своё же занятие занятостью не считаем: комната, которая уже назначена этому занятию,
        // для него свободна.
        Lesson occupant = room.getLessonInCell(cell);
        if (occupant != null && !occupant.equals(targetLesson)) {
            return new AuditoriumOptionDto(room.getId(), room.getName(), room.capacity(),
                    AuditoriumOptionDto.Status.BUSY, shortfall, describeOccupant(occupant), current);
        }

        return new AuditoriumOptionDto(room.getId(), room.getName(), room.capacity(),
                AuditoriumOptionDto.Status.FREE, shortfall, null, current);
    }

    /** Кто занял комнату — человеческим языком, для подсказки в списке. */
    private static String describeOccupant(Lesson occupant) {
        StringBuilder sb = new StringBuilder();
        if (occupant.getDisciplineCourse() != null && occupant.getDisciplineCourse().getDiscipline() != null) {
            sb.append(occupant.getDisciplineCourse().getDiscipline().getAbbreviation());
        }
        if (occupant.getStudyStream() != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(occupant.getStudyStream().getName());
        }
        return sb.length() > 0 ? sb.toString() : "другое занятие";
    }
}
