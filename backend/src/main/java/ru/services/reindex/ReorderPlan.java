package ru.services.reindex;

import java.util.List;

/**
 * Результат переупорядочивания трека: какие размещения переехали в другие ячейки
 * ({@code moves}) и какие помечены «под флаг» с причиной ({@code problems}) — например,
 * распавшаяся сцепка. Применение переездов — на слое персистентности, не здесь.
 *
 * @param moves    переезды размещений (только реально изменившиеся ячейки)
 * @param problems размещения под флагом (размещение + причина) для предупреждений
 */
public record ReorderPlan(List<CellMove> moves, List<ReorderProblem> problems) {

    public ReorderPlan {
        moves = List.copyOf(moves);
        problems = List.copyOf(problems);
    }
}
