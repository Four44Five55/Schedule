package ru.enums;

/**
 * Вид подразделения организации.
 *
 * <p>Единственный владелец политики вложенности: подразделения лежат в одной самоссылочной
 * таблице {@code org_unit}, и «кто в ком может лежать» задаётся здесь <b>рангом</b>, а не
 * матрицей допустимых пар. Родитель допустим, если его ранг строго меньше ранга ребёнка
 * (институт 10 → факультет 20 → кафедра 30). Матрица потребовала бы N² правок на каждый новый
 * вид; ранг — одной строки.</p>
 *
 * <p>Ранги идут с шагом 10, чтобы новый вид можно было вставить между существующими, не
 * перенумеровывая остальные. Одинаковый ранг означает «соседи по этажу»: отдел и факультет
 * друг друга не вкладывают.</p>
 *
 * <p><b>Чего здесь сознательно нет:</b> правил «кого можно приписать к этому виду»
 * (преподавателя — к кафедре или отделу, группу — к кафедре или факультету). Такие ограничения
 * легко оказываются неверными на живых данных (декан на факультете, преподаватель отдела
 * подготовки), а неверный жёсткий запрет блокирует ввод. Пока это соглашение, а не код.</p>
 *
 * <p>Копия значений лежит в таблице-справочнике {@code org_unit_type} — ради FK и читаемости
 * сырого SQL, как {@code kind_of_study} при {@link KindOfStudy}. Источник правды — этот enum.</p>
 */
public enum OrgUnitType {

    INSTITUTE("Институт", "Ин-т", 10),
    FACULTY("Факультет", "Ф-т", 20),
    DIVISION("Отдел", "Отд.", 20),
    DEPARTMENT("Кафедра", "Каф.", 30);

    private final String fullName;
    private final String abbreviationName;
    private final int nestingRank;

    OrgUnitType(String fullName, String abbreviationName, int nestingRank) {
        this.fullName = fullName;
        this.abbreviationName = abbreviationName;
        this.nestingRank = nestingRank;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviationName() {
        return abbreviationName;
    }

    public int getNestingRank() {
        return nestingRank;
    }

    /**
     * Может ли подразделение этого вида быть родителем для подразделения вида {@code child}.
     *
     * @param child вид вложенного подразделения
     * @return true, если ранг родителя строго меньше ранга ребёнка
     */
    public boolean canContain(OrgUnitType child) {
        return child != null && this.nestingRank < child.nestingRank;
    }
}
