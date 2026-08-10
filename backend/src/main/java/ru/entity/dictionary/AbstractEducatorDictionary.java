package ru.entity.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Общая форма простого справочника, который ведёт пользователь: имя, сокращение, порядок,
 * признак активности.
 *
 * <p><b>{@code @MappedSuperclass}, а не одна таблица с колонкой «вид».</b> Общая таблица не
 * позволила бы БД отличить звание от отрасли науки: {@code educator.special_rank_id} мог бы
 * указывать на строку-отрасль, и запретить это было бы нечем. Здесь же наследуется только форма,
 * а каждая ссылка ведёт в свою таблицу — инвариант держит FK, как в остальном проекте.</p>
 *
 * <p>Класс несёт форму, а не поведение: правила («сокращение уникально», «за строкой числятся
 * люди») живут в схеме и в сервисе. Это сознательно не повторение истории
 * {@code AbstractMaterialEntity} — того удалили за то, что он тащил в JPA-сущность занятость
 * ресурса, то есть поведение чужого слоя.</p>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AbstractEducatorDictionary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Полное имя: «полковник», «технические». */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Сокращение <b>без точек</b>: «п-к», «т», «юст». Уникально — по нему собирается подпись
     * преподавателя и будет опознаваться звание при разборе чужого файла на импорте.
     */
    @Column(name = "short_name", nullable = false, unique = true)
    private String shortName;

    /**
     * Порядок в списке: звания идут по старшинству, а не по алфавиту. Шаг 10 оставляет место
     * вставить значение между существующими.
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * Устаревшее значение гасится, а не удаляется: за ним остаются люди, а удаление упёрлось бы
     * в {@code RESTRICT}. Погашенное не предлагается в выборе, но уже выбранное показывается.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
