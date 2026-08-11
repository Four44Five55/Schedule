package ru.entity.constraints;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Educator;
import ru.entity.dictionary.KindOfConstraint;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;

@Entity
@Table(name = "educator_constraint")
@Getter
@Setter
@NoArgsConstructor
public class EducatorConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "educator_id")
    private Educator educator;

    /**
     * Вид ограничения — строка справочника, а не enum (перечень ведёт пользователь).
     *
     * <p>{@code EAGER}: справочник крошечный (единицы строк), а его подписи нужны при КАЖДОМ
     * маппинге ограничения в DTO — ленивая загрузка дала бы здесь ровно N+1 запросов на ровном
     * месте. Ключ соединения строковый, поэтому {@code referencedColumnName} задан явно.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "kind_of_constraint", referencedColumnName = "code", nullable = false)
    private KindOfConstraint kindOfConstraint;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description")
    private String description;

    /** Пара ограничения; null = весь день (все пары). */
    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot")
    private TimeSlotPair timeSlot;
}