package ru.services.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.CellForLesson;
import ru.enums.TimeSlotPair;
import ru.services.constraints.AllConstraints;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Два workspace разных периодов, живущие одновременно.
 *
 * <p>Это тест на <b>исходный дефект</b>, а не на новый класс. Раньше «какие ячейки существуют»
 * хранилось в статике на всю JVM: создание второго workspace очищало и перезаполняло её, и первый
 * — уже созданный, с уже засеянными размещениями — начинал отвечать про чужой период. Одним
 * пользователем это не ловилось; двумя вкладками, двумя сессиями или двумя инстансами — ловится.
 * Здесь второй workspace создаётся <b>после</b> первого, и проверяется, что первый не изменился.</p>
 *
 * <p>Сетка ({@code ScheduleGrid}) проверяется отдельно от календаря сознательно: прежний
 * конструктор принимал даты периода и <b>не использовал их</b>, заполняясь из той же статики, —
 * то есть даты сетки и её содержимое могли расходиться, и именно это расхождение не поймал бы
 * тест одного лишь календаря.</p>
 */
class ScheduleWorkspaceCalendarTest {

    private static final LocalDate AUTUMN_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate AUTUMN_END = LocalDate.of(2026, 12, 31);
    private static final LocalDate SPRING_START = LocalDate.of(2027, 2, 8);
    private static final LocalDate SPRING_END = LocalDate.of(2027, 6, 30);

    private static final LocalDate AUTUMN_DAY = LocalDate.of(2026, 10, 15); // четверг
    private static final LocalDate SPRING_DAY = LocalDate.of(2027, 3, 15);  // понедельник

    @Test
    @DisplayName("Созданный вторым workspace не меняет ответы первого")
    void secondWorkspaceDoesNotRewriteTheFirst() {
        ScheduleWorkspace autumn = workspace(AUTUMN_START, AUTUMN_END);
        int autumnCellsBefore = autumn.getCalendar().cells().size();

        ScheduleWorkspace spring = workspace(SPRING_START, SPRING_END);

        assertThat(autumn.getCalendar().cells())
                .as("осенний календарь после создания весеннего")
                .hasSize(autumnCellsBefore);
        assertThat(autumn.getCalendar().cellAt(AUTUMN_DAY, TimeSlotPair.FIRST))
                .as("свой день остался в своём периоде")
                .isPresent();
        assertThat(autumn.getCalendar().cellAt(SPRING_DAY, TimeSlotPair.FIRST))
                .as("чужой день в свой период не попал")
                .isEmpty();
        assertThat(spring.getCalendar().cellAt(SPRING_DAY, TimeSlotPair.FIRST)).isPresent();
        assertThat(spring.getCalendar().cellAt(AUTUMN_DAY, TimeSlotPair.FIRST)).isEmpty();
    }

    @Test
    @DisplayName("Сетка workspace построена по его же периоду, а не по чужому")
    void gridBelongsToItsOwnPeriod() {
        ScheduleWorkspace autumn = workspace(AUTUMN_START, AUTUMN_END);
        workspace(SPRING_START, SPRING_END); // раньше этот вызов «переписывал» сетку осени

        var gridCells = autumn.getGrid().getGridMap().keySet();

        assertThat(gridCells)
                .as("ячейки сетки — ровно ячейки календаря этого периода")
                .containsExactlyInAnyOrderElementsOf(autumn.getCalendar().cells());
        assertThat(gridCells).contains(new CellForLesson(AUTUMN_DAY, TimeSlotPair.FIRST));
        assertThat(gridCells).doesNotContain(new CellForLesson(SPRING_DAY, TimeSlotPair.FIRST));
        assertThat(autumn.getGrid().getStartDate()).isEqualTo(AUTUMN_START);
        assertThat(autumn.getGrid().getEndDate()).isEqualTo(AUTUMN_END);
    }

    /** Workspace без ресурсов: проверяется календарь и сетка, а не подбор — людей и комнат не нужно. */
    private ScheduleWorkspace workspace(LocalDate start, LocalDate end) {
        return new ScheduleWorkspace(start, end, List.of(), List.of(), List.of(),
                new AllConstraints(Map.of(), Map.of(), Map.of()));
    }
}
