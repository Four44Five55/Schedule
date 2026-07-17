package ru.services.reindex;

import java.util.List;
import java.util.Set;

/**
 * Вход политики подбора комнат при пересортировке: занятия класса и снимок занятости тех, кто
 * в класс не входит и не двигается.
 *
 * @param classPlacements занятия пересортировываемого класса (в любом порядке)
 * @param occupiedByOthers какие комнаты в каких ячейках заняты <b>не-классными</b> размещениями —
 *                         единственное, с чем может столкнуться переезжающая жёсткая комната
 */
public record ReorderRoomContext(
        List<ReorderRoomInput> classPlacements,
        Set<RoomSlot> occupiedByOthers
) {
    public ReorderRoomContext {
        classPlacements = List.copyOf(classPlacements);
        occupiedByOthers = Set.copyOf(occupiedByOthers);
    }
}
