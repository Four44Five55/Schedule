package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.DayOfWeek;
import ru.repository.AuditoriumRepository;
import ru.repository.DisciplineRepository;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.OrgUnitRepository;
import ru.repository.SpecialRankRepository;
import ru.repository.StudyStreamRepository;
import ru.enums.TimeSlotPair;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;
import ru.services.importing.ParsedSheet.SheetHeader;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Юнит-тесты той части сверки, которая не ходит в базу: восстановление потоков и провенанс.
 *
 * <p>Сам {@code ImportMatchingService} — спринговый сервис с шестью репозиториями, и поднимать
 * контекст ради двух чистых функций незачем: {@code compositions} и {@code Origins} статические
 * именно потому, что базы не касаются.</p>
 */
class ImportMatchingServiceTest {

    private static SheetCell cell(int day, String... lines) {
        return new SheetCell(DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0,
                LocalDate.of(2025, 9, day), List.of(lines), false);
    }

    private static ParsedSheet sheet(String file, CutKind kind, String owner, SheetCell... cells) {
        return new ParsedSheet(
                file,
                new SheetHeader(kind, owner, "9Ф", "91 кафедра", 2025, "осенний"),
                List.of(cells),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("Объединённые группы — один поток из двух, а не группа с запятой в имени")
    void jointGroupsBecomeOneStream() {
        ParsedSheet educatorFile = sheet("Иванов Т.В.html", CutKind.EDUCATOR, "п-к Иванов Т.В. дин",
                cell(1, "338-7", "10073/19, 10073/22", "ОВО"));

        assertThat(ImportMatchingService.compositions(List.of(educatorFile), SuffixStyle.SLASH))
                .containsExactly(List.of("10073/19", "10073/22"));
    }

    @Test
    @DisplayName("Один состав, увиденный в разных файлах и в разном порядке, — один поток")
    void sameCompositionCollapses() {
        ParsedSheet first = sheet("911.html", CutKind.AUDITORIUM, "252-3",
                cell(1, "Л", "911", "912", "АСКС"));
        ParsedSheet second = sheet("912.html", CutKind.AUDITORIUM, "252-3",
                cell(8, "Л", "912", "911", "АСКС"));

        assertThat(ImportMatchingService.compositions(List.of(first, second), SuffixStyle.SLASH))
                .containsExactly(List.of("911", "912"));
    }

    @Test
    @DisplayName("Одиночная группа — тоже поток: занятие иначе не к чему привязать")
    void singleGroupIsAStream() {
        ParsedSheet groupFile = sheet("911.html", CutKind.GROUP, "911",
                cell(1, "Л/Т.4", "УПМВ", "416-3"));

        assertThat(ImportMatchingService.compositions(List.of(groupFile), SuffixStyle.SLASH))
                .containsExactly(List.of("911"));
    }

    @Test
    @DisplayName("Провенанс: варианты значения дают объединение файлов без повторов")
    void originsUnionVariants() {
        ImportMatchingService.Origins origins = new ImportMatchingService.Origins();
        origins.add("Иванов Т.В. дин", "кафедра 91/ИвановТ.В.html");
        origins.add("Иванов Т.В. дин доц", "кафедра 91/911.html");
        origins.add("Иванов Т.В. дин", "кафедра 91/912.html");

        assertThat(origins.values()).containsExactly("Иванов Т.В. дин", "Иванов Т.В. дин доц");
        assertThat(origins.filesOf(List.of("Иванов Т.В. дин", "Иванов Т.В. дин доц")))
                .containsExactly("кафедра 91/ИвановТ.В.html", "кафедра 91/912.html", "кафедра 91/911.html");
    }

    @Test
    @DisplayName("Файл без имени в отчёте называет себя сам, а не пустотой")
    void unnamedSourceIsNamed() {
        assertThat(sheet(null, CutKind.GROUP, "911").sourceName()).isEqualTo(ParsedSheet.UNNAMED);
    }

    /**
     * Провенанс через весь отчёт — с пустой базой и заглушками репозиториев.
     *
     * <p>Спринг не поднимается: сверка ходит в репозитории ровно за {@code findAll()}, и подменить
     * их дешевле, чем поднимать контекст против живой БД (такие тесты в проекте отключены из
     * {@code build} именно поэтому).</p>
     */
    @Test
    @DisplayName("Файлы доезжают до КАЖДОГО раздела, включая «Группы»")
    void everySectionCarriesItsFiles() {
        // Пустая база: Mockito на методах, возвращающих список, по умолчанию отдаёт пустой —
        // значит всё, что приехало из файла, окажется «в базе нет», и строки будут в каждом разделе.
        ImportMatchingService service = new ImportMatchingService(
                mock(EducatorRepository.class), mock(GroupRepository.class),
                mock(DisciplineRepository.class), mock(AuditoriumRepository.class),
                mock(SpecialRankRepository.class), mock(OrgUnitRepository.class),
                mock(StudyStreamRepository.class));

        ParsedSheet groupFile = sheet("кафедра 91/911.html", CutKind.GROUP, "911",
                cell(1, "Л/Т.4", "УПМВ", "416-3"));

        ImportMatchReport report = service.match(List.of(groupFile), null, SuffixStyle.SLASH);

        // Ни одна строка любого раздела не остаётся без источника — включая «Группы», где номер
        // приезжает не из ячейки, а из шапки файла.
        assertThat(report.sections()).allSatisfy(section ->
                assertThat(section.rows()).as("раздел «%s»", section.title())
                        .allSatisfy(row -> assertThat(row.files())
                                .as("строка «%s» раздела «%s»", row.source(), section.title())
                                .containsExactly("кафедра 91/911.html")));

        assertThat(rowsOf(report, "Группы"))
                .singleElement()
                .extracting(ImportMatchReport.MatchRow::source, ImportMatchReport.MatchRow::fileCount)
                .containsExactly("911", 1);
    }

    @Test
    @DisplayName("Файл на объединённые группы: в «Группах» две строки, в «Потоках» — одна")
    void jointGroupFileIsSplitInGroupsAndJoinedInStreams() {
        // Шапка тоже бывает перечнем, а в раздел «Группы» номер приезжает именно из неё —
        // мимо ячейки. Без общего разбора перечня сюда попадала бы третья группа «10073/19,
        // 10073/22», которой не существует.
        ImportMatchingService service = new ImportMatchingService(
                mock(EducatorRepository.class), mock(GroupRepository.class),
                mock(DisciplineRepository.class), mock(AuditoriumRepository.class),
                mock(SpecialRankRepository.class), mock(OrgUnitRepository.class),
                mock(StudyStreamRepository.class));

        ParsedSheet jointFile = sheet("10073.html", CutKind.GROUP, "10073/19, 10073/22",
                cell(1, "Л/Т.4", "УПМВ", "416-3"));

        ImportMatchReport report = service.match(List.of(jointFile), null, SuffixStyle.SLASH);

        assertThat(rowsOf(report, "Группы")).extracting(ImportMatchReport.MatchRow::source)
                .containsExactlyInAnyOrder("10073/19", "10073/22");
        assertThat(rowsOf(report, "Потоки")).singleElement()
                .extracting(ImportMatchReport.MatchRow::source, ImportMatchReport.MatchRow::derived)
                .containsExactly("10073/19+10073/22", false);
    }

    @Test
    @DisplayName("Поток из одной группы помечен производным: он есть в отчёте, но не на первом плане")
    void singleGroupStreamIsDerived() {
        // Таких строк ровно столько же, сколько групп, и решения по ним те же — они дословно
        // повторяют раздел «Группы». Но из отчёта они не исчезают: заводится только названное
        // (И-10), а без своего потока занятие группы не к чему привязать.
        ImportMatchingService service = new ImportMatchingService(
                mock(EducatorRepository.class), mock(GroupRepository.class),
                mock(DisciplineRepository.class), mock(AuditoriumRepository.class),
                mock(SpecialRankRepository.class), mock(OrgUnitRepository.class),
                mock(StudyStreamRepository.class));

        ImportMatchReport report = service.match(List.of(sheet("911.html", CutKind.GROUP, "911",
                cell(1, "Л/Т.4", "УПМВ", "416-3"))), null, SuffixStyle.SLASH);

        assertThat(rowsOf(report, "Потоки")).singleElement()
                .extracting(ImportMatchReport.MatchRow::source,
                        ImportMatchReport.MatchRow::derived,
                        ImportMatchReport.MatchRow::status)
                .containsExactly("911", true, ImportMatchReport.MatchStatus.MISSING);
    }

    @Test
    @DisplayName("«101/1» и «101-1» — одна группа и один поток; имя в выбранном написании")
    void spellingsOfOneGroupAreCollapsed() {
        // Дефис приезжает оттуда, где номер попал в имя файла: «/» в именах файлов запрещён.
        // Две строки в отчёте означали бы две группы в справочнике — дубль, который разрезает
        // расписание надвое.
        ImportMatchingService service = new ImportMatchingService(
                mock(EducatorRepository.class), mock(GroupRepository.class),
                mock(DisciplineRepository.class), mock(AuditoriumRepository.class),
                mock(SpecialRankRepository.class), mock(OrgUnitRepository.class),
                mock(StudyStreamRepository.class));

        List<ParsedSheet> sheets = List.of(
                sheet("101-1.html", CutKind.GROUP, "101-1", cell(1, "Л/Т.4", "УПМВ", "416-3")),
                sheet("252-3.html", CutKind.AUDITORIUM, "252-3", cell(1, "Л", "101/1", "УПМВ")));

        ImportMatchReport slash = service.match(sheets, null, SuffixStyle.SLASH);
        assertThat(rowsOf(slash, "Группы")).singleElement()
                .extracting(ImportMatchReport.MatchRow::source, ImportMatchReport.MatchRow::fileCount)
                .containsExactly("101/1", 2);
        assertThat(rowsOf(slash, "Группы")).singleElement()
                .extracting(ImportMatchReport.MatchRow::detail).asString()
                .contains("встречено как: 101-1, 101/1");
        assertThat(rowsOf(slash, "Потоки")).singleElement()
                .extracting(ImportMatchReport.MatchRow::source).isEqualTo("101/1");

        // Тот же вход при другом выборе человека — то же опознание, другое написание имени.
        ImportMatchReport dash = service.match(sheets, null, SuffixStyle.DASH);
        assertThat(rowsOf(dash, "Группы")).singleElement()
                .extracting(ImportMatchReport.MatchRow::source).isEqualTo("101-1");
    }

    @Test
    @DisplayName("Регалии разной полноты — берём наибольшее и молчим; противоречие показываем")
    void fullestCredentialsWin() {
        ImportMatchingService service = new ImportMatchingService(
                mock(EducatorRepository.class), mock(GroupRepository.class),
                mock(DisciplineRepository.class), mock(AuditoriumRepository.class),
                mock(SpecialRankRepository.class), mock(OrgUnitRepository.class),
                mock(StudyStreamRepository.class));

        // «ктн» и «ктн доц» — один человек, просто в одной выгрузке хвост подписи короче.
        ImportMatchReport nested = service.match(List.of(
                sheet("a.html", CutKind.EDUCATOR, "Иванов И.И. ктн", cell(1, "252-3", "911", "АСКС")),
                sheet("b.html", CutKind.EDUCATOR, "Иванов И.И. ктн доц", cell(1, "252-3", "911", "АСКС"))
        ), null, SuffixStyle.SLASH);

        assertThat(rowsOf(nested, "Преподаватели")).singleElement()
                .extracting(ImportMatchReport.MatchRow::detail).isEqualTo("ктн доц");

        // «ктн» против «дтн» — не разная полнота, а противоречие: кандидат против доктора.
        ImportMatchReport conflicting = service.match(List.of(
                sheet("a.html", CutKind.EDUCATOR, "Петров П.П. ктн", cell(1, "252-3", "911", "АСКС")),
                sheet("b.html", CutKind.EDUCATOR, "Петров П.П. дтн", cell(1, "252-3", "911", "АСКС"))
        ), null, SuffixStyle.SLASH);

        assertThat(rowsOf(conflicting, "Преподаватели")).singleElement()
                .extracting(ImportMatchReport.MatchRow::detail).asString()
                .contains("в файлах по-разному");
    }

    private static List<ImportMatchReport.MatchRow> rowsOf(ImportMatchReport report, String title) {
        return report.sections().stream()
                .filter(section -> section.title().equals(title))
                .findFirst().orElseThrow()
                .rows();
    }

}
