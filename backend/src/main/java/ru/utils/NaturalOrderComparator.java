package ru.utils;

import java.util.Comparator;

/**
 * Естественный (численно-осведомлённый) порядок строк: числовые сегменты
 * сравниваются как числа, поэтому «гр-2» &lt; «гр-10», а не наоборот.
 *
 * Аналог фронтового {@code localeCompare(other, undefined, { numeric: true })}.
 * Используется для стабильного и читаемого порядка имён групп в ответах API
 * (чтобы номера в ячейке не «скакали» и шли по возрастанию номера, а не по коду).
 */
public final class NaturalOrderComparator implements Comparator<String> {

    public static final NaturalOrderComparator INSTANCE = new NaturalOrderComparator();

    private NaturalOrderComparator() {
    }

    @Override
    public int compare(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = stripLeadingZeros(a.substring(si, i));
                String nb = stripLeadingZeros(b.substring(sj, j));
                // Короче число — меньше значение (после снятия ведущих нулей).
                if (na.length() != nb.length()) return na.length() - nb.length();
                int cmp = na.compareTo(nb);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(ca, cb);
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        // Более короткая строка (исчерпалась раньше) идёт первой.
        return (a.length() - i) - (b.length() - j);
    }

    private static String stripLeadingZeros(String s) {
        int k = 0;
        while (k < s.length() - 1 && s.charAt(k) == '0') k++;
        return s.substring(k);
    }
}
