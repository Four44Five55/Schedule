package ru.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Критерий «однотипных» назначений — что именно сносит массовое снятие.
 *
 * <p><b>Зачем эти тесты появились.</b> Критерий жил в приватном {@code matchHomogeneous} с
 * сигнатурой из четырёх позиционных аргументов: два {@code Integer} подряд (курс, поток) и два
 * {@code List<Integer>} подряд (преподаватели, охват). Соседей такого рода компилятор менять
 * местами не мешает, а цена промаха здесь несимметрична остальным ошибкам: снятое назначение
 * уносит каскадом БД свои размещения, включая закреплённые вручную. Сигнатуру заменили на приём
 * DTO — но код был непокрыт, и рефакторинг непокрытого кода держится на честном слове. Тесты
 * закрепляют сам критерий, а не способ его передать: они переживут и следующую перестановку.</p>
 *
 * <p>Проверяется ровно то, что решено и записано в javadoc метода: совпадать должны <b>поток</b> и
 * <b>множество ведущих</b>, охват сужает по слотам, а <b>запасные в критерий не входят</b> —
 * страховка это характеристика назначения, а не признак его тождества.</p>
 */
class AssignmentRemoveCriterionTest {

    private static final int COURSE_ID = 700;
    private static final int OUR_STREAM = 21;
    private static final int OTHER_STREAM = 22;

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final LessonPlacementRepository placements = mock(LessonPlacementRepository.class);

    private final AssignmentService service = new AssignmentService(
            assignments, mock(CurriculumSlotService.class), mock(StudyStreamService.class),
            mock(EducatorService.class), mock(AssignmentMapper.class), placements,
            mock(ProjectionMaintenance.class));

    @Test
    @DisplayName("совпадает поток и состав ведущих — назначение попадает под снятие")
    void matchesSameStreamAndSameLeadingSet() {
        Assignment ours = assignment(1, slot(11), OUR_STREAM, educators(4, 7));
        givenCourseAssignments(ours);

        assertThat(remove(List.of(4, 7), null)).isEqualTo(1);
        assertThat(deletedIds()).containsExactly(1);
    }

    @Test
    @DisplayName("другой поток при том же составе — не трогается")
    void ignoresOtherStream() {
        givenCourseAssignments(assignment(2, slot(11), OTHER_STREAM, educators(4, 7)));

        assertThat(remove(List.of(4, 7), null)).isZero();
        verify(assignments, never()).deleteAllById(anyList());
    }

    @Test
    @DisplayName("состав ведущих сравнивается как множество: лишний или недостающий — мимо")
    void requiresExactLeadingSet() {
        givenCourseAssignments(
                assignment(3, slot(11), OUR_STREAM, educators(4)),        // недостаёт 7
                assignment(4, slot(12), OUR_STREAM, educators(4, 7, 9))); // лишний 9

        assertThat(remove(List.of(4, 7), null)).isZero();
    }

    @Test
    @DisplayName("порядок в составе значения не имеет — это множество, а не список")
    void ignoresOrderOfEducators() {
        givenCourseAssignments(assignment(5, slot(11), OUR_STREAM, educators(4, 7)));

        assertThat(remove(List.of(7, 4), null)).isEqualTo(1);
    }

    @Test
    @DisplayName("охват сужает по занятиям; пустой охват — весь курс")
    void narrowsBySlotScope() {
        Assignment inScope = assignment(6, slot(11), OUR_STREAM, educators(4));
        Assignment outOfScope = assignment(7, slot(12), OUR_STREAM, educators(4));
        givenCourseAssignments(inScope, outOfScope);

        assertThat(remove(List.of(4), List.of(11))).isEqualTo(1);
        assertThat(deletedIds()).containsExactly(6);

        assertThat(remove(List.of(4), null)).isEqualTo(2);
    }

    @Test
    @DisplayName("запасные в критерий не входят: назначение со страховкой снимается наравне")
    void ignoresReserveEducators() {
        Assignment withReserve = assignment(8, slot(11), OUR_STREAM, educators(4));
        withReserve.setReserveEducators(new HashSet<>(educators(9)));
        givenCourseAssignments(withReserve);

        // Состав запасных не назван вовсе — и это не мешает совпадению.
        assertThat(remove(List.of(4), null)).isEqualTo(1);
    }

    @Test
    @DisplayName("пустой состав ведущих ищет назначения БЕЗ преподавателей, а не любые")
    void emptyEducatorsMatchesOnlyAssignmentsWithoutThem() {
        givenCourseAssignments(
                assignment(9, slot(11), OUR_STREAM, List.of()),
                assignment(10, slot(12), OUR_STREAM, educators(4)));

        assertThat(remove(List.of(), null)).isEqualTo(1);
        assertThat(deletedIds()).containsExactly(9);
    }

    @Test
    @DisplayName("предпросмотр считает по тому же критерию, что и снятие")
    void previewUsesTheSameCriterion() {
        givenCourseAssignments(
                assignment(11, slot(11), OUR_STREAM, educators(4)),
                assignment(12, slot(12), OTHER_STREAM, educators(4)));
        when(placements.findIdsByAssignmentIdIn(anyCollection())).thenReturn(List.of());
        when(placements.countLockedByAssignmentIdIn(anyCollection())).thenReturn(0L);

        RemoveAssignmentsImpactDto impact = service.removeImpact(dto(List.of(4), null));

        assertThat(impact.matchedAssignments()).isEqualTo(1);
    }

    // ── фикстуры ──

    private int remove(List<Integer> educatorIds, List<Integer> slotIds) {
        return service.removeFromCourse(dto(educatorIds, slotIds));
    }

    private static RemoveAssignmentsFromCourseDto dto(List<Integer> educatorIds, List<Integer> slotIds) {
        return new RemoveAssignmentsFromCourseDto(COURSE_ID, OUR_STREAM, educatorIds, slotIds);
    }

    private void givenCourseAssignments(Assignment... all) {
        when(assignments.findAllByCourseIdWithDetails(COURSE_ID)).thenReturn(List.of(all));
    }

    @SuppressWarnings("unchecked")
    private List<Integer> deletedIds() {
        var captor = forClass(Iterable.class);
        verify(assignments).deleteAllById(captor.capture());
        List<Integer> ids = new java.util.ArrayList<>();
        ((Iterable<Integer>) captor.getValue()).forEach(ids::add);
        return ids;
    }

    private static Assignment assignment(int id, CurriculumSlot slot, int streamId, List<Educator> leading) {
        Assignment a = new Assignment();
        a.setId(id);
        a.setCurriculumSlot(slot);
        a.setStudyStream(stream(streamId));
        a.setEducators(new HashSet<>(leading));
        return a;
    }

    private static List<Educator> educators(int... ids) {
        return java.util.Arrays.stream(ids).mapToObj(id -> {
            Educator e = new Educator();
            e.setId(id);
            return e;
        }).toList();
    }

    private static CurriculumSlot slot(int id) {
        CurriculumSlot s = new CurriculumSlot();
        s.setId(id);
        return s;
    }

    private static StudyStream stream(int id) {
        StudyStream s = new StudyStream();
        s.setId(id);
        return s;
    }
}
