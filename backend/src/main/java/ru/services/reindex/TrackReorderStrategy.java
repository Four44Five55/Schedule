package ru.services.reindex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Пересортировка трека в порядок плана (после переноса). Чистая функция (без БД),
 * тестируется юнитами.
 *
 * <p><b>Правило (согласовано с заказчиком).</b> Занятия класса всегда должны идти в
 * порядке изучения ({@code planPosition}). После того как одно занятие перенесли (и оно
 * нарушило порядок), содержимое ячеек пересобирается так, чтобы i-я по времени ячейка
 * получила i-е по плану занятие — «пузырёк»: перенесённое встаёт на своё плановое место,
 * а те, через кого оно перепрыгнуло, сдвигаются на одну ячейку.</p>
 *
 * <p><b>Механизм — только даты.</b> Каждое занятие сохраняет своё содержание; меняется
 * лишь его дата/пара (тема едет с занятием). Ячейки берутся те же, что класс уже занимает
 * (перенос лишь поменял их набор — освободил исходную, занял целевую).</p>
 *
 * <p><b>Предусловие:</b> на вход — ОДИН однородный класс (один {@code StudyStream} +
 * набор преподавателей); (дата, пара) в нём уникальны. Разбиение по идентичности — на
 * сборочном слое.</p>
 *
 * <p><b>Сцепки (P2):</b> если связанные слоты после пересортировки оказались на несоседних
 * ячейках — они всё равно раскладываются, но пара помечается
 * {@link ReorderProblem.Reason#CHAIN_BROKEN}.</p>
 */
public class TrackReorderStrategy {

    private static final Comparator<TimedLesson> BY_TIME =
            Comparator.comparing(TimedLesson::date)
                    .thenComparingInt(l -> l.slot().ordinal());

    private static final Comparator<TimedLesson> BY_PLAN =
            Comparator.comparingInt(TimedLesson::planPosition);

    /**
     * @param lessons          занятия класса в их текущих ячейках (в любом порядке)
     * @param chainedSlotPairs пары сцепленных слотов {@code {slotAId, slotBId}} этого класса
     */
    public ReorderPlan resort(List<TimedLesson> lessons, List<int[]> chainedSlotPairs) {
        // Ячейки класса в порядке времени — целевые позиции.
        List<TimedLesson> byTime = new ArrayList<>(lessons);
        byTime.sort(BY_TIME);
        List<Cell> cells = byTime.stream().map(l -> new Cell(l.date(), l.slot())).toList();

        // Занятия в порядке плана — i-е по плану встаёт в i-ю по времени ячейку.
        List<TimedLesson> byPlan = new ArrayList<>(lessons);
        byPlan.sort(BY_PLAN);

        List<CellMove> moves = new ArrayList<>();
        Map<Integer, Cell> newCellBySlot = new HashMap<>();
        Map<Integer, UUID> placementBySlot = new HashMap<>();
        for (int i = 0; i < byPlan.size(); i++) {
            TimedLesson lesson = byPlan.get(i);
            Cell cell = cells.get(i);
            newCellBySlot.put(lesson.slotId(), cell);
            placementBySlot.put(lesson.slotId(), lesson.placementId());
            if (!lesson.date().equals(cell.date()) || lesson.slot() != cell.slot()) {
                moves.add(new CellMove(lesson.placementId(), cell.date(), cell.slot()));
            }
        }

        // Сцепки: связанные слоты должны оказаться на соседних ячейках, иначе — флаг.
        List<ReorderProblem> problems = new ArrayList<>();
        Set<UUID> flagged = new HashSet<>();
        for (int[] pair : chainedSlotPairs) {
            Cell a = newCellBySlot.get(pair[0]);
            Cell b = newCellBySlot.get(pair[1]);
            if (a == null || b == null) {
                continue;
            }
            if (!adjacent(a, b)) {
                flag(problems, flagged, placementBySlot.get(pair[0]));
                flag(problems, flagged, placementBySlot.get(pair[1]));
            }
        }
        return new ReorderPlan(moves, problems);
    }

    /** Ячейки соседние: одна дата и последовательные пары. */
    private static boolean adjacent(Cell a, Cell b) {
        return a.date().equals(b.date())
                && Math.abs(a.slot().ordinal() - b.slot().ordinal()) == 1;
    }

    private static void flag(List<ReorderProblem> problems, Set<UUID> flagged, UUID placementId) {
        if (placementId != null && flagged.add(placementId)) {
            problems.add(new ReorderProblem(placementId, ReorderProblem.Reason.CHAIN_BROKEN));
        }
    }
}
