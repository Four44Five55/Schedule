package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.StudyPeriod;
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
import ru.services.importing.ImportRollbackService.ImportedSession;
import ru.services.importing.ImportRollbackService.RollbackImpact;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты отката импорта.
 *
 * <p>Зафиксировано главное: <b>цена называется заранее</b>, снос идёт в единственно верном
 * порядке (курсы → потоки, иначе уборка молча ничего не находит), а импортная сессия опознаётся по
 * своим размещениям, а не по колонке-признаку.</p>
 */
class ImportRollbackServiceTest {

    private final DisciplineCourseRepository courses = mock(DisciplineCourseRepository.class);
    private final CurriculumSlotRepository slots = mock(CurriculumSlotRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final StudyStreamRepository streams = mock(StudyStreamRepository.class);
    private final LessonPlacementRepository placements = mock(LessonPlacementRepository.class);
    private final ScheduleSessionRepository sessions = mock(ScheduleSessionRepository.class);

    private final ImportRollbackService service = new ImportRollbackService(
            courses, slots, assignments, streams, placements, sessions);

    private void counts(long courseCount, long slotCount, long assignmentCount, long placementCount) {
        when(courses.countByStudyPeriodId(anyInt())).thenReturn(courseCount);
        when(slots.countByDisciplineCourse_StudyPeriod_Id(anyInt())).thenReturn(slotCount);
        when(assignments.countByCurriculumSlot_DisciplineCourse_StudyPeriod_Id(anyInt())).thenReturn(assignmentCount);
        when(placements.countByPeriod(anyInt())).thenReturn(placementCount);
    }

    @Test
    @DisplayName("Цена отката называется заранее: курсы, слоты, назначения и размещения")
    void impactNamesThePriceUpFront() {
        counts(3, 40, 12, 500);
        when(streams.findOrphans()).thenReturn(List.of());

        RollbackImpact impact = service.impact(1);

        assertThat(impact.courses()).isEqualTo(3);
        assertThat(impact.slots()).isEqualTo(40);
        assertThat(impact.assignments()).isEqualTo(12);
        // Именно это число и есть «сколько занятий расписания исчезнет».
        assertThat(impact.placements()).isEqualTo(500);
    }

    @Test
    @DisplayName("Порядок сноса: сначала курсы, потом — осиротевшие потоки")
    void coursesGoBeforeOrphanStreams() {
        counts(1, 1, 1, 1);
        DisciplineCourse course = new DisciplineCourse();
        when(courses.findByStudyPeriodId(anyInt())).thenReturn(List.of(course));
        StudyStream orphan = new StudyStream("911", 1);
        when(streams.findOrphans()).thenReturn(List.of(), List.of(orphan));

        RollbackImpact done = service.rollbackPlan(1);

        // Поток под RESTRICT: пока назначения не снесены, «поток без назначений» не найдётся ни один,
        // поэтому искать сирот можно только после flush удаления курсов.
        var order = inOrder(courses, streams);
        order.verify(courses).deleteAll(List.of(course));
        order.verify(courses).flush();
        order.verify(streams).findOrphans();
        order.verify(streams).deleteAll(List.of(orphan));
        assertThat(done.orphanStreams()).isEqualTo(1);
    }

    @Test
    @DisplayName("Импортная сессия опознаётся по своим размещениям, а не по колонке-признаку")
    void importedSessionIsRecognisedByItsPlacements() {
        ScheduleSession session = new ScheduleSession("Импорт: Осень", "import");
        session.setStudyPeriod(new StudyPeriod());
        UUID id = session.getId();
        List<Object[]> rows = java.util.Collections.singletonList(new Object[]{id, 1200L});
        when(placements.countBySourceAndPeriod(any(PlacementSource.class), anyInt())).thenReturn(rows);
        when(sessions.findAllById(any())).thenReturn(List.of(session));

        List<ImportedSession> found = service.importedSessions(1);

        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(id);
            assertThat(row.name()).isEqualTo("Импорт: Осень");
            assertThat(row.placements()).isEqualTo(1200L);
        });
    }
}
