package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Что вообще может быть номером группы.
 *
 * <p>Повод — падение живого прогона 2026-08-15: {@code value too long for character varying(255)}
 * при вставке в {@code groups}. Разбор ячейки применяет правило «всё между первой и последней
 * строкой — группы» буквально (и правильно: судить о содержимом — не его дело), а в живых файлах
 * между строками попадается текст. Без этой проверки он доезжал до {@code INSERT} и падал в самом
 * дальнем от причины месте.</p>
 */
class GroupNumberShapeTest {

    @Test
    @DisplayName("Все живые формы номера проходят")
    void realNumbersPass() {
        assertThat(ImportMatchingService.looksLikeGroupNumber("911")).isTrue();
        assertThat(ImportMatchingService.looksLikeGroupNumber("1155-1")).isTrue();
        assertThat(ImportMatchingService.looksLikeGroupNumber("855/11")).isTrue();
        assertThat(ImportMatchingService.looksLikeGroupNumber("10593")).isTrue();
    }

    @Test
    @DisplayName("Проза не проходит — именно она и роняла вставку")
    void proseIsRejected() {
        assertThat(ImportMatchingService.looksLikeGroupNumber(
                "Программное и информационное обеспечение функционирования автоматизированных систем"))
                .isFalse();
        assertThat(ImportMatchingService.looksLikeGroupNumber("каникулярный отпуск")).isFalse();
    }

    @Test
    @DisplayName("Нестандартный номер НЕ отсекается: он должен попасть в отчёт, а не исчезнуть")
    void unusualNumbersSurvive() {
        // Разбор про такие скажет «номер не по стандарту» — это находка для человека, и терять её
        // здесь нельзя: правило проверяет форму, а не соответствие нумерации.
        assertThat(ImportMatchingService.looksLikeGroupNumber("9-11а")).isTrue();
        assertThat(ImportMatchingService.looksLikeGroupNumber("911/2-1")).isTrue();
    }

    @Test
    @DisplayName("Пустое и null не проходят и не роняют проверку")
    void isTotal() {
        assertThat(ImportMatchingService.looksLikeGroupNumber(null)).isFalse();
        assertThat(ImportMatchingService.looksLikeGroupNumber("   ")).isFalse();
        assertThat(ImportMatchingService.looksLikeGroupNumber("ЭкзС")).isFalse();
    }
}
