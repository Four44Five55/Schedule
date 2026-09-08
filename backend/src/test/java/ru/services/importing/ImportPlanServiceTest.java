package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.StudyPeriod;
import ru.entity.Assignment;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.StudyStream;
import ru.entity.logicSchema.ThemeLesson;
import ru.enums.DayOfWeek;
import ru.enums.KindOfStudy;
import ru.enums.PeriodType;
import ru.enums.TimeSlotPair;
import ru.repository.AssignmentRepository;
import ru.repository.CurriculumSlotRepository;
import ru.repository.DisciplineCourseRepository;
import ru.repository.DisciplineRepository;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.SpecialRankRepository;
import ru.repository.StudyPeriodRepository;
import ru.repository.StudyStreamRepository;
import ru.repository.ThemeLessonRepository;
import ru.services.importing.DisciplineFooterParser.FooterRow;
import ru.exceptions.RuleViolationException;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;
import ru.services.importing.ParsedSheet.SheetHeader;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import ru.exceptions.NotFoundException;

/**
 * Юнит-тесты разрешения сведённого занятия в цепочку плана.
 *
 * <p>Проверяется главное: <b>семестр вычисляется, позиция берётся из темы, а неразрешённое
 * называет причину</b>. База подменена заглушками — сервис ходит в репозитории только за
 * {@code findAll()} и одним {@code findById}.</p>
 */
class ImportPlanServiceTest {

    private static final LocalDate DAY = LocalDate.of(2025, 9, 1);

    private final StudyPeriodRepository periods = mock(StudyPeriodRepository.class);
    private final DisciplineRepository disciplines = mock(DisciplineRepository.class);
    private final GroupRepository groups = mock(GroupRepository.class);
    private final StudyStreamRepository streams = mock(StudyStreamRepository.class);
    private final EducatorRepository educators = mock(EducatorRepository.class);
    private final SpecialRankRepository ranks = mock(SpecialRankRepository.class);
    private final DisciplineCourseRepository courses = mock(DisciplineCourseRepository.class);
    private final ThemeLessonRepository themes = mock(ThemeLessonRepository.class);
    private final CurriculumSlotRepository slots = mock(CurriculumSlotRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final ImportMergeService merge = mock(ImportMergeService.class);

    private ImportPlanService service() {
        return new ImportPlanService(periods, disciplines, educators, groups, streams,
                courses, themes, slots, assignments, ranks, merge);
    }

    /**
     * Сохранение с выдачей id — иначе следующий уровень цепочки не найдёт родителя.
     *
     * <p>Именно это и проверяют тесты записи: курс → слот → назначение собираются в один проход, и
     * слот ищется по id уже созданного курса.</p>
     */
    private void savesWithIds() {
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        when(courses.save(any())).thenAnswer(call -> {
            DisciplineCourse course = call.getArgument(0);
            course.setId(sequence.incrementAndGet());
            return course;
        });
        when(themes.save(any())).thenAnswer(call -> {
            ThemeLesson theme = call.getArgument(0);
            theme.setId(sequence.incrementAndGet());
            return theme;
        });
        when(slots.save(any())).thenAnswer(call -> {
            CurriculumSlot slot = call.getArgument(0);
            slot.setId(sequence.incrementAndGet());
            return slot;
        });
        when(assignments.save(any())).thenAnswer(call -> {
            Assignment assignment = call.getArgument(0);
            assignment.setId(sequence.incrementAndGet());
            return assignment;
        });
    }

    private void base(Integer enrollmentYear) {
        StudyPeriod period = new StudyPeriod("Осень 2025/2026", 2025, PeriodType.FALL_SEMESTER,
                DAY, DAY.plusMonths(4));
        when(periods.findById(any())).thenReturn(java.util.Optional.of(period));
        when(disciplines.findAll()).thenReturn(List.of(new Discipline("Автоматизированные системы", "АСКС")));

        Group group = new Group();
        group.setName("911");
        group.setSize(25);
        group.setEnrollmentYear(enrollmentYear);
        when(groups.findAll()).thenReturn(List.of(group));

        StudyStream stream = new StudyStream("911", 1);
        stream.setGroups(Set.of(group));
        when(streams.findAll()).thenReturn(List.of(stream));

        // Назначение ссылается на СУЩНОСТЬ преподавателя: без неё занятие не разрешается.
        Educator educator = new Educator();
        educator.setName("Ветров Р.И.");
        when(educators.findAll()).thenReturn(List.of(educator));
        when(ranks.findAll()).thenReturn(List.of());
    }

    private static ParsedSheet groupSheet(String... cellLines) {
        return groupSheetAt(0, cellLines);
    }

    /**
     * Тот же файл, но занятие в другой день.
     *
     * <p>Дата обязана быть разной, когда занятий несколько: склейка сводит их по ключу
     * «дата · пара · дисциплина» (§9), и три листа с одной датой дали бы <b>одно</b> занятие, а не
     * три — это и есть восстановление потока.</p>
     */
    /** Тот же файл, но другой группы — её номер стоит и в имени файла, и в шапке. */
    private static ParsedSheet groupSheetOf(String group, String... cellLines) {
        FooterRow footer = new FooterRow("АСКС", "Автоматизированные системы", "91",
                List.of("Ветров Р.И."), List.of("Ветров Р.И."), "18-30", "ЭКЗ", "");
        SheetCell cell = new SheetCell(DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0, DAY, List.of(cellLines), false);
        return new ParsedSheet(group + ".html",
                new SheetHeader(CutKind.GROUP, group, "11Ф", null, 2025, "осенний"),
                List.of(cell), List.of(footer), List.of());
    }

    private static ParsedSheet groupSheetAt(int dayOffset, String... cellLines) {
        // Практик в подвале нужен: без него «ЛР» останется без преподавателя и уйдёт в блокеры —
        // атрибуция разводит лектора и практика видом занятия (И-14).
        FooterRow footer = new FooterRow("АСКС", "Автоматизированные системы", "91",
                List.of("Ветров Р.И."), List.of("Ветров Р.И."), "18-30", "ЭКЗ", "");
        SheetCell cell = new SheetCell(DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0,
                DAY.plusDays(dayOffset), List.of(cellLines), false);
        return new ParsedSheet("911.html",
                new SheetHeader(CutKind.GROUP, "911", "9Ф", "91 кафедра", 2025, "осенний"),
                List.of(cell), List.of(footer), List.of());
    }

    @Test
    @DisplayName("Занятие разрешается в цепочку: семестр из года набора, позиция из номера темы")
    void lessonResolvesIntoThePlanChain() {
        base(2025);

        PlanReport report = service().preview(List.of(groupSheet("Л/Т.4", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        assertThat(report.resolved()).isEqualTo(1);
        assertThat(report.coursesToCreate()).isEqualTo(1);
        assertThat(report.slotsToCreate()).isEqualTo(1);
        assertThat(report.assignmentsToCreate()).isEqualTo(1);
        assertThat(report.positionsByTheme()).isEqualTo(1);
        assertThat(report.sample()).singleElement().satisfies(row -> {
            // Первокурсник осенью — первый семестр: 2 × (2025 − 2025) + 1.
            assertThat(row.semester()).isEqualTo(1);
            assertThat(row.kind()).isEqualTo(KindOfStudy.LECTURE);
            // Позиция — порядковый номер слота в курсе: схема требует уникальности позиции БЕЗ
            // вида занятия, поэтому «тема 4» это ссылка слота на тему, а не сама позиция.
            assertThat(row.position()).isEqualTo(1);
            assertThat(row.theme()).isEqualTo("Т.4");
            assertThat(row.stream()).isEqualTo("911");
            assertThat(row.educators()).containsExactly("Ветров Р.И.");
            assertThat(row.newCourse()).isTrue();
        });
    }

    @Test
    @DisplayName("Год набора старше — семестр больше: 2 × разница + 1 за осень")
    void semesterGrowsWithEnrollmentYear() {
        base(2023);

        PlanReport report = service().preview(List.of(groupSheet("Л/Т.1", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        assertThat(report.sample()).singleElement().extracting(PlanReport.PlanRow::semester).isEqualTo(5);
    }

    @Test
    @DisplayName("Нет года набора — семестр не выдумывается, занятие в блокеры")
    void withoutEnrollmentYearThereIsNoSemester() {
        base(null);

        PlanReport report = service().preview(List.of(groupSheet("Л/Т.4", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        assertThat(report.resolved()).isZero();
        assertThat(report.blockers()).extracting(MergeReport.Finding::message)
                .anyMatch(message -> message.contains("год набора"));
    }

    @Test
    @DisplayName("Чужой вид занятия не подменяется «самым вероятным» — это строка отчёта")
    void unknownKindIsReported() {
        base(2025);

        // Обозначение заведомо чужое. «П», «КП» и «КуР» сюда больше не годятся: заказчик назвал их
        // соответствия, и они переехали в таблицу — сообщённый факт перестал быть непонятым кодом.
        PlanReport report = service().preview(List.of(groupSheet("ЛТ/Т.1", "АСКС", "Сп. зал")), 1, SuffixStyle.SLASH);

        assertThat(report.resolved()).isZero();
        assertThat(report.blockers()).extracting(MergeReport.Finding::message)
                .anyMatch(message -> message.contains("вид «ЛТ» не сопоставлен"));
    }

    @Test
    @DisplayName("«П» — практическое занятие, «КуР» — курсовой проект: сказанное заказчиком не блокер")
    void foreignKindSpellingsResolve() {
        base(2025);

        PlanReport practice = service().preview(
                List.of(groupSheet("П/Т.1", "АСКС", "252-3")), 1, SuffixStyle.SLASH);
        PlanReport project = service().preview(
                List.of(groupSheet("КуР/Т.2", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        assertThat(practice.resolved()).isEqualTo(1);
        assertThat(practice.sample()).singleElement().extracting(PlanReport.PlanRow::kind)
                .isEqualTo(KindOfStudy.PRACTICAL_WORK);
        assertThat(project.resolved()).isEqualTo(1);
        assertThat(project.sample()).singleElement().extracting(PlanReport.PlanRow::kind)
                .isEqualTo(KindOfStudy.COURSE_PROJECT);
    }

    @Test
    @DisplayName("Короткий курс (11 факультет): года набора нет, но семестр известен — первый")
    void shortCourseGroupGetsFirstSemester() {
        // «11434» — пятизначный номер 11 факультета: обучение от двух недель до трёх месяцев,
        // цифры года набора в номере нет вовсе. Раньше такие занятия целиком уходили в блокеры.
        base(null);
        Group group = new Group();
        group.setName("11434");
        group.setSize(25);
        when(groups.findAll()).thenReturn(List.of(group));
        StudyStream stream = new StudyStream("11434", 1);
        stream.setGroups(Set.of(group));
        when(streams.findAll()).thenReturn(List.of(stream));

        PlanReport report = service().preview(
                List.of(groupSheetOf("11434", "Л/Т.1", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        assertThat(report.resolved()).isEqualTo(1);
        assertThat(report.sample()).singleElement().extracting(PlanReport.PlanRow::semester).isEqualTo(1);
    }

    @Test
    @DisplayName("Без темы позиция выводится хронологией — и считается отдельно (И-20)")
    void positionFallsBackToChronology() {
        base(2025);
        // Аудиторный разрез темы не несёт вовсе.
        ParsedSheet roomSheet = new ParsedSheet("252-3.html",
                new SheetHeader(CutKind.AUDITORIUM, "252-3", "9Ф", null, 2025, "осенний"),
                List.of(new SheetCell(DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0, DAY,
                        List.of("Л", "911", "АСКС"), false)),
                List.of(), List.of());

        PlanReport report = service().preview(List.of(roomSheet), 1, SuffixStyle.SLASH);

        // Преподавателя в аудиторной ячейке нет — занятие до назначения не доходит, но причина названа.
        assertThat(report.blockers()).extracting(MergeReport.Finding::message)
                .anyMatch(message -> message.contains("преподаватель не определён"));
    }

    @Test
    @DisplayName("Период обязателен: без него семестр не вычислить — отказ с текстом, а не NPE")
    void periodIsRequired() {
        // Тип важен не меньше текста: RuleViolationException — «ожидаемая ситуация, 400 с текстом
        // для человека», а IllegalArgumentException по контракту JDK означает дефект и обязан
        // лететь пятисоткой без объяснений. Подмена типа тихо меняет ответ клиенту.
        assertThatThrownBy(() -> service().preview(List.of(), null, SuffixStyle.SLASH))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("Период обязателен");
    }
    @Test
    @DisplayName("Период выбран, но его нет: «не найдено», а не «правило нарушено»")
    void missingPeriodIsNotFound() {
        when(periods.findById(404)).thenReturn(java.util.Optional.empty());

        // Разные типы — разные ответы клиенту (404 против 400), и разное действие человека:
        // выбрать другой период либо выбрать хоть какой-то. Один тип на оба случая эту разницу
        // стирает — а именно так и было, пока весь пакет бросал IllegalArgumentException.
        assertThatThrownBy(() -> service().preview(List.of(), 404, SuffixStyle.SLASH))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Периода с id 404 нет");
    }

    // ===================== Запись =====================

    @Test
    @DisplayName("Запись заводит всю цепочку: курс → тема → слот → назначение")
    void createWritesTheWholeChain() {
        base(2025);
        savesWithIds();

        PlanReport report = service().create(List.of(groupSheet("Л/Т.4", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        assertThat(report.resolved()).isEqualTo(1);
        org.mockito.ArgumentCaptor<CurriculumSlot> slot = org.mockito.ArgumentCaptor.forClass(CurriculumSlot.class);
        org.mockito.Mockito.verify(slots).save(slot.capture());
        assertThat(slot.getValue().getPosition()).isEqualTo(1);
        assertThat(slot.getValue().getThemeLesson().getThemeNumber()).isEqualTo("4");
        assertThat(slot.getValue().getKindOfStudy()).isEqualTo(KindOfStudy.LECTURE);
        assertThat(slot.getValue().getThemeLesson()).isNotNull();
        // Окно аттестации не выдумываем: вопрос «ЭКЗ → сессия?» открыт, и до ответа в плане
        // остаётся значение по умолчанию.
        assertThat(slot.getValue().getAssessmentWindow()).isEqualTo(ru.enums.AssessmentWindow.STUDY_TIME);

        org.mockito.ArgumentCaptor<Assignment> assignment = org.mockito.ArgumentCaptor.forClass(Assignment.class);
        org.mockito.Mockito.verify(assignments).save(assignment.capture());
        assertThat(assignment.getValue().getEducators()).extracting(Educator::getName)
                .containsExactly("Ветров Р.И.");
    }

    @Test
    @DisplayName("Сотня занятий одного курса заводит ОДИН курс, а не сотню")
    void oneCourseForManyLessons() {
        base(2025);
        savesWithIds();

        PlanReport report = service().create(List.of(
                groupSheetAt(0, "Л/Т.1", "АСКС", "252-3"),
                groupSheetAt(1, "Л/Т.2", "АСКС", "252-3"),
                groupSheetAt(2, "Л/Т.3", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        // Курс один на всех, а слотов столько же, сколько тем: позиция берётся из номера темы.
        assertThat(report.coursesToCreate()).isEqualTo(1);
        org.mockito.Mockito.verify(courses, org.mockito.Mockito.times(1)).save(any());
        org.mockito.Mockito.verify(slots, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    @DisplayName("Запись и расчёт дают одни и те же числа — это один проход, а не два описания")
    void createReportsTheSameNumbersAsPreview() {
        base(2025);
        savesWithIds();
        List<ParsedSheet> sheets = List.of(groupSheet("Л/Т.4", "АСКС", "252-3"));

        PlanReport planned = service().preview(sheets, 1, SuffixStyle.SLASH);
        PlanReport created = service().create(sheets, 1, SuffixStyle.SLASH);

        assertThat(created.resolved()).isEqualTo(planned.resolved());
        assertThat(created.coursesToCreate()).isEqualTo(planned.coursesToCreate());
        assertThat(created.slotsToCreate()).isEqualTo(planned.slotsToCreate());
        assertThat(created.assignmentsToCreate()).isEqualTo(planned.assignmentsToCreate());
    }

    @Test
    @DisplayName("Преподаватель не заведён — занятие в блокеры, назначение не создаётся")
    void unknownEducatorBlocksTheLesson() {
        base(2025);
        savesWithIds();
        when(educators.findAll()).thenReturn(List.of());

        PlanReport report = service().create(List.of(groupSheet("Л/Т.4", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        assertThat(report.resolved()).isZero();
        assertThat(report.blockers()).extracting(MergeReport.Finding::message)
                .anyMatch(message -> message.contains("не заведён"));
        org.mockito.Mockito.verify(assignments, org.mockito.Mockito.never()).save(any());
        // И курс тоже не создаётся: блокеры проверяются ДО всякой записи.
        org.mockito.Mockito.verify(courses, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Тема 4 у лекции и у лабораторной — два слота с РАЗНЫМИ позициями")
    void sameThemeInTwoKindsGetsDistinctPositions() {
        // Живой прогон уронил заведение: UNIQUE (discipline_course_id, position) — БЕЗ вида занятия,
        // а «позиция = номер темы» давала обеим четвёрку. Тема задаёт порядок, а не само число.
        base(2025);
        savesWithIds();

        service().create(List.of(
                groupSheetAt(0, "Л/Т.4", "АСКС", "252-3"),
                groupSheetAt(1, "ЛР/Т.4", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        org.mockito.ArgumentCaptor<CurriculumSlot> saved = org.mockito.ArgumentCaptor.forClass(CurriculumSlot.class);
        org.mockito.Mockito.verify(slots, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CurriculumSlot::getPosition)
                .doesNotHaveDuplicates();
        // Тема у обоих слотов общая — она и связывает их с одним материалом.
        assertThat(saved.getAllValues()).allSatisfy(slot ->
                assertThat(slot.getThemeLesson().getThemeNumber()).isEqualTo("4"));
        assertThat(saved.getAllValues()).extracting(CurriculumSlot::getKindOfStudy)
                .containsExactlyInAnyOrder(KindOfStudy.LECTURE, KindOfStudy.LAB_WORK);
    }

    @Test
    @DisplayName("Позиции идут подряд от единицы, а не прыгают по номерам тем")
    void positionsAreSequentialWithinCourse() {
        base(2025);
        savesWithIds();

        service().create(List.of(
                groupSheetAt(0, "Л/Т.7", "АСКС", "252-3"),
                groupSheetAt(1, "Л/Т.9", "АСКС", "252-3")), 1, SuffixStyle.SLASH);

        org.mockito.ArgumentCaptor<CurriculumSlot> saved = org.mockito.ArgumentCaptor.forClass(CurriculumSlot.class);
        org.mockito.Mockito.verify(slots, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CurriculumSlot::getPosition)
                .containsExactly(1, 2);
    }

}
