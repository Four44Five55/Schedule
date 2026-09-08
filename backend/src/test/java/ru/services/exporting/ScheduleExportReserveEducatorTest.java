package ru.services.exporting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.entity.Assignment;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.StudyPeriod;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.StudyStream;
import ru.entity.read.ScheduleView;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import ru.repository.AssignmentRepository;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.OrgUnitRepository;
import ru.repository.read.ScheduleViewRepository;
import ru.services.ScheduleResponseService;
import ru.services.StudyPeriodService;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Запасной преподаватель (И-22) в подвале бланка.
 *
 * <p>Смысл роли — «числится за дисциплиной, но занятий не ведёт»: распределение его не видит,
 * времени он не занимает, в проекции {@code schedule_view} его нет и быть не может. Отсюда и
 * предмет этих тестов: подвал — <b>единственное</b> место, где он показывается, и собрать его туда
 * можно только с write-стороны, вторым источником рядом с размещёнными строками.</p>
 *
 * <p>Проверяется ровно то, что решено и потому легко потерять при следующей правке легенды:
 * колонку выбирает вид занятия назначения, порядок «ведущие → запасные» значащий (он и есть
 * единственный признак роли в бланке), а часы запасной не меняет.</p>
 */
class ScheduleExportReserveEducatorTest {

    private static final int PERIOD_ID = 1;
    private static final int GROUP_ID = 100;
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    private final StudyPeriodService periods = mock(StudyPeriodService.class);
    private final ScheduleViewRepository views = mock(ScheduleViewRepository.class);
    private final ScheduleResponseService responses = mock(ScheduleResponseService.class);
    private final ConstraintService constraints = mock(ConstraintService.class);
    private final EducatorRepository educators = mock(EducatorRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final GroupRepository groups = mock(GroupRepository.class);
    private final OrgUnitRepository orgUnits = mock(OrgUnitRepository.class);
    private final ScheduleWorkbookRenderer renderer = mock(ScheduleWorkbookRenderer.class);

    private final ScheduleExportService service = new ScheduleExportService(
            periods, views, responses, constraints, educators, assignments, groups, orgUnits, renderer);

    @Test
    @DisplayName("Запасной лектор дописывается в колонку «Лектор» ПОСЛЕ ведущего")
    void reserveLecturerGoesAfterLeadingOne() {
        Educator leading = educator(1, "Иванов И.И.");
        Educator reserve = educator(2, "Петров П.П.");

        givenSchedule(List.of(lecture(leading)));
        givenReserve(List.of(assignment(KindOfStudy.LECTURE, reserve)));
        givenEducators(leading, reserve);

        ScheduleWorkbookRenderer.LegendRow row = exportAndTakeSingleLegendRow();

        assertThat(row.lecturer())
                .as("оба в колонке лектора")
                .contains("Иванов И.И.")
                .contains("Петров П.П.");
        assertThat(row.lecturer().indexOf("Иванов"))
                .as("порядок значащий: сначала тот, у кого занятия стоят поклеточно")
                .isLessThan(row.lecturer().indexOf("Петров"));
        assertThat(row.others())
                .as("запасной лекционного назначения в «другие виды занятий» не попадает")
                .doesNotContain("Петров");
    }

    @Test
    @DisplayName("Колонку выбирает вид занятия назначения: нелекционное → «Другие виды занятий»")
    void reservePractitionerGoesToOthersColumn() {
        Educator leading = educator(1, "Иванов И.И.");
        Educator reserve = educator(2, "Петров П.П.");

        givenSchedule(List.of(lecture(leading)));
        givenReserve(List.of(assignment(KindOfStudy.PRACTICAL_WORK, reserve)));
        givenEducators(leading, reserve);

        ScheduleWorkbookRenderer.LegendRow row = exportAndTakeSingleLegendRow();

        assertThat(row.others()).contains("Петров П.П.");
        assertThat(row.lecturer())
                .as("в лекторы практик не попадает, даже когда лекция у дисциплины есть")
                .doesNotContain("Петров");
    }

    @Test
    @DisplayName("Часы считаются по размещённым парам — запасной их не меняет")
    void reserveDoesNotChangeHours() {
        Educator leading = educator(1, "Иванов И.И.");
        Educator reserve = educator(2, "Петров П.П.");

        givenSchedule(List.of(lecture(leading)));
        givenReserve(List.of(assignment(KindOfStudy.LECTURE, reserve)));
        givenEducators(leading, reserve);

        ScheduleWorkbookRenderer.LegendRow row = exportAndTakeSingleLegendRow();

        assertThat(row.hours())
                .as("одна размещённая лекция = 2 академ. часа; запасной пар не добавляет")
                .isEqualTo("2-0");
    }

    @Test
    @DisplayName("Дисциплина без единого размещения строки подвала не получает — вместе с запасными")
    void reserveAloneDoesNotCreateLegendRow() {
        Educator leading = educator(1, "Иванов И.И.");
        Educator reserve = educator(2, "Петров П.П.");

        // В расписании стоит «Программирование», а запасной числится за «Физикой».
        givenSchedule(List.of(lecture(leading)));
        Assignment physics = assignment(KindOfStudy.LECTURE, reserve);
        physics.getCurriculumSlot().getDisciplineCourse()
                .setDiscipline(new Discipline("Физика", "Физ"));
        givenReserve(List.of(physics));
        givenEducators(leading, reserve);

        List<ScheduleWorkbookRenderer.LegendRow> legend = exportAndTakeLegend();

        assertThat(legend).as("строка одна — по размещённой дисциплине").hasSize(1);
        assertThat(legend.get(0).discipline()).isEqualTo("Программирование");
        assertThat(legend.get(0).lecturer())
                .as("запасной чужой дисциплины в чужую строку не протекает")
                .doesNotContain("Петров");
    }

    // ── подготовка ──────────────────────────────────────────────────────────────

    private void givenSchedule(List<ScheduleView> rows) {
        StudyPeriod period = new StudyPeriod();
        period.setStartDate(START);
        period.setEndDate(END);
        when(periods.getEntityById(PERIOD_ID)).thenReturn(period);
        when(views.findByPeriod(START, END)).thenReturn(rows);
        when(responses.buildGridFromViews(any())).thenReturn(Map.of());
        when(constraints.loadAllConstraints())
                .thenReturn(new AllConstraints(Map.of(), Map.of(), Map.of()));
        when(renderer.render(any(), any(), any(), any(), any(), any())).thenReturn(new byte[0]);
    }

    private void givenReserve(List<Assignment> withReserve) {
        when(assignments.findWithReserveByPeriodId(anyInt())).thenReturn(withReserve);
    }

    private void givenEducators(Educator... all) {
        when(educators.findAllWithDetailsByIdIn(anyCollection())).thenReturn(List.of(all));
    }

    private static Educator educator(int id, String name) {
        Educator educator = new Educator(name);
        educator.setId(id);
        return educator;
    }

    /** Размещённая лекция по «Программированию» у группы {@link #GROUP_ID}. */
    private static ScheduleView lecture(Educator educator) {
        ScheduleView view = new ScheduleView(UUID.randomUUID(), START, TimeSlotPair.FIRST);
        view.setGroup(GROUP_ID, "911");
        view.setEducator(educator.getId(), educator.getName());
        view.setDiscipline("Программирование", "Прогр");
        view.setKindOfStudy(KindOfStudy.LECTURE.name());
        return view;
    }

    /** Назначение по «Программированию» на ту же группу — с одним запасным. */
    private static Assignment assignment(KindOfStudy kind, Educator reserve) {
        Group group = new Group();
        group.setId(GROUP_ID);
        group.setName("911");

        StudyStream stream = new StudyStream("911", 1);
        stream.setGroups(Set.of(group));

        DisciplineCourse course = new DisciplineCourse();
        course.setDiscipline(new Discipline("Программирование", "Прогр"));

        CurriculumSlot slot = new CurriculumSlot();
        slot.setDisciplineCourse(course);
        slot.setKindOfStudy(kind);

        Assignment assignment = new Assignment(slot, stream);
        assignment.addReserveEducator(reserve);
        return assignment;
    }

    // ── выполнение ──────────────────────────────────────────────────────────────

    private ScheduleWorkbookRenderer.LegendRow exportAndTakeSingleLegendRow() {
        List<ScheduleWorkbookRenderer.LegendRow> legend = exportAndTakeLegend();
        assertThat(legend).as("одна дисциплина — одна строка подвала").hasSize(1);
        return legend.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<ScheduleWorkbookRenderer.LegendRow> exportAndTakeLegend() {
        service.export(PERIOD_ID, ExportAxis.GROUP, GROUP_ID);

        ArgumentCaptor<List<ScheduleWorkbookRenderer.SheetData>> sheets =
                ArgumentCaptor.forClass(List.class);
        verify(renderer).render(sheets.capture(), any(), any(), any(), any(), any());
        assertThat(sheets.getValue()).as("лист группы").hasSize(1);
        return sheets.getValue().get(0).legend();
    }
}
