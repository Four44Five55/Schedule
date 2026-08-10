package ru.services.educator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.services.educator.EducatorTitles.Credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Подпись преподавателя — чистая функция, поэтому тестируется без Spring и БД (как
 * {@code OrgUnitSubtreeTest} и {@code LessonOrderRuleTest}).
 *
 * <p>Главное, что здесь проверяется, — поведение на <b>неполных</b> данных: все регалии
 * необязательны, «не указано» — законное состояние, и подпись не должна разваливаться на висящие
 * запятые и лишние пробелы ни в одной комбинации.</p>
 */
class EducatorTitlesTest {

    private static final String NAME = "Иванов И.И.";

    private static Credentials of(String rank, String service, String degree, String branch, String title) {
        String standalone = "к".equals(degree) ? "канд наук" : "д".equals(degree) ? "д-р наук" : null;
        return new Credentials(rank, service, degree, standalone, branch, title);
    }

    @Nested
    @DisplayName("Полная и пустая подпись")
    class FullAndEmpty {

        @Test
        @DisplayName("все регалии: звание и служба приставкой, степень и звание — хвостом")
        void fullLine() {
            Credentials credentials = of("п-к", "юст", "к", "т", "доц");
            assertEquals("п-к юст Иванов И.И., к.т.н., доц", EducatorTitles.line(credentials, NAME));
        }

        @Test
        @DisplayName("регалий нет — подпись равна ФИО, без запятых и пробелов")
        void noCredentials() {
            assertEquals(NAME, EducatorTitles.line(Credentials.EMPTY, NAME));
            assertEquals(NAME, EducatorTitles.line(null, NAME));
        }

        @Test
        @DisplayName("пустое ФИО — пустая подпись: регалии сами по себе никого не называют")
        void noName() {
            assertEquals("", EducatorTitles.line(of("п-к", null, "к", "т", "доц"), null));
            assertEquals("", EducatorTitles.line(of("п-к", null, null, null, null), "   "));
        }
    }

    @Nested
    @DisplayName("Частичные наборы")
    class Partial {

        @Test
        @DisplayName("только звание")
        void rankOnly() {
            assertEquals("п-к Иванов И.И.", EducatorTitles.line(of("п-к", null, null, null, null), NAME));
        }

        @Test
        @DisplayName("только учёное звание")
        void titleOnly() {
            assertEquals("Иванов И.И., доц", EducatorTitles.line(of(null, null, null, null, "доц"), NAME));
        }

        @Test
        @DisplayName("только степень — приставки нет, хвост один")
        void degreeOnly() {
            assertEquals("Иванов И.И., д.т.н.", EducatorTitles.line(of(null, null, "д", "т", null), NAME));
        }

        @Test
        @DisplayName("звание без службы — служба не выдумывается")
        void rankWithoutService() {
            assertEquals("п-к Иванов И.И., доц", EducatorTitles.line(of("п-к", null, null, null, "доц"), NAME));
        }
    }

    @Nested
    @DisplayName("Связность частей")
    class Coherence {

        @Test
        @DisplayName("служба без звания НЕ печатается: «юстиции Иванов» — обрывок, а не подпись")
        void serviceWithoutRankIsDropped() {
            assertEquals(NAME, EducatorTitles.line(of(null, "юст", null, null, null), NAME));
            assertEquals("", EducatorTitles.rankPrefix(of(null, "юст", null, null, null)));
        }

        @Test
        @DisplayName("отрасль без уровня — не степень: одни «технические» ничего не значат")
        void branchWithoutDegreeIsDropped() {
            assertEquals(NAME, EducatorTitles.line(of(null, null, null, "т", null), NAME));
        }

        @Test
        @DisplayName("уровень без отрасли даёт «канд наук»: записи «к.н.» не существует")
        void degreeWithoutBranch() {
            assertEquals("Иванов И.И., канд наук", EducatorTitles.line(of(null, null, "к", null, null), NAME));
            assertEquals("Иванов И.И., д-р наук", EducatorTitles.line(of(null, null, "д", null, null), NAME));
        }

        @Test
        @DisplayName("уровень без отрасли и без запасной формы — степень пропускается целиком")
        void degreeWithoutBranchAndStandalone() {
            Credentials credentials = new Credentials(null, null, "к", null, null, null);
            assertEquals(NAME, EducatorTitles.line(credentials, NAME));
        }
    }

    @Nested
    @DisplayName("Формат сокращений")
    class Format {

        @Test
        @DisplayName("точки расставляет форматтер: в данных сокращения хранятся без них")
        void dotsBelongToFormatNotData() {
            assertEquals("к.т.н.", EducatorTitles.degreeAbbreviation(of(null, null, "к", "т", null)));
        }

        @Test
        @DisplayName("составная отрасль сохраняет дефис: «ф-м» → «к.ф-м.н.»")
        void compoundBranch() {
            assertEquals("к.ф-м.н.", EducatorTitles.degreeAbbreviation(of(null, null, "к", "ф-м", null)));
        }

        @Test
        @DisplayName("пробелы по краям значений не протекают в подпись")
        void trimsValues() {
            Credentials credentials = new Credentials("  п-к ", " юст ", " к ", "канд наук", " т ", " доц ");
            assertEquals("п-к юст Иванов И.И., к.т.н., доц", EducatorTitles.line(credentials, "  Иванов И.И. "));
        }

        @Test
        @DisplayName("пустые строки равносильны отсутствию значения")
        void blanksAreAbsent() {
            Credentials credentials = new Credentials("", "", "", "", "", "");
            assertEquals(NAME, EducatorTitles.line(credentials, NAME));
        }
    }
}
