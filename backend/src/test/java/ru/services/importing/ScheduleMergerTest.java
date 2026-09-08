package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;
import ru.services.importing.DisciplineFooterParser.FooterRow;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;
import ru.services.importing.ParsedSheet.SheetHeader;
import ru.services.importing.ScheduleMerger.MergedSchedule;
import ru.services.importing.ScheduleMerger.PeriodBounds;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты сведения разрезов.
 *
 * <p>Главное, что здесь зафиксировано: <b>занятие собирается из трёх файлов в одно</b>, и ни один
 * разрез не считается полным. Кластер — дата · пара · дисциплина (§9 спецификации: так
 * восстанавливаются потоки), но кластер это ещё не занятие: внутри него параллельные занятия
 * разводятся по общей группе, комнате и преподавателю (И-29).</p>
 */
class ScheduleMergerTest {

    private static final LocalDate DAY = LocalDate.of(2025, 9, 1);

    private static SheetCell cell(LocalDate date, String... lines) {
        return new SheetCell(DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0, date, List.of(lines), false);
    }

    private static ParsedSheet sheet(String file, CutKind kind, String owner,
                                     List<FooterRow> footer, SheetCell... cells) {
        return new ParsedSheet(file,
                new SheetHeader(kind, owner, "9Ф", "91 кафедра", 2025, "осенний"),
                List.of(cells), footer, List.of());
    }

    private static FooterRow footer(String code, String name, List<String> lecturers, List<String> practicians) {
        return new FooterRow(code, name, "91", lecturers, practicians, "18-30", "ЭКЗ", "");
    }

    private static MergedSchedule merge(List<ParsedSheet> sheets, PeriodBounds period) {
        return ScheduleMerger.merge(sheets, SuffixStyle.SLASH, period);
    }

    @Test
    @DisplayName("Три разреза об одном занятии сходятся в одно: вид, тема, преподаватель и комната")
    void threeCutsBecomeOneLesson() {
        // Ни один разрез не полон: у преподавателя нет вида занятия, у аудитории — темы,
        // у группы — преподавателя. Целое получается только сложением.
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", List.of(), cell(DAY, "Л/Т.4", "АСКС", "252-3")),
                sheet("252-3.html", CutKind.AUDITORIUM, "252-3", List.of(), cell(DAY, "Л", "911", "АСКС")),
                sheet("ВетровР.И.html", CutKind.EDUCATOR, "к-н Ветров Р.И. ктн", List.of(),
                        cell(DAY, "252-3", "911", "АСКС")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).singleElement().satisfies(lesson -> {
            assertThat(lesson.kind()).isEqualTo("Л");
            assertThat(lesson.theme()).isEqualTo("Т.4");
            assertThat(lesson.discipline()).isEqualTo("АСКС");
            assertThat(lesson.groups()).containsExactly("911");
            assertThat(lesson.rooms()).containsExactly("252-3");
            assertThat(lesson.educators()).containsExactly("к-н Ветров Р.И. ктн");
            assertThat(lesson.cuts()).containsExactlyInAnyOrder(CutKind.GROUP, CutKind.AUDITORIUM, CutKind.EDUCATOR);
        });
        assertThat(merged.report().entries()).isEqualTo(3);
        assertThat(merged.report().lessons()).isEqualTo(1);
        assertThat(merged.report().withoutEducator()).isZero();
    }

    @Test
    @DisplayName("Поток восстанавливается: одна лекция из файлов трёх групп — одно занятие")
    void streamIsRestoredFromGroupFiles() {
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", List.of(), cell(DAY, "Л/Т.1", "АСКС", "252-3")),
                sheet("912.html", CutKind.GROUP, "912", List.of(), cell(DAY, "Л/Т.1", "АСКС", "252-3")),
                sheet("913.html", CutKind.GROUP, "913", List.of(), cell(DAY, "Л/Т.1", "АСКС", "252-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).singleElement()
                .extracting(MergedLesson::groups, MergedLesson::rooms)
                .containsExactly(List.of("911", "912", "913"), List.of("252-3"));
    }

    @Test
    @DisplayName("Занятие, стёртое маркером у группы, приходит из аудиторного разреза (И-21)")
    void lessonMissedByGroupCutIsCounted() {
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", List.of(), cell(DAY, "Л/Т.1", "АСКС", "252-3")),
                sheet("252-3.html", CutKind.AUDITORIUM, "252-3", List.of(),
                        cell(DAY.plusDays(1), "ПЗ", "911", "АСКС")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(2);
        assertThat(merged.report().missedByGroupCut()).isEqualTo(1);
        assertThat(merged.lessons()).filteredOn(MergedLesson::missedByGroupCut).singleElement()
                .extracting(MergedLesson::date).isEqualTo(DAY.plusDays(1));
    }

    @Test
    @DisplayName("Преподаватель из подвала: «Л» — лектору, «ЛР» — практику (И-14)")
    void educatorComesFromFooterWhenThereIsOnlyOne() {
        List<FooterRow> footer = List.of(
                footer("АСКС", "Автоматизированные системы", List.of("Ветров Р.И."), List.of("Зорина А.Ю.")));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", footer,
                        cell(DAY, "Л/Т.1", "АСКС", "252-3"),
                        cell(DAY.plusDays(1), "ЛР/Т.2", "АСКС", "252-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).extracting(MergedLesson::kind, lesson -> lesson.educators().get(0))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Л", "Ветров Р.И."),
                        org.assertj.core.groups.Tuple.tuple("ЛР", "Зорина А.Ю."));
    }

    @Test
    @DisplayName("Двое практиков: подвал их не различает — никого не приписываем, это находка")
    void twoPracticiansAreNotGuessed() {
        List<FooterRow> footer = List.of(footer("АСКС", "Автоматизированные системы",
                List.of("Ветров Р.И."), List.of("Зорина А.Ю.", "Кораблёв В.В.")));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", footer, cell(DAY, "ЛР/Т.2", "АСКС", "252-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).singleElement().extracting(MergedLesson::educators).asList().isEmpty();
        assertThat(merged.report().withoutEducator()).isEqualTo(1);
        assertThat(merged.report().findings()).extracting(MergeReport.Finding::message)
                .anyMatch(message -> message.contains("подвал даёт 2 преподавателей"));
    }

    @Test
    @DisplayName("Разрез главнее подвала: ведёт тот, у кого занятие стоит поклеточно (И-22)")
    void educatorCutWinsOverFooter() {
        // В подвале по дисциплине числится Зорина, но занятие стоит в файле Кораблёва. Ведёт Кораблёв;
        // Зорина — кандидат (возможно, запасная, И-22), и приписывать её занятию нельзя: она
        // оказалась бы занята в свободные часы, а занятие — помечено полупотоком (двое ведущих).
        List<FooterRow> footer = List.of(
                footer("ФП", "Физическая подготовка", List.of("Лектор Л.Л."), List.of("Зорина А.Ю.")));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", footer, cell(DAY, "ЛР/Т.2", "ФП", "252-3")),
                sheet("КораблёвВ.В.html", CutKind.EDUCATOR, "Кораблёв В.В.", List.of(),
                        cell(DAY, "252-3", "911", "ФП")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).singleElement().satisfies(lesson -> {
            assertThat(lesson.educators()).containsExactly("Кораблёв В.В.");
            assertThat(lesson.coTaught()).isFalse();
        });
        assertThat(merged.report().withoutEducator()).isZero();
    }

    @Test
    @DisplayName("Подвал даёт троих, но разрез назвал ведущего — это не находка")
    void manyFooterCandidatesAreNotAFindingWhenTheCutAnswers() {
        // «По дисциплине ФП подвал даёт 3 преподавателей» — обычное состояние подвала, а не кривизна.
        // Находка только там, где преподавательского файла нет: вопрос в непривязанных занятиях,
        // а не в числе подписей (иначе одна дисциплина даёт тысячи повторов одной и той же строки).
        List<FooterRow> footer = List.of(footer("ФП", "Физическая подготовка", List.of(),
                List.of("Зорина А.Ю.", "Кораблёв В.В.", "Снегирёв В.Л.")));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", footer, cell(DAY, "ЛР/Т.2", "ФП", "252-3")),
                sheet("912.html", CutKind.GROUP, "912", footer, cell(DAY.plusDays(1), "ЛР/Т.3", "ФП", "252-3")),
                sheet("СнегирёвВ.Л.html", CutKind.EDUCATOR, "Снегирёв В.Л.", List.of(),
                        cell(DAY, "252-3", "911", "ФП")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).filteredOn(lesson -> lesson.date().equals(DAY))
                .singleElement().extracting(MergedLesson::educators).asList()
                .containsExactly("Снегирёв В.Л.");
        // Второе занятие разрезом не покрыто — вот о нём находка, ровно одна.
        assertThat(merged.report().withoutEducator()).isEqualTo(1);
        assertThat(merged.report().findings())
                .filteredOn(finding -> finding.message().contains("подвал даёт 3 преподавателей"))
                .singleElement().extracting(MergeReport.Finding::count).isEqualTo(1);
    }

    @Test
    @DisplayName("Полупотоки разводятся: разные группы, комнаты и преподаватели — два занятия (И-29)")
    void parallelHalfStreamsAreSplit() {
        List<ParsedSheet> sheets = List.of(
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", List.of(), cell(DAY, "252-3", "911", "АСКС")),
                sheet("Петров.html", CutKind.EDUCATOR, "Петров П.П.", List.of(), cell(DAY, "253-3", "912", "АСКС")));

        MergedSchedule merged = merge(sheets, null);

        // Записи не связаны ничем: ни группой, ни комнатой, ни преподавателем. Значит это два
        // занятия, а не поток «911+912» — состава, которого не существует и заводить который
        // было бы не за что (ровно на этом спотыкался расчёт плана).
        assertThat(merged.report().lessons()).isEqualTo(2);
        assertThat(merged.report().parallelSplit()).isEqualTo(2);
        assertThat(merged.report().coTaught()).isZero();
        assertThat(merged.lessons())
                .extracting(MergedLesson::groups, MergedLesson::rooms, MergedLesson::educators)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                List.of("911"), List.of("252-3"), List.of("Иванов И.И.")),
                        org.assertj.core.groups.Tuple.tuple(
                                List.of("912"), List.of("253-3"), List.of("Петров П.П.")));
    }

    @Test
    @DisplayName("Одна дисциплина у двух групп в одно время в разных комнатах — два занятия, не поток")
    void sameDisciplineAtSameTimeInDifferentRoomsIsTwoLessons() {
        // Живой случай, с которого правило и началось: 1 группа и 33 группа, одна дисциплина, одна
        // пара, разные комнаты и разные преподаватели. Поток «1+33» из этого не следует.
        List<FooterRow> footer = List.of(footer("АА", "Дисциплина АА",
                List.of("Иванов И.И."), List.of("Петров П.П.")));
        List<ParsedSheet> sheets = List.of(
                sheet("1.html", CutKind.GROUP, "1", footer, cell(DAY, "Л/Т.1", "АА", "252-3")),
                sheet("33.html", CutKind.GROUP, "33", footer, cell(DAY, "Л/Т.1", "АА", "253-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(2);
        assertThat(merged.lessons()).extracting(MergedLesson::groups)
                .containsExactlyInAnyOrder(List.of("1"), List.of("33"));
    }

    @Test
    @DisplayName("Занятие, поделённое по кабинетам, остаётся одним: комнаты связал преподаватель (§9)")
    void roomsSplitLessonStaysOneWhenEducatorLinksThem() {
        // §9 спецификации: у занятия законно несколько комнат. Но связь между ними должна быть
        // видна — здесь её показывает преподавательский файл, где обе группы стоят в одной ячейке.
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", List.of(), cell(DAY, "Л/Т.1", "АСКС", "252-3")),
                sheet("912.html", CutKind.GROUP, "912", List.of(), cell(DAY, "Л/Т.1", "АСКС", "253-3")),
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", List.of(),
                        cell(DAY, "252-3", "911", "АСКС"),
                        cell(DAY, "253-3", "912", "АСКС")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(1);
        assertThat(merged.report().parallelSplit()).isZero();
        assertThat(merged.lessons()).singleElement().satisfies(lesson -> {
            assertThat(lesson.groups()).containsExactly("911", "912");
            assertThat(lesson.rooms()).containsExactlyInAnyOrder("252-3", "253-3");
            assertThat(lesson.educators()).containsExactly("Иванов И.И.");
        });
    }

    @Test
    @DisplayName("Общий зал: ФП у разных потоков в «Сп. зале» — занятий столько, сколько ведущих")
    void sharedHallDoesNotGlueParallelLessons() {
        // Живой случай: физподготовка у всех групп идёт в спортзале, и по комнате все занятия
        // выглядят одним. Но преподавательский разрез разделяет потоки прямо — зал вмещает
        // несколько занятий разом, поэтому опорой он тут не работает.
        List<ParsedSheet> sheets = List.of(
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", List.of(),
                        cell(DAY, "Сп. зал", "911", "ФП")),
                sheet("Петров.html", CutKind.EDUCATOR, "Петров П.П.", List.of(),
                        cell(DAY, "Сп. зал", "912", "ФП")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(2);
        assertThat(merged.report().coTaught()).isZero();
        assertThat(merged.lessons()).extracting(MergedLesson::groups, MergedLesson::educators)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(List.of("911"), List.of("Иванов И.И.")),
                        org.assertj.core.groups.Tuple.tuple(List.of("912"), List.of("Петров П.П.")));
    }

    @Test
    @DisplayName("Групповой файл пристаёт к своему занятию в зале через группу, а не через зал")
    void groupFileJoinsItsOwnLessonInTheSharedHall() {
        List<ParsedSheet> sheets = List.of(
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", List.of(),
                        cell(DAY, "Сп. зал", "911", "ФП")),
                sheet("Петров.html", CutKind.EDUCATOR, "Петров П.П.", List.of(),
                        cell(DAY, "Сп. зал", "912", "ФП")),
                // Групповые файлы называют только зал — общий для обоих занятий. Связь даёт группа.
                sheet("911.html", CutKind.GROUP, "911", List.of(), cell(DAY, "ПЗ/Т.1", "ФП", "Сп. зал")),
                sheet("912.html", CutKind.GROUP, "912", List.of(), cell(DAY, "ПЗ/Т.2", "ФП", "Сп. зал")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(2);
        assertThat(merged.lessons())
                .extracting(MergedLesson::groups, MergedLesson::educators, MergedLesson::theme)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(List.of("911"), List.of("Иванов И.И."), "Т.1"),
                        org.assertj.core.groups.Tuple.tuple(List.of("912"), List.of("Петров П.П."), "Т.2"));
    }

    @Test
    @DisplayName("Поток в общей комнате не разводится, пока ведущий один")
    void oneEducatorKeepsTheRoomAnchorWorking() {
        List<ParsedSheet> sheets = List.of(
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", List.of(),
                        cell(DAY, "Сп. зал", "911", "ФП")),
                sheet("912.html", CutKind.GROUP, "912", List.of(), cell(DAY, "ПЗ/Т.1", "ФП", "Сп. зал")));

        MergedSchedule merged = merge(sheets, null);

        // Спора о комнате нет — значит она по-прежнему опора, и 912 присоединяется к занятию Иванова.
        assertThat(merged.report().lessons()).isEqualTo(1);
        assertThat(merged.lessons()).singleElement().extracting(MergedLesson::groups).asList()
                .containsExactlyInAnyOrder("911", "912");
    }

    @Test
    @DisplayName("Двое ведут одно занятие: общая комната и группа — это не полупотоки (вопрос 1a)")
    void twoEducatorsOnTheSameLessonAreCoTeaching() {
        List<ParsedSheet> sheets = List.of(
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", List.of(), cell(DAY, "252-3", "911", "ИЯ")),
                sheet("Петров.html", CutKind.EDUCATOR, "Петров П.П.", List.of(), cell(DAY, "252-3", "911", "ИЯ")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(1);
        assertThat(merged.report().parallelSplit()).isZero();
        assertThat(merged.report().coTaught()).isEqualTo(1);
        assertThat(merged.lessons()).singleElement().extracting(MergedLesson::educators).asList()
                .containsExactly("Иванов И.И.", "Петров П.П.");
    }

    @Test
    @DisplayName("Написание номера группы не разводит занятие: «101/1» и «101-1» — одна опора")
    void groupSpellingDoesNotSplitTheLesson() {
        List<ParsedSheet> sheets = List.of(
                sheet("101-1.html", CutKind.GROUP, "101-1", List.of(), cell(DAY, "Л/Т.1", "АСКС", "252-3")),
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", List.of(),
                        cell(DAY, "416-3", "101/1", "АСКС")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(1);
        assertThat(merged.lessons()).singleElement().satisfies(lesson -> {
            assertThat(lesson.groups()).containsExactly("101/1");
            assertThat(lesson.educators()).containsExactly("Иванов И.И.");
        });
    }

    @Test
    @DisplayName("Экзамен записан полным именем дисциплины — подвал всё равно находится")
    void examWrittenWithFullDisciplineNameStillFindsItsFooter() {
        // Живой случай: в ячейке экзамена стоит не обозначение «АСКС», а полное название. Пока
        // входом в подвал служило одно обозначение, такие занятия оставались без преподавателя.
        List<FooterRow> footer = List.of(
                footer("АСКС", "Автоматизированные системы", List.of("Ветров Р.И."), List.of("Зорина А.Ю.")));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", footer,
                        cell(DAY, "ЭКЗ", "Автоматизированные системы", "252-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().withoutEducator()).isZero();
        assertThat(merged.lessons()).singleElement().satisfies(lesson -> {
            // Дисциплина сведена к обозначению: иначе занятие уехало бы отдельной дисциплиной.
            assertThat(lesson.discipline()).isEqualTo("АСКС");
            // Экзамен принимает лектор, а не практик.
            assertThat(lesson.educators()).containsExactly("Ветров Р.И.");
        });
    }

    @Test
    @DisplayName("Полное имя и обозначение — одно занятие: экзамен не разъезжается на два")
    void fullNameAndAbbreviationMergeIntoOneLesson() {
        List<FooterRow> footer = List.of(
                footer("АСКС", "Автоматизированные системы", List.of("Ветров Р.И."), List.of()));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", footer,
                        cell(DAY, "ЭКЗ", "Автоматизированные системы", "252-3")),
                sheet("252-3.html", CutKind.AUDITORIUM, "252-3", List.of(),
                        cell(DAY, "ЭКЗ", "911", "АСКС")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.report().lessons()).isEqualTo(1);
        assertThat(merged.lessons()).singleElement()
                .extracting(MergedLesson::discipline, MergedLesson::kind)
                .containsExactly("АСКС", "ЭКЗ");
    }

    @Test
    @DisplayName("«П» — практическое занятие: подвал отдаёт его практику, а не лектору")
    void singleLetterPracticeGoesToPractician() {
        List<FooterRow> footer = List.of(
                footer("АСКС", "Автоматизированные системы", List.of("Ветров Р.И."), List.of("Зорина А.Ю.")));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", footer, cell(DAY, "П/Т.3", "АСКС", "252-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).singleElement()
                .extracting(MergedLesson::kind, lesson -> lesson.educators().get(0))
                .containsExactly("П", "Зорина А.Ю.");
    }

    @Test
    @DisplayName("Вне периода — считается отдельно и не смешивается с «не сведено» (И-8)")
    void lessonsOutsideThePeriodAreCounted() {
        PeriodBounds period = new PeriodBounds(2025, DAY, DAY.plusDays(30));
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", List.of(),
                        cell(DAY, "Л/Т.1", "АСКС", "252-3"),
                        cell(DAY.plusDays(60), "ЭКЗ", "АСКС", "252-3")));

        MergedSchedule merged = merge(sheets, period);

        assertThat(merged.report().lessons()).isEqualTo(2);
        assertThat(merged.report().outsidePeriod()).isEqualTo(1);
    }

    @Test
    @DisplayName("Не тот период: учебный год файла не совпал с годом периода — находка, а не сдвиг")
    void wrongPeriodIsReported() {
        PeriodBounds period = new PeriodBounds(2024, DAY.minusYears(1), DAY);
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", List.of(), cell(DAY, "Л/Т.1", "АСКС", "252-3")));

        assertThat(merge(sheets, period).report().findings())
                .extracting(MergeReport.Finding::message)
                .anyMatch(message -> message.contains("тот ли период"));
    }

    @Test
    @DisplayName("Ячейка без даты в сведение не попадает, но и не исчезает молча")
    void undatedCellIsReported() {
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911", List.of(), cell(null, "Л/Т.1", "АСКС", "252-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).isEmpty();
        assertThat(merged.report().undatedEntries()).isEqualTo(1);
        assertThat(merged.report().findings()).extracting(MergeReport.Finding::message)
                .anyMatch(message -> message.contains("дата ячейки не восстановлена"));
    }

    @Test
    @DisplayName("Разные написания дисциплины сводятся: словарь один и у сверки, и у склейки")
    void disciplineSpellingsAreCollapsed() {
        // В подвале обозначение «АСКС», в другой группе то же название сокращено иначе.
        List<ParsedSheet> sheets = List.of(
                sheet("911.html", CutKind.GROUP, "911",
                        List.of(footer("АСКС", "Автоматизированные системы", List.of("Ветров Р.И."), List.of())),
                        cell(DAY, "Л/Т.1", "АСКС", "252-3")),
                sheet("912.html", CutKind.GROUP, "912",
                        List.of(footer("АС", "Автоматизированные системы", List.of("Ветров Р.И."), List.of())),
                        cell(DAY, "Л/Т.1", "АС", "252-3")));

        MergedSchedule merged = merge(sheets, null);

        assertThat(merged.lessons()).singleElement()
                .extracting(MergedLesson::discipline, MergedLesson::groups)
                .containsExactly("АС", List.of("911", "912"));
    }
}
