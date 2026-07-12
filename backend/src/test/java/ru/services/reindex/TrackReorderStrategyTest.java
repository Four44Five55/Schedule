package ru.services.reindex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поведение {@link TrackReorderStrategy}: пересортировка трека в порядок плана после
 * переноса (меняются только даты, тема едет с занятием).
 */
class TrackReorderStrategyTest {

    private final TrackReorderStrategy strategy = new TrackReorderStrategy();

    // 7 дат = 7 ячеек (по одной паре на дату для наглядности).
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);
    private static final LocalDate D4 = LocalDate.of(2026, 9, 4);
    private static final LocalDate D5 = LocalDate.of(2026, 9, 5);
    private static final LocalDate D6 = LocalDate.of(2026, 9, 6);

    /** Занятие: размещение id, ячейка (date, FIRST), slotId=плановая позиция для простоты. */
    private static TimedLesson at(UUID id, LocalDate date, int planPosition) {
        return new TimedLesson(id, date, TimeSlotPair.FIRST, planPosition, planPosition);
    }

    private static TimedLesson at(UUID id, LocalDate date, TimeSlotPair slot, int planPosition) {
        return new TimedLesson(id, date, slot, planPosition, planPosition);
    }

    private static Map<UUID, Cell> movesById(ReorderPlan plan) {
        return plan.moves().stream().collect(Collectors.toMap(
                CellMove::placementId, m -> new Cell(m.date(), m.slot())));
    }

    private static Set<UUID> problemIds(ReorderPlan plan) {
        return plan.problems().stream().map(ReorderProblem::placementId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Кейс заказчика: 6-е перенесли в раннюю ячейку — пузырьком возвращается в плановый порядок")
    void bubblesBackToPlanOrder() {
        // После переноса по времени: 1, 6, 2, 3, 4, 5 (ячейки D1..D6).
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(),
                p4 = UUID.randomUUID(), p5 = UUID.randomUUID(), p6 = UUID.randomUUID();
        var lessons = List.of(
                at(p1, D1, 1),
                at(p6, D2, 6),
                at(p2, D3, 2),
                at(p3, D4, 3),
                at(p4, D5, 4),
                at(p5, D6, 5));

        var moves = movesById(strategy.resort(lessons, List.of()));

        // Ожидаем плановый порядок: D1=1, D2=2, D3=3, D4=4, D5=5, D6=6.
        assertFalse(moves.containsKey(p1), "1-е уже на месте (D1)");
        assertEquals(new Cell(D2, TimeSlotPair.FIRST), moves.get(p2), "2-е встало на D2");
        assertEquals(new Cell(D3, TimeSlotPair.FIRST), moves.get(p3), "3-е на D3");
        assertEquals(new Cell(D4, TimeSlotPair.FIRST), moves.get(p4), "4-е на D4");
        assertEquals(new Cell(D5, TimeSlotPair.FIRST), moves.get(p5), "5-е на D5");
        assertEquals(new Cell(D6, TimeSlotPair.FIRST), moves.get(p6), "6-е доехало до D6");
    }

    @Test
    @DisplayName("Раннее занятие перенесли позже — пузырьком уезжает влево (обратный случай)")
    void bubblesEarlyLessonLeft() {
        // После переноса по времени: 2, 3, 4, 1 (1-е уехало в конец).
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID();
        var lessons = List.of(
                at(p2, D1, 2),
                at(p3, D2, 3),
                at(p4, D3, 4),
                at(p1, D4, 1));

        var moves = movesById(strategy.resort(lessons, List.of()));

        // Плановый порядок: D1=1, D2=2, D3=3, D4=4. p1 «пузырьком» уезжает в начало (D4→D1),
        // а 2/3/4 сдвигаются на одну ячейку вправо — двигаются ВСЕ четыре, включая p4 (D3→D4).
        assertEquals(new Cell(D1, TimeSlotPair.FIRST), moves.get(p1), "1-е вернулось в начало (D1)");
        assertEquals(new Cell(D2, TimeSlotPair.FIRST), moves.get(p2), "2-е на D2");
        assertEquals(new Cell(D3, TimeSlotPair.FIRST), moves.get(p3), "3-е на D3");
        assertEquals(new Cell(D4, TimeSlotPair.FIRST), moves.get(p4), "4-е доехало до D4");
    }

    @Test
    @DisplayName("Уже в порядке плана — переездов нет")
    void noMovesWhenAlreadyOrdered() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        var lessons = List.of(at(p1, D1, 1), at(p2, D2, 2), at(p3, D3, 3));

        var plan = strategy.resort(lessons, List.of());

        assertTrue(plan.moves().isEmpty(), "всё уже по плану — ничего не двигаем");
        assertTrue(plan.problems().isEmpty());
    }

    @Test
    @DisplayName("Сцепка сохраняется: связанные слоты 2-3 легли на соседние пары одного дня")
    void keepsChainWhenAdjacent() {
        // Слоты 2 и 3 сцеплены; их ячейки — один день D2, пары FIRST/SECOND.
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID();
        var lessons = List.of(
                at(p1, D1, TimeSlotPair.FIRST, 1),
                at(p2, D2, TimeSlotPair.FIRST, 2),
                at(p3, D2, TimeSlotPair.SECOND, 3),
                at(p4, D3, TimeSlotPair.FIRST, 4));

        var plan = strategy.resort(lessons, List.of(new int[]{2, 3}));

        assertTrue(plan.problems().isEmpty(), "2-3 на соседних парах одного дня — сцепка цела");
    }

    @Test
    @DisplayName("Сцепка рвётся: связанные слоты после пересортировки оказались в разные дни")
    void breaksChainAcrossDays() {
        // Слоты 2-3 сцеплены, но их плановые позиции легли на ячейки разных дней.
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        var lessons = List.of(
                at(p1, D1, 1),
                at(p2, D2, 2),
                at(p3, D3, 3));

        var plan = strategy.resort(lessons, List.of(new int[]{2, 3}));

        // 2→D2/FIRST, 3→D3/FIRST — разные дни → разрыв.
        assertEquals(Set.of(p2, p3), problemIds(plan), "сцепка 2-3 разъехалась по дням");
    }
}
