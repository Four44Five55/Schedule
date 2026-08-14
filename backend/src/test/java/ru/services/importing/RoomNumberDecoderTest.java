package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.services.importing.RoomNumberDecoder.RoomNumber;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты расшифровки номера аудитории.
 *
 * <p>Все примеры — из живой выгрузки группы 911: там встречаются восемь разных мест, семь с
 * корпусом и одно без («Сп. зал»).</p>
 */
class RoomNumberDecoderTest {

    @Test
    @DisplayName("«252-3» — комната 252 в корпусе 3")
    void suffixIsBuilding() {
        RoomNumber room = RoomNumberDecoder.decode("252-3");

        assertThat(room.name()).isEqualTo("252");
        assertThat(room.building()).isEqualTo("3");
        assertThat(room.buildingUnknown()).isFalse();
    }

    @Test
    @DisplayName("Корпус 1 и корпус 3 различают одноимённые комнаты")
    void sameNameDifferentBuildings() {
        assertThat(RoomNumberDecoder.decode("241-1").building()).isEqualTo("1");
        assertThat(RoomNumberDecoder.decode("241-3").building()).isEqualTo("3");
        assertThat(RoomNumberDecoder.decode("241-1").name())
                .isEqualTo(RoomNumberDecoder.decode("241-3").name());
    }

    @Test
    @DisplayName("«Сп. зал» — имя без корпуса, и это не ошибка")
    void roomWithoutBuilding() {
        RoomNumber room = RoomNumberDecoder.decode("Сп. зал");

        assertThat(room.name()).isEqualTo("Сп. зал");
        assertThat(room.buildingUnknown()).isTrue();
    }

    @Test
    @DisplayName("Нечисловой суффикс за корпус не принимается — остаётся частью имени")
    void nonNumericSuffixStaysInName() {
        RoomNumber room = RoomNumberDecoder.decode("Зал-А");

        assertThat(room.name()).isEqualTo("Зал-А");
        assertThat(room.buildingUnknown()).isTrue();
    }

    @Test
    @DisplayName("Корпус берётся по ПОСЛЕДНЕМУ дефису")
    void buildingIsTakenFromTheLastDash() {
        RoomNumber room = RoomNumberDecoder.decode("252-1-3");

        assertThat(room.name()).isEqualTo("252-1");
        assertThat(room.building()).isEqualTo("3");
    }

    @Test
    @DisplayName("Висящий дефис и пустая часть номер не портят")
    void danglingDashIsKept() {
        assertThat(RoomNumberDecoder.decode("252-").name()).isEqualTo("252-");
        assertThat(RoomNumberDecoder.decode("-3").name()).isEqualTo("-3");
    }

    @Test
    @DisplayName("Тотальность: null и пустая строка дают пусто, а не исключение")
    void decoderIsTotal() {
        assertThat(RoomNumberDecoder.decode(null)).isNull();
        assertThat(RoomNumberDecoder.decode("   ")).isNull();
    }
}
