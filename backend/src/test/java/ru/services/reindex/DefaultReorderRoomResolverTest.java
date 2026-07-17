package ru.services.reindex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поведение {@link DefaultReorderRoomResolver}: комната взаимозаменяемого занятия остаётся с
 * ячейкой, комната жёсткого — едет с занятием и проверяется на занятость.
 */
class DefaultReorderRoomResolverTest {

    private final DefaultReorderRoomResolver resolver = new DefaultReorderRoomResolver();

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final Cell C1 = new Cell(D1, TimeSlotPair.FIRST);
    private static final Cell C2 = new Cell(D2, TimeSlotPair.FIRST);

    private static Set<UUID> problemIds(ReorderRoomPlan plan) {
        return plan.problems().stream().map(ReorderProblem::placementId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Взаимозаменяемые комнаты остаются с ячейками — занятия обмениваются местами и комнатами")
    void interchangeableRoomsStayWithCells() {
        UUID pA = UUID.randomUUID();
        UUID pB = UUID.randomUUID();
        // pA: c1→c2, pB: c2→c1. Ни у кого нет жёсткого требования.
        var inputs = List.of(
                new ReorderRoomInput(pA, C1, C2, Set.of(10), false, null),
                new ReorderRoomInput(pB, C2, C1, Set.of(20), false, null));

        ReorderRoomPlan plan = resolver.resolve(new ReorderRoomContext(inputs, Set.of()));

        // Комната c2 (20) досталась приехавшему в c2; комната c1 (10) — приехавшему в c1.
        assertEquals(Set.of(20), plan.roomsByPlacement().get(pA));
        assertEquals(Set.of(10), plan.roomsByPlacement().get(pB));
        assertTrue(plan.problems().isEmpty());
    }

    @Test
    @DisplayName("Жёсткая комната свободна на целевой ячейке — едет с занятием, без флага")
    void hardRoomTravelsWhenFree() {
        UUID pHard = UUID.randomUUID();
        UUID pB = UUID.randomUUID();
        var inputs = List.of(
                new ReorderRoomInput(pHard, C1, C2, Set.of(10), true, 99),
                new ReorderRoomInput(pB, C2, C1, Set.of(20), false, null));

        // Комната 10 на c2 никем не занята.
        ReorderRoomPlan plan = resolver.resolve(new ReorderRoomContext(inputs, Set.of()));

        assertEquals(Set.of(10), plan.roomsByPlacement().get(pHard)); // своя комната, не базовая
        assertTrue(plan.problems().isEmpty());
    }

    @Test
    @DisplayName("Жёсткая комната занята на целевой ячейке — занятие в базовую аудиторию + флаг")
    void hardRoomOccupiedFallsBackToBase() {
        UUID pHard = UUID.randomUUID();
        UUID pB = UUID.randomUUID();
        var inputs = List.of(
                new ReorderRoomInput(pHard, C1, C2, Set.of(10), true, 99),
                new ReorderRoomInput(pB, C2, C1, Set.of(20), false, null));

        // Комнату 10 на c2 занял кто-то вне класса.
        var occupied = Set.of(new RoomSlot(10, C2));
        ReorderRoomPlan plan = resolver.resolve(new ReorderRoomContext(inputs, occupied));

        assertEquals(Set.of(99), plan.roomsByPlacement().get(pHard)); // базовая
        assertTrue(problemIds(plan).contains(pHard));
        assertEquals(ReorderProblem.Reason.AUDITORIUM_CONFLICT, plan.problems().get(0).reason());
    }

    @Test
    @DisplayName("Жёсткая комната занята, базовой нет — оставляем свою + флаг (лучше некуда)")
    void hardRoomOccupiedNoBaseKeepsOwnAndFlags() {
        UUID pHard = UUID.randomUUID();
        UUID pB = UUID.randomUUID();
        var inputs = List.of(
                new ReorderRoomInput(pHard, C1, C2, Set.of(10), true, null),
                new ReorderRoomInput(pB, C2, C1, Set.of(20), false, null));

        var occupied = Set.of(new RoomSlot(10, C2));
        ReorderRoomPlan plan = resolver.resolve(new ReorderRoomContext(inputs, occupied));

        assertEquals(Set.of(10), plan.roomsByPlacement().get(pHard));
        assertTrue(problemIds(plan).contains(pHard));
    }

    @Test
    @DisplayName("Не переехавшее занятие комнаты не меняет — его нет в решении")
    void notMovedIsUntouched() {
        UUID pStay = UUID.randomUUID();
        var inputs = List.of(new ReorderRoomInput(pStay, C1, C1, Set.of(10), true, 99));

        ReorderRoomPlan plan = resolver.resolve(new ReorderRoomContext(inputs, Set.of()));

        assertFalse(plan.roomsByPlacement().containsKey(pStay));
        assertTrue(plan.problems().isEmpty());
    }
}
