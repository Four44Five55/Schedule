package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.enums.OrgUnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Подразделение организации: факультет, кафедра, отдел, институт.
 *
 * <p>Одна сущность на все виды (Composite): узел и лист — это одна и та же строка, вид задаётся
 * полем {@link #type}, вложенность — ссылкой на {@link #parent}. Дерево произвольной глубины;
 * {@code parent == null} означает верхний уровень, что легитимно и для кафедры вне факультета.</p>
 *
 * <p>Это <b>master-данные и область видимости</b>, а не планируемый ресурс: у подразделения нет
 * занятости, оно не участвует в солвере и не имеет {@code SchedulableResource}. Его роль —
 * принадлежность преподавателей и групп и фильтрация по ней.</p>
 *
 * <p>Допустимость родителя (ранг вида, отсутствие цикла) проверяет
 * {@code OrgUnitHierarchyRule} — схема этого не знает.</p>
 */
@Entity
@Table(name = "org_unit")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Полное название («Кафедра высшей математики»).
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Краткое название для сеток и выпадающих списков («ВМ»). Необязательно.
     */
    @Column(name = "short_name")
    private String shortName;

    /**
     * Вид подразделения; задаёт его этаж в иерархии (см. {@link OrgUnitType#canContain}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private OrgUnitType type;

    /**
     * Вышестоящее подразделение; {@code null} — верхний уровень.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrgUnit parent;

    /**
     * Непосредственно вложенные подразделения (только один уровень вниз).
     */
    @OneToMany(mappedBy = "parent")
    private List<OrgUnit> children = new ArrayList<>();

    /**
     * Действующее подразделение. Расформированное скрывается из выбора, но остаётся в базе —
     * на него ссылаются исторические данные.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public OrgUnit(String name, OrgUnitType type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrgUnit that = (OrgUnit) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
