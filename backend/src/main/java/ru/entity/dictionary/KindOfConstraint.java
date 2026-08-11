package ru.entity.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.constraints.ConstraintKindRef;

/**
 * Вид ограничения: командировка, отпуск, наряд, учения — что угодно, что заводит пользователь.
 *
 * <p>Раньше это был Java-enum {@code ru.enums.KindOfConstraints}, и новый вид требовал релиза.
 * Перечень переехал в справочник, потому что код по видам не ветвится: вид ограничения — подпись
 * и цвет, а поведение («ресурс занят в эти дни/пары») одинаково для всех. Виды ЗАНЯТИЙ, наоборот,
 * остаются в коде — от них зависит распределение и порядок изучения (правило — в CLAUDE.md).</p>
 *
 * <p><b>Ключ строковый, а не {@code SERIAL}.</b> В таблицах ограничений уже лежат коды видов
 * строками; числовой id потребовал бы переписать данные, запросы и фронт. Пользовательские виды
 * получают служебный код {@code USER_<n>} — человеку он не показывается.</p>
 *
 * <p><b>Не наследует {@link AbstractEducatorDictionary}</b>: у того ключ — {@code Integer id},
 * здесь ключ смысловой и строковый, плюс есть цвет и признак системного. Общей была бы только
 * пара полей — наследование ради этого связало бы два справочника, живущих по разным правилам.</p>
 */
@Entity
@Table(name = "kind_of_constraint")
@Getter
@Setter
@NoArgsConstructor
public class KindOfConstraint {

    /** Ключ. У пришедших из кода — прежнее имя enum; неизменен: на него ссылаются ограничения. */
    @Id
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /** Полное название: «Командировка», «Государственная итоговая аттестация». */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /** Сокращение для ячейки сетки: «Ком», «ГИА». Уникально — по нему вид узнают в расписании. */
    @Column(name = "short_name", nullable = false, unique = true, length = 50)
    private String shortName;

    /**
     * Ключ палитры фронта ({@code amber}, {@code sky}, {@code rose}…), а НЕ hex-код: Tailwind
     * собирает классы статически, и цвет из базы в вёрстку иначе не подставить.
     */
    @Column(name = "color", nullable = false, length = 20)
    private String color = "slate";

    /** Порядок в списке; шаг 10 оставляет место вставить вид между существующими. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Погашенный вид не предлагается в выборе, но уже проставленный показывается. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Пришёл из кода: код неизменен, удалять нельзя. Название и цвет правятся свободно. */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    /** Снимок для доменного ядра — чтобы решатель не тащил в себя JPA-сущность. */
    public ConstraintKindRef toRef() {
        return new ConstraintKindRef(code, name, shortName, sortOrder == null ? 0 : sortOrder);
    }
}
