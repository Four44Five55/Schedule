package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ключ подразделения: приводит к одному виду то, что разные каналы выгрузки пишут по-разному.
 *
 * <p>Повод — живой прогон 2026-08-15: одна кафедра приехала в отчёт четырьмя строками («51»,
 * «51 кафедра», «53», «53 кафедра»), потому что номер группы даёт голую цифру, а шапка — цифру со
 * словом. Заведение по такому отчёту сделало бы из одной кафедры два узла дерева.</p>
 */
class OrgUnitKeyTest {

    @Test
    @DisplayName("Цифра и цифра со словом дают один ключ")
    void wordIsIgnored() {
        assertThat(ImportMatchingService.orgUnitKey("51 кафедра")).isEqualTo("51");
        assertThat(ImportMatchingService.orgUnitKey("Каф. 51")).isEqualTo("51");
        assertThat(ImportMatchingService.orgUnitKey(" 51 ")).isEqualTo("51");
    }

    @Test
    @DisplayName("Разные подразделения ключом не сливаются")
    void differentUnitsStayDifferent() {
        assertThat(ImportMatchingService.orgUnitKey("51 кафедра"))
                .isNotEqualTo(ImportMatchingService.orgUnitKey("53 кафедра"));
    }

    @Test
    @DisplayName("Имя факультета не трогается — там слова нет")
    void facultyCodeSurvives() {
        assertThat(ImportMatchingService.orgUnitKey("9Ф")).isEqualTo("9ф");
    }

    @Test
    @DisplayName("Именованное подразделение сохраняет имя, теряя только слово-приставку")
    void namedUnitKeepsItsName() {
        assertThat(ImportMatchingService.orgUnitKey("Кафедра информатики")).isEqualTo("информатики");
    }

    @Test
    @DisplayName("Пустое и null не роняют разбор")
    void isTotal() {
        assertThat(ImportMatchingService.orgUnitKey(null)).isEmpty();
        assertThat(ImportMatchingService.orgUnitKey("   ")).isEmpty();
    }
}
