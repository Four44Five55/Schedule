package ru.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.dto.assignment.ApplyAssignmentToCourseDto;
import ru.dto.assignment.AssignmentCreateDto;
import ru.entity.Assignment;
import ru.entity.Educator;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.mapper.AssignmentMapper;
import ru.repository.AssignmentRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.projection.ProjectionMaintenance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Один состав преподавателей — нескольким потокам сразу.
 *
 * <p><b>Что здесь проверяется и почему именно это.</b> Схема не менялась: у занятия по-прежнему
 * {@code UNIQUE (curriculum_slot_id, study_stream_id)}, то есть поток получает своё отдельное
 * назначение, и «несколько потоков» — это цикл, а не новая сущность. Поэтому опасны ровно два
 * места, и оба здесь закреплены:</p>
 *
 * <ol>
 *   <li><b>Уже назначенный поток не должен ронять пачку.</b> Раньше повтор доходил до вставки и
 *       падал нарушением UNIQUE — сырым 500 (обработчик 409 накрывает только пакет
 *       {@code ru.controllers.command}). Пока поток выбирали по одному, наткнуться было трудно;
 *       со списком достаточно одного занятого, чтобы откатилось всё остальное.</li>
 *   <li><b>Число запросов не должно расти вместе с числом потоков.</b> Соблазн написать цикл
 *       «поток → свой запрос существующих» даёт N+1 ровно там, где потоков много, — а список
 *       потоков заводится именно ради этого случая.</li>
 * </ol>
 */
class AssignmentMultiStreamTest {

    private static final int COURSE_ID = 700;

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final CurriculumSlotService slots = mock(CurriculumSlotService.class);
    private final StudyStreamService streams = mock(StudyStreamService.class);
    private final EducatorService educators = mock(EducatorService.class);
    private final AssignmentMapper mapper = mock(AssignmentMapper.class);
    private final LessonPlacementRepository placements = mock(LessonPlacementRepository.class);
    private final ProjectionMaintenance projection = mock(ProjectionMaintenance.class);

    private final AssignmentService service = new AssignmentService(
            assignments, slots, streams, educators, mapper, placements, projection);

    /** Всё сохранённое за вызов — предмет проверки (маппер замокан, DTO ничего не расскажут). */
    private final List<Assignment> saved = new ArrayList<>();

    AssignmentMultiStreamTest() {
        when(assignments.save(any(Assignment.class))).thenAnswer(inv -> {
            Assignment a = inv.getArgument(0);
            saved.add(a);
            return a;
        });
        when(mapper.toDtoList(anyList())).thenReturn(List.of());
        when(educators.getAllEntitiesByIds(anyList())).thenReturn(List.of(educator(1)));
    }

    // ── applyToCourse: потоки × занятия ──

    @Test
    @DisplayName("applyToCourse: два потока и два занятия → четыре назначения, состав общий")
    void appliesEachStreamToEachSlot() {
        givenCourse(slot(11), slot(12));
        givenStreams(stream(21), stream(22));
        when(assignments.findAllByCourseIdWithDetails(COURSE_ID)).thenReturn(List.of());

        service.applyToCourse(apply(List.of(21, 22), false, null));

        assertThat(saved).hasSize(4);
        assertThat(saved).allSatisfy(a -> assertThat(a.getEducators()).extracting(Educator::getId).containsExactly(1));
        assertThat(saved).extracting(a -> a.getStudyStream().getId() + ":" + a.getCurriculumSlot().getId())
                .containsExactlyInAnyOrder("21:11", "21:12", "22:11", "22:12");
    }

    @Test
    @DisplayName("applyToCourse: уже назначенный поток пропускается, второй заводится — пачка не рушится")
    void skipsAlreadyAssignedStreamWithoutBlockingTheRest() {
        CurriculumSlot slot = slot(11);
        givenCourse(slot);
        givenStreams(stream(21), stream(22));
        // Поток 21 на это занятие назначен заранее — вторую строку ему завести некуда (UNIQUE).
        when(assignments.findAllByCourseIdWithDetails(COURSE_ID))
                .thenReturn(List.of(existing(slot, stream(21), educator(9))));

        service.applyToCourse(apply(List.of(21, 22), false, null));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStudyStream().getId()).isEqualTo(22);
        // Чужой состав не тронут: overwrite=false означает «не трогать ручные исключения».
        verify(projection).announce(any(), eq(List.of()));
    }

    @Test
    @DisplayName("applyToCourse: overwrite=true меняет состав у уже назначенного потока, не заводя второго")
    void overwritesExistingStreamInsteadOfDuplicating() {
        CurriculumSlot slot = slot(11);
        Assignment existing = existing(slot, stream(21), educator(9));
        givenCourse(slot);
        givenStreams(stream(21), stream(22));
        when(assignments.findAllByCourseIdWithDetails(COURSE_ID)).thenReturn(List.of(existing));

        service.applyToCourse(apply(List.of(21, 22), true, null));

        assertThat(saved).hasSize(2);
        assertThat(existing.getEducators()).extracting(Educator::getId).containsExactly(1);
        assertThat(saved).filteredOn(a -> a.getStudyStream().getId() == 22).hasSize(1);
    }

    @Test
    @DisplayName("applyToCourse: существующие назначения читаются одним запросом на любое число потоков")
    void readsExistingAssignmentsOnceRegardlessOfStreamCount() {
        givenCourse(slot(11));
        givenStreams(stream(21), stream(22), stream(23), stream(24));
        when(assignments.findAllByCourseIdWithDetails(COURSE_ID)).thenReturn(List.of());

        service.applyToCourse(apply(List.of(21, 22, 23, 24), false, null));

        verify(assignments, times(1)).findAllByCourseIdWithDetails(COURSE_ID);
    }

    @Test
    @DisplayName("applyToCourse: повтор потока в списке — это один поток, а не два назначения")
    void ignoresRepeatedStreamInRequest() {
        givenCourse(slot(11));
        givenStreams(stream(21));
        when(assignments.findAllByCourseIdWithDetails(COURSE_ID)).thenReturn(List.of());

        service.applyToCourse(apply(List.of(21, 21), false, null));

        assertThat(saved).hasSize(1);
    }

    // ── createAssignments: несколько потоков на ОДНО занятие ──

    @Test
    @DisplayName("создание: по назначению на каждый поток одного занятия, состав общий")
    void createsOneAssignmentPerStreamOfTheSlot() {
        CurriculumSlot slot = slot(11);
        when(slots.getEntityById(11)).thenReturn(slot);
        givenStreams(stream(21), stream(22));
        when(assignments.findByCurriculumSlotId(11)).thenReturn(List.of());

        service.createAssignments(create(11, 21, 22));

        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(a -> a.getStudyStream().getId()).containsExactly(21, 22);
    }

    @Test
    @DisplayName("создание: уже назначенный поток пропускается, остальные заводятся (а не падение на UNIQUE)")
    void skipsStreamThatAlreadyHasAssignmentOnThatSlot() {
        CurriculumSlot slot = slot(11);
        when(slots.getEntityById(11)).thenReturn(slot);
        givenStreams(stream(21), stream(22));
        when(assignments.findByCurriculumSlotId(11))
                .thenReturn(List.of(existing(slot, stream(21), educator(9))));

        service.createAssignments(create(11, 21, 22));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStudyStream().getId()).isEqualTo(22);
    }

    @Test
    @DisplayName("создание: один и тот же поток дважды в запросе — одно назначение")
    void skipsDuplicateStreamWithinOneRequest() {
        CurriculumSlot slot = slot(11);
        when(slots.getEntityById(11)).thenReturn(slot);
        givenStreams(stream(21));
        when(assignments.findByCurriculumSlotId(11)).thenReturn(List.of());

        service.createAssignments(create(11, 21, 21));

        assertThat(saved).hasSize(1);
    }

    // ── фикстуры ──

    private void givenCourse(CurriculumSlot... courseSlots) {
        when(slots.getEntitiesByCourseId(COURSE_ID)).thenReturn(List.of(courseSlots));
    }

    private void givenStreams(StudyStream... available) {
        for (StudyStream s : available) {
            when(streams.getEntityById(s.getId())).thenReturn(s);
        }
    }

    private static ApplyAssignmentToCourseDto apply(List<Integer> streamIds, boolean overwrite, List<Integer> slotIds) {
        return new ApplyAssignmentToCourseDto(COURSE_ID, streamIds, List.of(1), null, overwrite, slotIds);
    }

    private static AssignmentCreateDto create(int slotId, int... streamIds) {
        List<AssignmentCreateDto.AssignmentDetail> details = new ArrayList<>();
        for (int streamId : streamIds) {
            details.add(new AssignmentCreateDto.AssignmentDetail(streamId, List.of(1), null));
        }
        return new AssignmentCreateDto(slotId, details);
    }

    private static Assignment existing(CurriculumSlot slot, StudyStream stream, Educator... leading) {
        Assignment a = new Assignment();
        a.setId(900 + stream.getId());
        a.setCurriculumSlot(slot);
        a.setStudyStream(stream);
        a.setEducators(new HashSet<>(Set.of(leading)));
        return a;
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

    private static Educator educator(int id) {
        Educator e = new Educator();
        e.setId(id);
        return e;
    }
}
