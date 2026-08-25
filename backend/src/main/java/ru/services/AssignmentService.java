package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.assignment.ApplyAssignmentToCourseDto;
import ru.dto.assignment.AssignmentCreateDto;
import ru.dto.assignment.AssignmentDto;
import ru.dto.assignment.AssignmentUpdateDto;
import ru.dto.assignment.RemoveAssignmentsFromCourseDto;
import ru.dto.assignment.RemoveAssignmentsImpactDto;
import ru.entity.Assignment;
import ru.entity.Educator;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.mapper.AssignmentMapper;
import ru.repository.AssignmentRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для управления "Назначениями" (Assignments).
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CurriculumSlotService curriculumSlotService;
    private final StudyStreamService studyStreamService;
    private final EducatorService educatorService;
    private final AssignmentMapper assignmentMapper;
    private final LessonPlacementRepository placementRepository;
    private final ProjectionMaintenance projectionMaintenance;

    /**
     * Создать назначения для ОДНОГО слота — по одному на каждый переданный поток.
     *
     * <p><b>Потоки, у которых назначение на этом слоте уже есть, пропускаются.</b> В схеме стоит
     * {@code UNIQUE (curriculum_slot_id, study_stream_id)}: у занятия один поток = одно назначение.
     * Раньше повтор доходил до вставки и падал нарушением ограничения — сырым 500, потому что
     * {@link ru.controllers.command.CommandExceptionHandler} накрывает только пакет
     * {@code ru.controllers.command}, а этот контроллер лежит в {@code ru.controllers}. Пока
     * поток выбирали по одному, наткнуться на это было трудно; с выбором нескольких потоков разом
     * достаточно одного уже назначенного, чтобы <b>вся</b> пачка откатилась.</p>
     *
     * <p>Пропуск, а не ошибка, — потому что менять состав уже существующего назначения умеет
     * правка (и {@code applyToCourse} с {@code overwrite}); «создать» здесь означает ровно
     * создать. Сколько назначений создано, видно по длине ответа: вызывающий сравнивает её
     * с числом запрошенных потоков.</p>
     */
    @Transactional
    public List<AssignmentDto> createAssignments(AssignmentCreateDto createDto) {
        // 1. Находим родительский слот через его сервис
        CurriculumSlot slot = curriculumSlotService.getEntityById(createDto.curriculumSlotId());

        // Потоки, уже назначенные на этот слот. Множество пополняется по ходу цикла — так же
        // отсекается и дубль ВНУТРИ одного запроса (два одинаковых потока в списке).
        Set<Integer> taken = assignmentRepository.findByCurriculumSlotId(slot.getId()).stream()
                .map(a -> a.getStudyStream().getId())
                .collect(Collectors.toCollection(HashSet::new));

        List<Assignment> createdAssignments = new ArrayList<>();

        for (AssignmentCreateDto.AssignmentDetail detail : createDto.assignments()) {
            if (!taken.add(detail.studyStreamId())) continue;

            // 2. Находим связанные сущности через их сервисы
            StudyStream stream = studyStreamService.getEntityById(detail.studyStreamId());
            List<Educator> educators = educatorService.getAllEntitiesByIds(detail.educatorIds());
            List<Educator> reserve = resolveReserve(detail.educatorIds(), detail.reserveEducatorIds());

            // 3. Создаём и сохраняем (сборка вынесена для переиспользования в applyToCourse)
            createdAssignments.add(
                    assignmentRepository.save(buildAssignment(slot, stream, educators, reserve)));
        }

        return assignmentMapper.toDtoList(createdAssignments);
    }

    /**
     * Назначает потоки + преподавателей на занятия курса. Удобство для типового
     * случая «один преподаватель ведёт поток через весь курс»: проставить разом,
     * а исключения потом править точечно.
     *
     * <p><b>Потоков может быть несколько, состав преподавателей у них общий</b> — это второй
     * типовой случай: потоки разные, а комбинация ведущих одна и та же, и повторять её вручную
     * для каждого потока значило бы вводить одно и то же по нескольку раз. Результат —
     * декартово произведение «потоки × занятия охвата»: назначение материализуется по слотам,
     * отдельной «курс-уровневой» сущности нет. Один поток — частный случай, прежнее поведение.</p>
     *
     * <p>Политика для уже назначенных слотов считается <b>по каждому потоку отдельно</b>:
     * {@code overwrite=false} (по умолчанию) — пропускаем (ручные исключения не трогаем,
     * операция идемпотентна); {@code overwrite=true} — заменяем состав преподавателей
     * существующего назначения (та же строка, ссылки не рвутся). Иначе один поток, назначенный
     * заранее, блокировал бы всю пачку.</p>
     *
     * <p>Охват: {@code slotIds} {@code null}/пусто → все слоты курса (прежнее поведение);
     * иначе — только слоты курса из этого набора (выбор по видам/конкретным занятиям
     * разворачивается во фронте). Фильтр по {@code courseId} уже отсекает чужие слоты —
     * пересечение с {@code slotIds} лишь сужает.</p>
     *
     * <p>Запасные (И-22) едут тем же путём, что и ведущие: при {@code overwrite=true} состав
     * запасных заменяется целиком — иначе «перезаписать» означало бы разное для двух ролей
     * одного назначения.</p>
     *
     * <p><b>Принимает DTO целиком, а не разобранным на аргументы</b> — как {@code createAssignments}
     * и {@code updateAssignment} в этом же классе. Разобранная сигнатура несла подряд четыре
     * {@code List<Integer>} (потоки, ведущие, запасные, охват): перепутать их местами компилятор
     * не мешает, а результат был бы катастрофическим и молчаливым.</p>
     */
    @Transactional
    public List<AssignmentDto> applyToCourse(ApplyAssignmentToCourseDto dto) {
        // Порядок сохраняем, повторы гасим: один и тот же поток дважды в списке — это один поток,
        // а не попытка назначить его дважды (второй заход и так упёрся бы в UNIQUE).
        List<StudyStream> streams = new LinkedHashSet<>(dto.studyStreamIds()).stream()
                .map(studyStreamService::getEntityById)
                .toList();
        List<Educator> educators = educatorService.getAllEntitiesByIds(dto.educatorIds());
        List<Educator> reserve = resolveReserve(dto.educatorIds(), dto.reserveEducatorIds());
        List<CurriculumSlot> slots = curriculumSlotService.getEntitiesByCourseId(dto.courseId());

        if (dto.slotIds() != null && !dto.slotIds().isEmpty()) {
            Set<Integer> wanted = new HashSet<>(dto.slotIds());
            slots = slots.stream().filter(s -> wanted.contains(s.getId())).toList();
        }

        // Уже назначенные слоты курса — по-прежнему ОДИН запрос на весь вызов, сколько бы потоков
        // ни пришло: запрос внутрь цикла по потокам дал бы N+1 ровно там, где потоков много.
        Set<Integer> wantedStreams = streams.stream().map(StudyStream::getId).collect(Collectors.toSet());
        Map<Integer, Map<Integer, Assignment>> existingByStreamAndSlot =
                assignmentRepository.findAllByCourseIdWithDetails(dto.courseId()).stream()
                        .filter(a -> wantedStreams.contains(a.getStudyStream().getId()))
                        .collect(Collectors.groupingBy(a -> a.getStudyStream().getId(),
                                Collectors.toMap(a -> a.getCurriculumSlot().getId(), a -> a)));

        List<Assignment> affected = new ArrayList<>();
        List<Assignment> overwritten = new ArrayList<>(); // только они меняют уже стоящие занятия
        for (StudyStream stream : streams) {
            Map<Integer, Assignment> existingBySlot =
                    existingByStreamAndSlot.getOrDefault(stream.getId(), Map.of());
            for (CurriculumSlot slot : slots) {
                Assignment existing = existingBySlot.get(slot.getId());
                if (existing == null) {
                    affected.add(assignmentRepository.save(buildAssignment(slot, stream, educators, reserve)));
                } else if (dto.overwrite()) {
                    existing.setEducators(new HashSet<>(educators));
                    existing.setReserveEducators(new HashSet<>(reserve));
                    affected.add(assignmentRepository.save(existing));
                    overwritten.add(existing);
                }
                // overwrite=false и назначение уже есть → SKIP
            }
        }
        announceChanged(overwritten);
        return assignmentMapper.toDtoList(affected);
    }

    /**
     * Массовое снятие «однотипных» назначений — зеркало {@link #applyToCourse}. Удаляет
     * назначения курса с тем же потоком И тем же составом преподавателей, что у варианта,
     * в пределах охвата {@code slotIds} (пусто → все слоты курса).
     *
     * <p>Каскад write-стороны (размещения) выполняет БД по FK. Read-модель
     * {@code schedule_view} чистим синхронно в этой же транзакции (см. {@link #purge}).</p>
     *
     * <p><b>Принимает DTO целиком, а не разобранным на аргументы</b> — как остальные методы этого
     * класса. Разобранная сигнатура несла два {@code Integer} подряд (курс, поток) и два
     * {@code List<Integer>} подряд (преподаватели, охват): перепутать соседей местами компилятор
     * не мешает, а промах критерия здесь означает снос <b>не тех</b> назначений вместе с их
     * размещениями — молча и необратимо.</p>
     *
     * @return число удалённых назначений
     */
    @Transactional
    public int removeFromCourse(RemoveAssignmentsFromCourseDto dto) {
        List<Assignment> matched = matchHomogeneous(dto);
        purge(matched);
        return matched.size();
    }

    /**
     * Предпросмотр последствий: сколько назначений подпадёт, сколько среди них размещено
     * и сколько из размещённых закреплено (замок) — см. {@link RemoveAssignmentsImpactDto}.
     */
    @Transactional(readOnly = true)
    public RemoveAssignmentsImpactDto removeImpact(RemoveAssignmentsFromCourseDto dto) {
        List<Assignment> matched = matchHomogeneous(dto);
        return impactOf(matched.stream().map(Assignment::getId).toList());
    }

    /**
     * Предпросмотр последствий удаления ОДНОГО назначения — для подтверждения точечного
     * удаления (иконка корзины): размещения уходят каскадом, включая закреплённые.
     */
    @Transactional(readOnly = true)
    public RemoveAssignmentsImpactDto deleteImpact(Integer assignmentId) {
        getEntityById(assignmentId); // 404, если назначения нет — не считаем последствия пустоты
        return impactOf(List.of(assignmentId));
    }

    /** Счётчики последствий для набора назначений (общий примитив массового и точечного удаления). */
    private RemoveAssignmentsImpactDto impactOf(List<Integer> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return new RemoveAssignmentsImpactDto(0, 0L, 0L);
        }
        long placed = placementRepository.findIdsByAssignmentIdIn(assignmentIds).size();
        long locked = placementRepository.countLockedByAssignmentIdIn(assignmentIds);
        return new RemoveAssignmentsImpactDto(assignmentIds.size(), placed, locked);
    }

    /**
     * Назначения курса, «однотипные» выбранному варианту: тот же поток, тот же состав
     * <b>ведущих</b> преподавателей (сравнение множеств), в пределах охвата слотов.
     *
     * <p>Запасные в критерий намеренно не входят: снятие делается по тому, кто ведёт занятие, а
     * страховка — характеристика назначения, а не признак его тождества. Иначе снятие
     * промахивалось бы мимо назначений, у которых запасного добавили или убрали позже.</p>
     */
    private List<Assignment> matchHomogeneous(RemoveAssignmentsFromCourseDto dto) {
        Set<Integer> wantedEducators = dto.educatorIds() == null ? Set.of() : new HashSet<>(dto.educatorIds());
        Set<Integer> scope = (dto.slotIds() == null || dto.slotIds().isEmpty()) ? null : new HashSet<>(dto.slotIds());

        return assignmentRepository.findAllByCourseIdWithDetails(dto.courseId()).stream()
                .filter(a -> a.getStudyStream().getId().equals(dto.studyStreamId()))
                .filter(a -> scope == null || scope.contains(a.getCurriculumSlot().getId()))
                .filter(a -> a.getEducators().stream().map(Educator::getId)
                        .collect(Collectors.toSet()).equals(wantedEducators))
                .toList();
    }

    /**
     * Удалить набор назначений. Общий примитив для массового и точечного удаления.
     *
     * <p>Размещения уходят каскадом БД ({@code assignment → lesson_placement}), а вслед за ними
     * — строки read-модели: {@code schedule_view} с миграции 017 имеет FK на
     * {@code lesson_placement} с {@code ON DELETE CASCADE}. Руками проекцию здесь НЕ чистим:
     * знание «у размещения есть проекция» принадлежит одному месту (схеме БД), а не каждому
     * сервису, который что-то удаляет. Именно размазанность этого знания и оставляла
     * строки-призраки на путях, где о ней забыли (удаление слота плана, дисциплины).</p>
     */
    private void purge(List<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return;
        }
        assignmentRepository.deleteAllById(assignments.stream().map(Assignment::getId).toList());
    }

    /**
     * Объявить, что состав преподавателей и/или поток назначений изменился.
     *
     * <p>Сервис назначений не знает и не должен знать, как это отражается на расписании:
     * он лишь сообщает об изменении своих данных, а перепроекцию делает владелец read-модели
     * ({@link ru.services.ScheduleSynchronizer}) — она несёт преподавателя снимком.</p>
     *
     * @param changed назначения, у которых изменился состав преподавателей и/или поток
     */
    private void announceChanged(List<Assignment> changed) {
        projectionMaintenance.announce(
                ProjectionSource.ASSIGNMENT, changed.stream().map(Assignment::getId).toList());
    }

    /** Сборка сущности назначения из уже разрешённых связей (DRY для create/applyToCourse). */
    private Assignment buildAssignment(CurriculumSlot slot, StudyStream stream,
                                       List<Educator> educators, List<Educator> reserve) {
        Assignment newAssignment = new Assignment();
        newAssignment.setCurriculumSlot(slot);
        newAssignment.setStudyStream(stream);
        newAssignment.setEducators(new HashSet<>(educators));
        newAssignment.setReserveEducators(new HashSet<>(reserve));
        return newAssignment;
    }

    /**
     * Разрешает запасных (И-22) и проверяет главное ограничение: <b>один человек не может быть на
     * одном назначении и ведущим, и запасным</b>.
     *
     * <p>Роли взаимоисключающие по смыслу: ведущий занятие проводит и занимает время, запасной
     * числится и времени не занимает. Человек в обоих списках сразу означал бы, что вопрос «занят
     * ли он в эту пару» имеет два ответа. Молча выбросить его из запасных нельзя — это скрыло бы
     * ошибку ввода, поэтому отказ.</p>
     *
     * @param educatorIds        ведущие, как их прислал вызывающий
     * @param reserveEducatorIds запасные; {@code null}/пусто → запасных нет
     * @return сущности запасных (пустой список, если их нет)
     * @throws IllegalArgumentException если списки пересекаются
     */
    private List<Educator> resolveReserve(List<Integer> educatorIds, List<Integer> reserveEducatorIds) {
        if (reserveEducatorIds == null || reserveEducatorIds.isEmpty()) {
            return List.of();
        }
        Set<Integer> leading = educatorIds == null ? Set.of() : new HashSet<>(educatorIds);
        List<Integer> both = reserveEducatorIds.stream().filter(leading::contains).toList();
        if (!both.isEmpty()) {
            throw new IllegalArgumentException(
                    "Преподаватель не может быть одновременно ведущим и запасным на одном назначении: id="
                            + both.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        return educatorService.getAllEntitiesByIds(reserveEducatorIds);
    }

    @Transactional
    public AssignmentDto updateAssignment(Integer assignmentId, AssignmentUpdateDto updateDto) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден."));

        // Находим новые связанные сущности через сервисы
        StudyStream stream = studyStreamService.getEntityById(updateDto.studyStreamId());
        List<Educator> educators = educatorService.getAllEntitiesByIds(updateDto.educatorIds());
        List<Educator> reserve = resolveReserve(updateDto.educatorIds(), updateDto.reserveEducatorIds());

        // Обновляем поля
        assignment.setStudyStream(stream);
        assignment.setEducators(new HashSet<>(educators));
        // Состав запасных приходит целиком: пусто → запасных не остаётся. Перепроекции это не
        // требует (в проекции запасного нет), но announceChanged ниже нужен из-за ведущих.
        assignment.setReserveEducators(new HashSet<>(reserve));

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        // Уже размещённые занятия несут снимок прежнего преподавателя/потока — иначе правка
        // «дойдёт» только до раздела «Назначения», а сетка и отчёты останутся со старым.
        announceChanged(List.of(updatedAssignment));
        return assignmentMapper.toDto(updatedAssignment);
    }

    @Transactional
    public void deleteAssignment(Integer assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден."));
        // Через общий примитив — чтобы точечное удаление тоже чистило read-модель.
        purge(List.of(assignment));
    }

    @Transactional(readOnly = true)
    public Optional<AssignmentDto> findById(Integer assignmentId) {
        return assignmentRepository.findById(assignmentId).map(assignmentMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findAllDtosByCourseId(Integer courseId) {
        List<Assignment> assignments = assignmentRepository.findAllByCourseIdWithDetails(courseId);
        return assignmentMapper.toDtoList(assignments);
    }
    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    @Transactional(readOnly = true)
    public List<Assignment> getAllEntitiesByCourseId(Integer courseId) {
        return assignmentRepository.findAllByCourseIdWithDetails(courseId);
    }

    /**
     * Назначения НАБОРА курсов, сгруппированные по курсу — одним запросом.
     *
     * <p>Замена вызову {@link #getAllEntitiesByCourseId} в цикле: потребители (доска раскладки,
     * палитра неразмещённых, счётчики «распределено N/M») всегда работают с набором выбранных
     * курсов, и цикл давал по запросу на курс. Группировка сделана здесь, а не у вызывающих,
     * чтобы ключ («курс назначения» = {@code curriculumSlot.disciplineCourse.id}) выводился в
     * одном месте.</p>
     *
     * @param courseIds курсы; пусто/{@code null} → пустая карта
     * @return курс → его назначения (курсы без назначений в карте отсутствуют)
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<Assignment>> getAllEntitiesByCourseIds(Collection<Integer> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        return assignmentRepository.findAllByCourseIdsWithDetails(courseIds).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCurriculumSlot().getDisciplineCourse().getId()));
    }

    @Transactional(readOnly = true)
    public Assignment getEntityById(Integer id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + id + " не найден."));
    }

    @Transactional(readOnly = true)
    public List<Assignment> getAllEntities() {
        return assignmentRepository.findAll();
    }
}
