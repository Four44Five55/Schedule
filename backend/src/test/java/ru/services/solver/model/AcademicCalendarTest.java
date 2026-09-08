package ru.services.solver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.CellForLesson;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Учебный календарь периода — «какие ячейки в нём существуют».
 *
 * <p>До 2026-08-27 на этот вопрос отвечала статическая карта на всю JVM
 * ({@code CellForLessonFactory}), которую очищал и перезаполнял каждый запрос. Ни один тест здесь
 * не был бы возможен в прежнем виде: результат зависел бы от того, кто вызвал
 * {@code initializeCellCache} последним — то есть от порядка тестов. Поэтому проверяется не только
 * содержимое календаря, но и <b>независимость двух календарей</b>: ровно то свойство, ради
 * которого статика и убиралась.</p>
 */
class AcademicCalendarTest {

    // Понедельник 2026-09-07 … воскресенье 2026-09-13 — полная учебная неделя.
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    @Test
    @DisplayName("Неделя периода: Пн–Пт по 4 пары, суббота без 4-й, воскресенья нет вовсе")
    void weekFollowsSlotPolicy() {
        AcademicCalendar calendar = AcademicCalendar.of(MONDAY, SUNDAY);

        assertThat(calendar.cellsOn(MONDAY)).hasSize(4);
        assertThat(calendar.cellsOn(SATURDAY))
                .as("суббота — три пары: 4-я закрыта правилом")
                .hasSize(3);
        assertThat(calendar.cellsOn(SUNDAY))
                .as("воскресенье не учебное — ячеек нет")
                .isEmpty();
        assertThat(calendar.dates())
                .as("день без единой пары в календарь не попадает")
                .doesNotContain(SUNDAY)
                .hasSize(6);
        assertThat(calendar.cells()).hasSize(4 * 5 + 3);
    }

    @Test
    @DisplayName("Порядок — часть контракта: даты по возрастанию, внутри дня — порядок пар")
    void orderIsPartOfTheContract() {
        AcademicCalendar calendar = AcademicCalendar.of(MONDAY, SUNDAY);

        assertThat(calendar.dates()).isSorted();
        assertThat(calendar.cellsOn(MONDAY))
                .extracting(CellForLesson::getTimeSlotPair)
                .containsExactly(TimeSlotPair.FIRST, TimeSlotPair.SECOND,
                        TimeSlotPair.THIRD, TimeSlotPair.FOURTH);

        // Сквозной порядок: первая ячейка календаря — первая пара первого учебного дня.
        assertThat(calendar.cells().get(0))
                .isEqualTo(new CellForLesson(MONDAY, TimeSlotPair.FIRST));
    }

    @Test
    @DisplayName("Списки неизменяемы — вызывающий не может отсортировать чужой календарь под себя")
    void returnedListsAreImmutable() {
        AcademicCalendar calendar = AcademicCalendar.of(MONDAY, SUNDAY);

        assertThatThrownBy(() -> calendar.cells().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> calendar.cellsOn(MONDAY).sort(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> calendar.dates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("«Слот вне периода» — ответ календаря, а не догадка вызывающего")
    void cellAtAnswersMembership() {
        AcademicCalendar calendar = AcademicCalendar.of(MONDAY, SATURDAY);

        assertThat(calendar.cellAt(MONDAY, TimeSlotPair.FIRST))
                .contains(new CellForLesson(MONDAY, TimeSlotPair.FIRST));
        assertThat(calendar.cellAt(SATURDAY, TimeSlotPair.FOURTH))
                .as("суббота 4-я пара закрыта правилом — в периоде такой ячейки нет")
                .isEmpty();
        assertThat(calendar.cellAt(SUNDAY, TimeSlotPair.FIRST)).isEmpty();
        assertThat(calendar.cellAt(MONDAY.minusDays(1), TimeSlotPair.FIRST))
                .as("день раньше начала периода")
                .isEmpty();
        assertThat(calendar.cellAt(SATURDAY.plusDays(7), TimeSlotPair.FIRST))
                .as("день позже конца периода")
                .isEmpty();
        assertThat(calendar.cellAt(null, null)).isEmpty();
    }

    @Test
    @DisplayName("Два календаря разных периодов независимы — осень ничего не знает о весне")
    void calendarsOfDifferentPeriodsDoNotAffectEachOther() {
        AcademicCalendar autumn = AcademicCalendar.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        AcademicCalendar spring = AcademicCalendar.of(LocalDate.of(2027, 2, 8), LocalDate.of(2027, 6, 30));

        LocalDate autumnDay = LocalDate.of(2026, 10, 15);
        LocalDate springDay = LocalDate.of(2027, 3, 15);

        assertThat(autumn.cellAt(autumnDay, TimeSlotPair.FIRST)).isPresent();
        assertThat(autumn.cellAt(springDay, TimeSlotPair.FIRST))
                .as("осенний период не отвечает про весенний день")
                .isEmpty();
        assertThat(spring.cellAt(springDay, TimeSlotPair.FIRST)).isPresent();
        assertThat(spring.cellAt(autumnDay, TimeSlotPair.FIRST)).isEmpty();

        // Именно это и ломалось раньше: ответ зависел от того, чей период инициализировали последним.
        assertThat(autumn.cells()).doesNotContainAnyElementsOf(spring.cells());
    }

    @Test
    @DisplayName("Пустой период — пустой календарь, а не исключение")
    void emptyPeriodIsLegal() {
        AcademicCalendar calendar = AcademicCalendar.of(MONDAY, MONDAY.minusDays(1));

        assertThat(calendar.cells()).isEmpty();
        assertThat(calendar.dates()).isEmpty();
        assertThat(calendar.cellAt(MONDAY, TimeSlotPair.FIRST)).isEmpty();
    }

    @Test
    @DisplayName("Политика пар — параметр: другая сетка строится без правки потребителей")
    void slotPolicyIsAParameter() {
        // Гипотетический период, где учебными считаются только первые две пары.
        AcademicCalendar.SlotPolicy twoPairs =
                (date, slot) -> slot == TimeSlotPair.FIRST || slot == TimeSlotPair.SECOND;

        AcademicCalendar calendar = AcademicCalendar.of(MONDAY, MONDAY, twoPairs);

        assertThat(calendar.cellsOn(MONDAY))
                .extracting(CellForLesson::getTimeSlotPair)
                .containsExactly(TimeSlotPair.FIRST, TimeSlotPair.SECOND);
        assertThat(calendar.cellAt(MONDAY, TimeSlotPair.FOURTH)).isEmpty();
    }

    @Test
    @DisplayName("Дата и политика обязательны — календарь без них не значит ничего")
    void argumentsAreRequired() {
        List<Runnable> broken = List.of(
                () -> AcademicCalendar.of(null, MONDAY),
                () -> AcademicCalendar.of(MONDAY, null),
                () -> AcademicCalendar.of(MONDAY, MONDAY, null));

        broken.forEach(call -> assertThatThrownBy(call::run)
                .isInstanceOf(IllegalArgumentException.class));
    }
}
