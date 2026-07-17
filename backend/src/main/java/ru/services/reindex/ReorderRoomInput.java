package ru.services.reindex;

import java.util.Set;
import java.util.UUID;

/**
 * Одно занятие класса на вход политике подбора комнат при пересортировке — в pure-виде
 * (без JPA), чтобы {@link ReorderRoomResolver} оставался чистой функцией и тестировался юнитами.
 *
 * <p>Сборку из сущностей делает {@code TrackReorderService}; сюда доходит только то, что нужно
 * решению.</p>
 *
 * @param placementId    размещение
 * @param currentCell    где занятие стоит сейчас
 * @param targetCell     куда его ставит перестановка ({@code == currentCell}, если не переезжает)
 * @param currentRoomIds нынешние комнаты занятия (их может быть несколько)
 * @param hardRoomBound  у слота жёсткая привязка комнаты (required-аудитория или пул) — тогда
 *                       комната обязана ехать с занятием, а не оставаться с ячейкой
 * @param baseRoomId     базовая аудитория группы для запасного варианта; {@code null}, если нет
 */
public record ReorderRoomInput(
        UUID placementId,
        Cell currentCell,
        Cell targetCell,
        Set<Integer> currentRoomIds,
        boolean hardRoomBound,
        Integer baseRoomId
) {
    public ReorderRoomInput {
        currentRoomIds = Set.copyOf(currentRoomIds);
    }

    /** Переезжает ли занятие (иначе комнаты не трогаем вовсе). */
    public boolean moved() {
        return !currentCell.equals(targetCell);
    }
}
