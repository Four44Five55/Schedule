package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.services.importing.GroupNumberDecoder.GroupNumber;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты расшифровки номера группы из чужой выгрузки.
 *
 * <p>Фиксируются решения, которые иначе пришлось бы выводить из кода: суффикс отрезается
 * <b>до</b> измерения длины базы, кафедра факультетов 1–9 называется склейкой, у 11 факультета года
 * набора нет вовсе, а номер не по стандарту — это <b>ответ с причиной</b>, а не исключение.</p>
 *
 * <p>Примеры взяты из спецификации формата (IMPORT_FORMAT §4), то есть из живой выгрузки, а не
 * придуманы под реализацию.</p>
 */
class GroupNumberDecoderTest {

    @Nested
    @DisplayName("Три формы номера")
    class Forms {

        @Test
        @DisplayName("951 — факультет 9, набор …5, кафедра называется «91» (склейка)")
        void threeDigitFormGluesDepartmentName() {
            GroupNumber number = GroupNumberDecoder.decode("951");

            assertThat(number.recognized()).isTrue();
            assertThat(number.facultyCode()).isEqualTo("9");
            assertThat(number.enrollmentDigit()).isEqualTo(5);
            assertThat(number.departmentShortName()).isEqualTo("91");
            assertThat(number.suffix()).isNull();
        }

        @Test
        @DisplayName("103 — факультет 1, кафедра «13»: склейка работает и с нулём в наборе")
        void threeDigitFormWithZeroEnrollmentDigit() {
            GroupNumber number = GroupNumberDecoder.decode("103");

            assertThat(number.recognized()).isTrue();
            assertThat(number.facultyCode()).isEqualTo("1");
            assertThat(number.enrollmentDigit()).isEqualTo(0);
            assertThat(number.departmentShortName()).isEqualTo("13");
        }

        @Test
        @DisplayName("10593 — факультет 10, кафедра «93» двузначная, без склейки")
        void fiveDigitFormKeepsDepartmentAsIs() {
            GroupNumber number = GroupNumberDecoder.decode("10593");

            assertThat(number.recognized()).isTrue();
            assertThat(number.facultyCode()).isEqualTo("10");
            assertThat(number.enrollmentDigit()).isEqualTo(5);
            assertThat(number.departmentShortName()).isEqualTo("93");
        }

        @Test
        @DisplayName("1152 — факультет 11, кафедра «52», года набора в номере НЕТ")
        void fourDigitFormHasNoEnrollmentDigit() {
            GroupNumber number = GroupNumberDecoder.decode("1152");

            assertThat(number.recognized()).isTrue();
            assertThat(number.facultyCode()).isEqualTo("11");
            assertThat(number.departmentShortName()).isEqualTo("52");
            assertThat(number.enrollmentDigit()).isNull();
        }
    }

    @Nested
    @DisplayName("Суффикс")
    class Suffix {

        @Test
        @DisplayName("103/12 — суффикс отрезается ДО измерения длины: это факультет 1, а не 10")
        void suffixIsCutBeforeMeasuringBase() {
            GroupNumber number = GroupNumberDecoder.decode("103/12");

            assertThat(number.recognized()).isTrue();
            assertThat(number.base()).isEqualTo("103");
            assertThat(number.suffix()).isEqualTo("12");
            assertThat(number.facultyCode()).isEqualTo("1");
            assertThat(number.departmentShortName()).isEqualTo("13");
        }

        @Test
        @DisplayName("10593/13 — суффикс не мешает пятизначной форме")
        void suffixDoesNotDisturbFiveDigitForm() {
            GroupNumber number = GroupNumberDecoder.decode("10593/13");

            assertThat(number.recognized()).isTrue();
            assertThat(number.facultyCode()).isEqualTo("10");
            assertThat(number.departmentShortName()).isEqualTo("93");
            assertThat(number.suffix()).isEqualTo("13");
        }

        @Test
        @DisplayName("855/2 и 855/11 — разные группы: суффикс остаётся частью имени")
        void suffixDistinguishesGroups() {
            GroupNumber single = GroupNumberDecoder.decode("855/2");
            GroupNumber split = GroupNumberDecoder.decode("855/11");

            assertThat(single.raw()).isNotEqualTo(split.raw());
            assertThat(single.suffix()).isEqualTo("2");
            assertThat(split.suffix()).isEqualTo("11");
            // База у них одна — расшифровка факультета, года и кафедры совпадает.
            assertThat(single.departmentShortName()).isEqualTo(split.departmentShortName());
            assertThat(single.enrollmentDigit()).isEqualTo(split.enrollmentDigit());
        }
    }

    @Nested
    @DisplayName("Номер не по стандарту — ответ с причиной, а не исключение")
    class NotStandard {

        @Test
        @DisplayName("Неизвестная длина базы")
        void unknownBaseLength() {
            GroupNumber number = GroupNumberDecoder.decode("95");

            assertThat(number.recognized()).isFalse();
            assertThat(number.problem()).contains("длина базы");
        }

        @Test
        @DisplayName("Пятизначный номер чужого факультета (12) формой десятого не считается")
        void fiveDigitsOfUnknownFacultyIsNotStandard() {
            GroupNumber number = GroupNumberDecoder.decode("12345");

            assertThat(number.recognized()).isFalse();
        }

        @Test
        @DisplayName("Буквы в номере")
        void lettersAreRejected() {
            GroupNumber number = GroupNumberDecoder.decode("95А");

            assertThat(number.recognized()).isFalse();
            assertThat(number.problem()).contains("цифр");
        }

        @Test
        @DisplayName("Код факультета с нуля не начинается")
        void leadingZeroIsRejected() {
            GroupNumber number = GroupNumberDecoder.decode("051");

            assertThat(number.recognized()).isFalse();
            assertThat(number.problem()).contains("нул");
        }

        @Test
        @DisplayName("«/» без суффикса")
        void danglingSlash() {
            GroupNumber number = GroupNumberDecoder.decode("955/");

            assertThat(number.recognized()).isFalse();
        }

        @Test
        @DisplayName("Больше одного «/»")
        void twoSlashes() {
            GroupNumber number = GroupNumberDecoder.decode("955/1/2");

            assertThat(number.recognized()).isFalse();
        }

        @Test
        @DisplayName("Тотальность: null, пустая строка и пробелы дают ответ, а не исключение")
        void decoderIsTotal() {
            assertThat(GroupNumberDecoder.decode(null).recognized()).isFalse();
            assertThat(GroupNumberDecoder.decode("").recognized()).isFalse();
            assertThat(GroupNumberDecoder.decode("   ").recognized()).isFalse();
        }

        @Test
        @DisplayName("Пробелы по краям номер не портят")
        void surroundingSpacesAreTrimmed() {
            GroupNumber number = GroupNumberDecoder.decode("  951  ");

            assertThat(number.recognized()).isTrue();
            assertThat(number.base()).isEqualTo("951");
        }
    }

    @Nested
    @DisplayName("Год набора выводится из цифры и периода")
    class EnrollmentYear {

        @Test
        @DisplayName("Для 2026/27 цифра 5 — это 2025, а не 2015")
        void digitResolvesWithinStudyWindow() {
            assertThat(GroupNumberDecoder.enrollmentYear(5, 2026)).isEqualTo(2025);
        }

        @Test
        @DisplayName("Цифра года самого периода — первокурсники")
        void currentYearIsInsideWindow() {
            assertThat(GroupNumberDecoder.enrollmentYear(6, 2026)).isEqualTo(2026);
        }

        @Test
        @DisplayName("Окно короче десяти лет: цифра вне окна года не даёт")
        void digitOutsideWindowIsNotGuessed() {
            // 2020 отстоит от 2026 на шесть лет — за пределами окна обучения.
            assertThat(GroupNumberDecoder.enrollmentYear(0, 2026)).isNull();
        }

        @Test
        @DisplayName("Нет цифры (11 факультет) — нет и года")
        void absentDigitGivesNoYear() {
            assertThat(GroupNumberDecoder.enrollmentYear(null, 2026)).isNull();
        }

        @Test
        @DisplayName("Через десятилетие: для 2021/22 цифра 8 — это 2018")
        void windowCrossesDecadeBoundary() {
            assertThat(GroupNumberDecoder.enrollmentYear(8, 2021)).isEqualTo(2018);
        }
    }
}
