package ru.services.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.board.BoardLessonDto;
import ru.dto.board.CoursePlacementCountDto;
import ru.dto.board.DisciplinePlacementDto;
import ru.dto.board.EducatorPlacementCountDto;
import ru.dto.board.EntityPlacementDto;
import ru.dto.board.PlacementBoardDto;
import ru.entity.Assignment;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.entity.logicSchema.ThemeLesson;
import ru.entity.write.LessonPlacement;
import ru.enums.KindOfStudy;
import ru.services.AssignmentService;
import ru.repository.write.LessonPlacementRepository;
import ru.utils.GroupNameComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Строит «доску раскладки» (Фича 2, Фаза B) — сводное дерево сущность→дисциплина→занятие со
 * счётчиками total/placed/unplaced для ручной сборки расписания.
 *
 * <p>SRP: только чтение/агрегация (в отличие от {@link ru.services.ManualPlacementService},
 * который владеет мутациями place/remove). Прецедент разделения — {@code EducatorScheduleReportService}.
 * Единица счёта — {@link Assignment} (в сессии ≤1 {@link LessonPlacement} на назначение), что
 * согласуется с {@code /query/readiness}.</p>
 *
 * <p>Ось (группа/преподаватель) — стратегия {@link BoardAxis}; полный набор назначений берётся из
 * {@link AssignmentService#getAllEntitiesByCourseId} (тот же источник, что палитра неразмещённых),
 * поэтому сущность видна даже когда все её занятия уже размещены/сгенерированы.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementBoardService {

    private final AssignmentService assignmentService;
    private final LessonPlacementRepository placementRepo;

    /**
     * @param sessionId сессия раскладки (источник размещённого состояния)
     * @param courseIds выбранные курсы; пусто/{@code null} → пустая доска
     * @param axis      ось группировки (группы/преподаватели)
     * @return дерево со счётчиками (сущности отсортированы по имени, дисциплины по аббревиатуре,
     *         занятия по позиции в плане)
     */
    @Transactional(readOnly = true)
    public PlacementBoardDto build(UUID sessionId, List<Integer> courseIds, BoardAxis axis) {
        if (courseIds == null || courseIds.isEmpty()) {
            return new PlacementBoardDto(0, 0, 0, List.of());
        }

        // Состояние: назначение → его размещение в этой сессии (если есть).
        Map<Integer, LessonPlacement> placementByAssignment = placementRepo.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(p -> p.getAssignment().getId(), p -> p, (a, b) -> a));

        // Назначения ВСЕХ выбранных курсов одним запросом (раньше — по запросу на курс).
        Map<Integer, List<Assignment>> assignmentsByCourse =
                assignmentService.getAllEntitiesByCourseIds(courseIds);

        // Аккумулятор дерева: id сущности → её узел; внутри — courseId → узел дисциплины.
        Map<Integer, EntityAgg> byEntity = new LinkedHashMap<>();

        int total = 0;
        int placed = 0;

        // Обходим в порядке запрошенных курсов — от него зависит порядок дисциплин в дереве.
        for (Integer courseId : courseIds) {
            for (Assignment a : assignmentsByCourse.getOrDefault(courseId, List.of())) {
                LessonPlacement placement = placementByAssignment.get(a.getId());

                // Заголовочные счётчики: назначение один раз, независимо от оси.
                total++;
                if (placement != null) {
                    placed++;
                }

                BoardLessonDto lesson = toLesson(a, courseId, placement);
                for (BoardAxis.EntityRef ref : axis.entitiesOf(a)) {
                    byEntity
                            .computeIfAbsent(ref.id(), k -> new EntityAgg(ref.id(), ref.name()))
                            .disciplines
                            .computeIfAbsent(courseId, k -> new DiscAgg(courseId, a.getCurriculumSlot()))
                            .lessons.add(lesson);
                }
            }
        }

        List<EntityPlacementDto> entities = byEntity.values().stream()
                .map(EntityAgg::toDto)
                .sorted(Comparator.comparing(EntityPlacementDto::name, GroupNameComparator.INSTANCE))
                .toList();

        int unplaced = total - placed;
        log.info("Доска раскладки: сессия={}, ось={}, курсов={}, total={}, placed={}, сущностей={}",
                sessionId, axis, courseIds.size(), total, placed, entities.size());
        return new PlacementBoardDto(total, placed, unplaced, entities);
    }

    /**
     * Лёгкие счётчики «распределено N/M» по каждому курсу — без построения дерева доски.
     * Считает по тем же данным, что {@link #build}, поэтому числа согласованы: total —
     * назначения курса, placed — те из них, что имеют размещение в сессии.
     *
     * @param sessionId сессия
     * @param courseIds курсы; пусто/{@code null} → пустой список
     * @return счётчики по каждому запрошенному курсу (в порядке запроса)
     */
    @Transactional(readOnly = true)
    public List<CoursePlacementCountDto> countsByCourse(UUID sessionId, List<Integer> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        java.util.Set<Integer> placedAssignmentIds = placementRepo.findBySessionId(sessionId).stream()
                .map(p -> p.getAssignment().getId())
                .collect(Collectors.toSet());

        Map<Integer, List<Assignment>> assignmentsByCourse =
                assignmentService.getAllEntitiesByCourseIds(courseIds);

        List<CoursePlacementCountDto> result = new ArrayList<>();
        for (Integer courseId : courseIds) {
            List<Assignment> assignments = assignmentsByCourse.getOrDefault(courseId, List.of());
            int total = assignments.size();
            int placed = (int) assignments.stream()
                    .filter(a -> placedAssignmentIds.contains(a.getId())).count();
            result.add(new CoursePlacementCountDto(
                    courseId, total, placed, educatorCounts(assignments, placedAssignmentIds)));
        }
        return result;
    }

    /**
     * Разбивка счётчиков курса по преподавателям (для раскрытия дисциплины во вкладке
     * «Генерация»). Совместное занятие двух преподавателей учитывается у каждого — так же,
     * как работает охват «по преподавателю» в генерации и очистке.
     */
    private List<EducatorPlacementCountDto> educatorCounts(
            List<Assignment> assignments, java.util.Set<Integer> placedAssignmentIds) {
        Map<Integer, EducatorAgg> byEducator = new LinkedHashMap<>();
        for (Assignment a : assignments) {
            if (a.getEducators() == null) continue;
            boolean isPlaced = placedAssignmentIds.contains(a.getId());
            for (Educator e : a.getEducators()) {
                byEducator.computeIfAbsent(e.getId(), k -> new EducatorAgg(e.getId(), e.getName()))
                        .add(isPlaced);
            }
        }
        return byEducator.values().stream()
                .map(EducatorAgg::toDto)
                .sorted(Comparator.comparing(
                        EducatorPlacementCountDto::educatorName,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** Накопитель счётчиков одного преподавателя (по образцу {@code EntityAgg} выше). */
    private static final class EducatorAgg {
        private final Integer id;
        private final String name;
        private int total;
        private int placed;

        EducatorAgg(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        void add(boolean isPlaced) {
            total++;
            if (isPlaced) placed++;
        }

        EducatorPlacementCountDto toDto() {
            return new EducatorPlacementCountDto(id, name, total, placed);
        }
    }

    private BoardLessonDto toLesson(Assignment a, Integer courseId, LessonPlacement placement) {
        CurriculumSlot slot = a.getCurriculumSlot();
        KindOfStudy kind = slot.getKindOfStudy();
        StudyStream stream = a.getStudyStream();
        ThemeLesson theme = slot.getThemeLesson();

        // Порядок групп фиксируем по коду (как в ManualPlacementService), чтобы id/имена
        // оставались согласованными по индексу.
        List<Group> groups = stream.getGroups().stream()
                .sorted(Comparator.comparing(Group::getName, GroupNameComparator.INSTANCE))
                .toList();

        return new BoardLessonDto(
                a.getId(),
                courseId,
                slot.getId(),
                kind.name(),
                kind.getAbbreviationName(),
                slot.getPosition(),
                theme != null ? theme.getThemeNumber() : null,
                theme != null ? theme.getTitle() : null,
                stream.getId(),
                stream.getName(),
                groups.stream().map(Group::getId).toList(),
                groups.stream().map(Group::getName).toList(),
                a.getEducators().stream().map(Educator::getId).toList(),
                a.getEducators().stream().map(Educator::getName).toList(),
                placement != null ? placement.getId().toString() : null,
                placement != null ? placement.getScheduledDate().toString() : null,
                placement != null ? placement.getScheduledSlot().name() : null,
                placement != null ? placement.isLocked() : null,
                placement != null ? placement.getSource().name() : null
        );
    }

    // ===== Аккумуляторы дерева (изменяемые при обходе, затем сворачиваются в неизменяемые DTO). =====

    private static final class EntityAgg {
        final Integer id;
        final String name;
        final Map<Integer, DiscAgg> disciplines = new LinkedHashMap<>();

        EntityAgg(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        EntityPlacementDto toDto() {
            List<DisciplinePlacementDto> discs = disciplines.values().stream()
                    .map(DiscAgg::toDto)
                    .sorted(Comparator.comparing(DisciplinePlacementDto::abbreviation,
                            Comparator.nullsLast(String::compareTo)))
                    .toList();
            int total = discs.stream().mapToInt(DisciplinePlacementDto::total).sum();
            int placed = discs.stream().mapToInt(DisciplinePlacementDto::placed).sum();
            return new EntityPlacementDto(id, name, total, placed, total - placed, discs);
        }
    }

    private static final class DiscAgg {
        final Integer courseId;
        final String abbreviation;
        final String name;
        final List<BoardLessonDto> lessons = new ArrayList<>();

        DiscAgg(Integer courseId, CurriculumSlot slot) {
            this.courseId = courseId;
            this.abbreviation = slot.getDisciplineCourse().getDiscipline().getAbbreviation();
            this.name = slot.getDisciplineCourse().getDiscipline().getName();
        }

        DisciplinePlacementDto toDto() {
            List<BoardLessonDto> sorted = lessons.stream()
                    .sorted(Comparator.comparing(BoardLessonDto::position,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            int total = sorted.size();
            int placed = (int) sorted.stream().filter(BoardLessonDto::isPlaced).count();
            return new DisciplinePlacementDto(courseId, abbreviation, name, total, placed, total - placed, sorted);
        }
    }
}
