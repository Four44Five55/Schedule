package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.KindOfStudy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты соответствия «обозначение выгрузки → наш вид занятия».
 *
 * <p>Зафиксировано главное: свои аббревиатуры узнаются сами (список живёт в {@link KindOfStudy}),
 * расхождения чужой программы перечислены поимённо и <b>только со слов заказчика</b>, а незнакомое
 * обозначение остаётся незнакомым — подстановка «самого вероятного» вида запрещена.</p>
 */
class LessonKindDictionaryTest {

    @Test
    @DisplayName("Наши аббревиатуры узнаются без всякой таблицы")
    void ourAbbreviationsResolveThemselves() {
        assertThat(LessonKindDictionary.of("Л")).isEqualTo(KindOfStudy.LECTURE);
        assertThat(LessonKindDictionary.of("ЛР")).isEqualTo(KindOfStudy.LAB_WORK);
        assertThat(LessonKindDictionary.of("ПЗ")).isEqualTo(KindOfStudy.PRACTICAL_WORK);
        assertThat(LessonKindDictionary.of("ЭКЗ")).isEqualTo(KindOfStudy.EXAM);
    }

    @Test
    @DisplayName("«П» — практическое занятие: у нас оно «ПЗ» (22368 занятий живой выгрузки)")
    void singleLetterPracticeIsPracticalWork() {
        assertThat(LessonKindDictionary.of("П")).isEqualTo(KindOfStudy.PRACTICAL_WORK);
    }

    @Test
    @DisplayName("«КП» и «КуР» — оба курсовой проект: первое совпало с нашим, второе в таблице")
    void bothCourseProjectSpellingsResolve() {
        assertThat(LessonKindDictionary.of("КП")).isEqualTo(KindOfStudy.COURSE_PROJECT);
        assertThat(LessonKindDictionary.of("КуР")).isEqualTo(KindOfStudy.COURSE_PROJECT);
    }

    @Test
    @DisplayName("Регистр и пробелы — оформление, а не различие")
    void spellingNoiseIsIgnored() {
        assertThat(LessonKindDictionary.of(" лр ")).isEqualTo(KindOfStudy.LAB_WORK);
        assertThat(LessonKindDictionary.of("кур")).isEqualTo(KindOfStudy.COURSE_PROJECT);
    }

    @Test
    @DisplayName("Незнакомое обозначение остаётся незнакомым — это строка отчёта, а не подстановка")
    void unknownCodeStaysUnknown() {
        assertThat(LessonKindDictionary.of("ЫЫЫ")).isNull();
        assertThat(LessonKindDictionary.of(null)).isNull();
        assertThat(LessonKindDictionary.of("  ")).isNull();
    }

    @Test
    @DisplayName("Колонка подвала: лекция и аттестация — к лектору, практики — к практикам")
    void footerColumnFollowsTheCategory() {
        assertThat(LessonKindDictionary.isLecture("Л")).isTrue();
        assertThat(LessonKindDictionary.isLecture("ЛР")).isFalse();
        assertThat(LessonKindDictionary.isLecture("П")).isFalse();

        // Экзамен принимает тот, кто читал курс: по колонке подвала он идёт к лектору.
        assertThat(LessonKindDictionary.isAssessment("ЭКЗ")).isTrue();
        assertThat(LessonKindDictionary.isAssessment("ЗО")).isTrue();
        assertThat(LessonKindDictionary.isAssessment("ЛР")).isFalse();

        // Неизвестное обозначение не объявляется ни лекцией, ни аттестацией.
        assertThat(LessonKindDictionary.isLecture("ЫЫЫ")).isFalse();
        assertThat(LessonKindDictionary.isAssessment("ЫЫЫ")).isFalse();
    }
}
