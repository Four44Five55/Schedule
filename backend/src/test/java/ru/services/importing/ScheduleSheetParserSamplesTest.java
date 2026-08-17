package ru.services.importing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Характеризующие тесты на <b>живых</b> файлах выгрузки (2025/2026, осенний семестр).
 *
 * <p><b>Сами себя пропускают, если образцов нет:</b> каталог {@code docs/} лежит в
 * {@code .gitignore}, значит на чистой машине этих файлов не будет, а сборка ломаться не должна.
 * Правило разбора стерегут синтетические тесты в {@link ScheduleSheetParserTest}; здесь —
 * страховка от расхождения правила с реальностью.</p>
 *
 * <p>Числа в утверждениях сняты с файлов, а не выведены из кода: если разбор изменится, тест
 * покажет, что именно поехало.</p>
 */
class ScheduleSheetParserSamplesTest {

    private static final Path SAMPLES = findSamples();

    private static Path findSamples() {
        for (Path candidate : List.of(Path.of("docs", "samples"), Path.of("..", "docs", "samples"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static ParsedSheet sample(String name) throws IOException {
        Assumptions.assumeTrue(SAMPLES != null, "нет каталога docs/samples — тест пропущен");
        Path file = SAMPLES.resolve(name);
        Assumptions.assumeTrue(Files.exists(file), "нет образца " + name + " — тест пропущен");
        return ScheduleSheetParser.parse(Files.readAllBytes(file));
    }

    /**
     * Любой образец нужного разреза — <b>найденный, а не названный по имени файла</b>.
     *
     * <p>Имя преподавательского файла это фамилия живого человека, и держать её в репозитории
     * незачем: разрез объявлен в шапке, значит и искать файл надо по шапке. Заодно тест перестал
     * зависеть от того, чьи именно образцы лежат в каталоге.</p>
     */
    private static ParsedSheet anySample(CutKind kind) throws IOException {
        Assumptions.assumeTrue(SAMPLES != null, "нет каталога docs/samples — тест пропущен");
        try (Stream<Path> files = Files.list(SAMPLES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                ParsedSheet sheet = ScheduleSheetParser.parse(Files.readAllBytes(file));
                if (sheet.header().kind() == kind) {
                    return sheet;
                }
            }
        }
        return Assumptions.abort("нет образца разреза " + kind + " — тест пропущен");
    }

    private static Optional<SheetCell> at(ParsedSheet sheet, LocalDate date, TimeSlotPair slot) {
        return sheet.cells().stream()
                .filter(c -> date.equals(c.date()) && c.slot() == slot)
                .findFirst();
    }

    @Test
    @DisplayName("Групповой файл 911: шапка, даты и содержимое ячеек")
    void groupSample() throws IOException {
        ParsedSheet sheet = sample("911.html");

        assertThat(sheet.header().kind()).isEqualTo(CutKind.GROUP);
        assertThat(sheet.header().owner()).isEqualTo("911");
        assertThat(sheet.header().faculty()).isEqualTo("9Ф");
        assertThat(sheet.header().startYear()).isEqualTo(2025);
        assertThat(sheet.header().semester()).isEqualTo("осенний");

        // 1 сентября 2025 — понедельник первой учебной недели.
        assertThat(at(sheet, LocalDate.of(2025, 9, 1), TimeSlotPair.FIRST)).get()
                .extracting(SheetCell::lines, org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("Л/Т.4", "УПМВ", "416-3");

        assertThat(sheet.cells()).allSatisfy(c -> assertThat(c.date()).isNotNull());
        assertThat(sheet.cells()).extracting(SheetCell::day)
                .doesNotContain(DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("Подвал файла 911: дисциплины, преподаватели и две находки живого файла")
    void groupSampleFooter() throws IOException {
        ParsedSheet sheet = sample("911.html");

        assertThat(sheet.footer()).isNotEmpty();
        assertThat(footerRow(sheet, "АСКС")).satisfies(discipline -> {
            assertThat(discipline.name()).isEqualTo("Автоматизированные системы управления КС");
            assertThat(discipline.department()).isEqualTo("91");
            // Подписи здесь — живые люди, поэтому проверяется ЧИСЛО, а не имена: тесту важно
            // ровно оно. Разбор самих подписей (звание, инициалы, регалии) закреплён на
            // синтетических данных в EducatorNameDecoderTest.
            assertThat(discipline.lecturers()).hasSize(2);
            // Находка 1: два практика у одной дисциплины в одной группе — блокирующий вопрос 1a
            // получил ответ «не ноль» на первом же живом файле.
            assertThat(discipline.practicians()).hasSize(2);
            assertThat(discipline.hours()).isEqualTo("18-30");
        });

        // У НИР лектора нет вовсе — пустой перечень это законное значение, а не сбой разбора.
        assertThat(footerRow(sheet, "НИР")).satisfies(discipline -> {
            assertThat(discipline.lecturers()).isEmpty();
            assertThat(discipline.practicians()).hasSize(1);
            assertThat(discipline.report()).isEqualTo("ЗО");
        });

        // Находка 2: у одной строки в колонке «Дисциплина» стоит индекс плана, а не название.
        assertThat(sheet.problems()).anyMatch(p -> p.contains("ДС.1.О"));
    }

    private static DisciplineFooterParser.FooterRow footerRow(ParsedSheet sheet, String code) {
        return sheet.footer().stream()
                .filter(row -> code.equals(row.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("в подвале нет дисциплины " + code));
    }

    @Test
    @DisplayName("Преподавательский файл: вырезанная первая неделя не сдвигает даты")
    void educatorSampleKeepsRealDates() throws IOException {
        ParsedSheet sheet = anySample(CutKind.EDUCATOR);

        // Владелец — подпись живого человека, поэтому проверяется её наличие, а не текст.
        assertThat(sheet.header().owner()).isNotBlank();
        assertThat(sheet.header().department()).isEqualTo("91 кафедра");

        // Первая колонка подписана единицей, но это 8 сентября: неделя 1 сентября вырезана.
        LocalDate earliest = sheet.cells().stream()
                .map(SheetCell::date)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElseThrow();
        assertThat(earliest).isAfterOrEqualTo(LocalDate.of(2025, 9, 8));
    }

    @Test
    @DisplayName("Аудиторный файл видит занятия, которые в групповом затёрты маркером «ЭкзС»")
    void auditoriumSampleRevealsLessonsHiddenByMarker() throws IOException {
        ParsedSheet group = sample("911.html");
        ParsedSheet room = sample("252-3.html");

        assertThat(room.header().kind()).isEqualTo(CutKind.AUDITORIUM);
        assertThat(room.header().owner()).isEqualTo("252-3");

        LocalDate examWeekMonday = LocalDate.of(2026, 1, 12);

        assertThat(at(group, examWeekMonday, TimeSlotPair.FIRST)).get()
                .matches(SheetCell::isMarker)
                .extracting(c -> c.lines().get(0)).isEqualTo("ЭкзС");

        assertThat(at(room, examWeekMonday, TimeSlotPair.FIRST)).get()
                .matches(c -> !c.isMarker())
                .extracting(SheetCell::lines, org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("Л", "911", "АСКС");
    }
}
