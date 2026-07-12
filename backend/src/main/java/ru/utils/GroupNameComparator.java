package ru.utils;

import java.util.Comparator;

/**
 * Порядок имён (кодов) групп. Код группы вида {@code 10933/22} — иерархический:
 * {@code /} это РАЗДЕЛИТЕЛЬ уровней принадлежности, а не деление. Поэтому сортируем
 * уровень за уровнем: сперва по части до {@code /} (числом), затем по следующей и т.д.
 *
 * GoF Strategy: подключаемая стратегия сравнения. Доменное знание о структуре кода
 * (разделение по {@code /}) живёт здесь; численное сравнение отдельного уровня
 * делегируется {@link NaturalOrderComparator} (композиция, без дублирования логики,
 * SRP). При появлении новых уровней кода менять ничего не нужно (OCP) — сравнение
 * идёт по всем сегментам, а более короткий код (меньше уровней) идёт раньше.
 */
public final class GroupNameComparator implements Comparator<String> {

    public static final GroupNameComparator INSTANCE = new GroupNameComparator();

    /** Разделитель уровней принадлежности в коде группы. */
    private static final String LEVEL_SEPARATOR = "/";

    /** Сравнение отдельного уровня — численно-осведомлённо (гр-2 < гр-10). */
    private static final Comparator<String> LEVEL = NaturalOrderComparator.INSTANCE;

    private GroupNameComparator() {
    }

    @Override
    public int compare(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        String[] levelsA = a.split(LEVEL_SEPARATOR, -1);
        String[] levelsB = b.split(LEVEL_SEPARATOR, -1);
        int common = Math.min(levelsA.length, levelsB.length);
        for (int i = 0; i < common; i++) {
            int cmp = LEVEL.compare(levelsA[i].trim(), levelsB[i].trim());
            if (cmp != 0) return cmp;
        }
        // Все общие уровни равны — короче код (меньше уровней) идёт первым.
        return levelsA.length - levelsB.length;
    }
}
