package ru.services.importing;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.services.importing.DisciplineFooterParser.FooterRow;
import ru.services.importing.ParsedSheet.CutKind;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Разбор подвала группового файла: перечень дисциплин с преподавателями.
 *
 * <p>Разметка фикстур снята с живого файла 911: восемь колонок плюс группа «Дом.задание» под общим
 * заголовком с {@code colspan}, а под шапкой — вторая строка заголовка «вид | выд. | сдача».
 * Именно из-за неё колонки ищутся по названию, а не по номеру, и именно она обязана не попасть в
 * строки дисциплин.</p>
 */
class DisciplineFooterParserTest {

    private static final String HEAD = """
            <tr>
              <td rowspan=2>Обозн</td>
              <td rowspan=2>Дисциплина</td>
              <td rowspan=2>Каф.</td>
              <td rowspan=2>Лектор, уч. степень, уч. звание</td>
              <td rowspan=2>Другие виды занятий</td>
              <td rowspan=2>Кол-во часов</td>
              <td rowspan=2>Отчет</td>
              <td rowspan=2>Поток лекционный</td>
              <td colspan=3>Дом.задание, Курс.проект. ЭКЗ</td>
            </tr>
            <tr><td>вид</td><td>выд.</td><td>сдача</td></tr>
            """;

    private final List<String> problems = new ArrayList<>();

    /** Подвал из строк, каждая — набор ячеек. */
    private List<FooterRow> parse(String... rows) {
        return parse(CutKind.GROUP, "<table>" + HEAD + String.join("", rows) + "</table>");
    }

    private List<FooterRow> parse(CutKind cut, String html) {
        return DisciplineFooterParser.parse(Jsoup.parse(html), cut, problems);
    }

    private static String row(String... cells) {
        StringBuilder html = new StringBuilder("<tr>");
        for (String cell : cells) {
            html.append("<td>").append(cell).append("</td>");
        }
        return html.append("</tr>").toString();
    }

    @Test
    @DisplayName("Строка подвала читается целиком, перечень преподавателей делится по «;»")
    void readsRow() {
        List<FooterRow> footer = parse(row(
                "АСКС", "Автоматизированные системы", "91",
                "Табакеркин В.Ф. двн проф; п/п-к Иванов С.В.",
                "Муркина А.Ю. кфмн ; к-н Сидоров Р.И. ктн",
                "18-30", "ЭКЗ", "911", "", "", ""));

        assertThat(footer).singleElement().satisfies(discipline -> {
            assertThat(discipline.code()).isEqualTo("АСКС");
            assertThat(discipline.name()).isEqualTo("Автоматизированные системы");
            assertThat(discipline.department()).isEqualTo("91");
            assertThat(discipline.lecturers()).containsExactly("Табакеркин В.Ф. двн проф", "п/п-к Иванов С.В.");
            assertThat(discipline.practicians()).containsExactly("Муркина А.Ю. кфмн", "к-н Сидоров Р.И. ктн");
            assertThat(discipline.hours()).isEqualTo("18-30");
            assertThat(discipline.report()).isEqualTo("ЭКЗ");
            assertThat(discipline.stream()).isEqualTo("911");
            assertThat(discipline.allEducators()).hasSize(4);
        });
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("Часы и звания не разбираются: значения отдаются как в файле")
    void keepsValuesAsPrinted() {
        List<FooterRow> footer = parse(row(
                "НИР", "Научно-исследовательская работа", "91",
                "", "Северова Е.В.", "0-24", "ЗО", "", "", "", ""));

        assertThat(footer).singleElement().satisfies(discipline -> {
            assertThat(discipline.lecturers()).isEmpty();
            assertThat(discipline.practicians()).containsExactly("Северова Е.В.");
            assertThat(discipline.hours()).isEqualTo("0-24");
        });
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("Подшапка «вид | выд. | сдача» не читается как дисциплина")
    void skipsSecondHeaderRow() {
        assertThat(parse()).isEmpty();
        assertThat(problems).anyMatch(p -> p.contains("ни одной строки дисциплины"));
    }

    @Test
    @DisplayName("Индекс плана вместо названия — замечание, а не тихая подстановка")
    void reportsPlanIndexInsteadOfName() {
        List<FooterRow> footer = parse(row(
                "Математическое обеспечение АССН", "ДС.1.О", "91",
                "п/п-к Астахов С.В.", "Зорина А.Ю. кфмн", "50-70", "", "", "", "", ""));

        // Поля остаются как в файле: толковать их — дело слоя сопоставления, а не разбора.
        assertThat(footer).singleElement().satisfies(discipline -> {
            assertThat(discipline.code()).isEqualTo("Математическое обеспечение АССН");
            assertThat(discipline.name()).isEqualTo("ДС.1.О");
        });
        assertThat(problems).anyMatch(p -> p.contains("ДС.1.О") && p.contains("индекс плана"));
    }

    @Test
    @DisplayName("Колонки ищутся по заголовку: лишняя колонка слева ничего не сдвигает")
    void findsColumnsByHeaderNotByPosition() {
        String head = """
                <tr><td>№</td><td>Обозн</td><td>Дисциплина</td><td>Каф.</td>
                    <td>Лектор</td><td>Другие виды занятий</td><td>Кол-во часов</td>
                    <td>Отчет</td><td>Поток лекционный</td></tr>
                """;
        List<FooterRow> footer = parse(CutKind.GROUP, "<table>" + head
                + row("1", "УПМВ", "Устройство и применение", "91", "Иванов И.И.", "", "10-20", "ЗЧ", "")
                + "</table>");

        assertThat(footer).singleElement().satisfies(discipline -> {
            assertThat(discipline.code()).isEqualTo("УПМВ");
            assertThat(discipline.name()).isEqualTo("Устройство и применение");
            assertThat(discipline.report()).isEqualTo("ЗЧ");
        });
    }

    @Test
    @DisplayName("Строка без обозначения пропускается со счётом, разбор не падает")
    void countsRowsWithoutCode() {
        List<FooterRow> footer = parse(
                row("", "", "", "", "", "", "", "", "", "", ""),
                row("НИР", "Научно-исследовательская работа", "91", "", "Северова Е.В.", "0-24", "ЗО", "", "", "", ""));

        assertThat(footer).hasSize(1);
        assertThat(problems).anyMatch(p -> p.contains("1 строк") && p.contains("без обозначения"));
    }

    @Test
    @DisplayName("Нет подвала: у группового разреза это замечание, у остальных — норма")
    void missingFooterIsReportedOnlyForGroupCut() {
        assertThat(parse(CutKind.GROUP, "<table><tr><td>Пн</td></tr></table>")).isEmpty();
        assertThat(problems).anyMatch(p -> p.contains("подвал"));

        problems.clear();
        assertThat(parse(CutKind.EDUCATOR, "<table><tr><td>Пн</td></tr></table>")).isEmpty();
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("Неполная шапка подвала: чего не нашлось — названо, разбор продолжается")
    void reportsMissingColumns() {
        String head = "<tr><td>Обозн</td><td>Дисциплина</td><td>Каф.</td></tr>";
        List<FooterRow> footer = parse(CutKind.GROUP,
                "<table>" + head + row("НИР", "Научно-исследовательская работа", "91") + "</table>");

        assertThat(footer).singleElement().satisfies(discipline -> {
            assertThat(discipline.code()).isEqualTo("НИР");
            assertThat(discipline.lecturers()).isEmpty();
            assertThat(discipline.report()).isEmpty();
        });
        assertThat(problems).anyMatch(p -> p.contains("нет колонок") && p.contains("лектор"));
    }
}
