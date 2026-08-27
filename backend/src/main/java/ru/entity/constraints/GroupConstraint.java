package ru.entity.constraints;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.entity.Group;
import ru.entity.dictionary.KindOfConstraint;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;

@Entity
@jakarta.persistence.EntityListeners(ru.services.workspace.ConstraintChangeListener.class)
@Getter
@Setter
@Table(name = "group_constraint")
public class GroupConstraint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

    /** Вид ограничения — строка справочника. EAGER: см. {@link EducatorConstraint}. */
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
