package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;
import ru.inter.IMaterialEntity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Сущность, представляющая преподавателя.
 * Реализует интерфейс Schedulable, чтобы участвовать в полиморфных операциях.
 */
@Entity
@Table(name = "educator")
@Getter
@Setter
@NoArgsConstructor
public class Educator implements IMaterialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ФИО преподавателя.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Коллекция предпочитаемых дней недели для работы.
     * Эта коллекция будет храниться в отдельной таблице 'educator_day_priority'.
     * Загружается лениво, чтобы не тянуть лишние данные.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "educator_day_priority", joinColumns = @JoinColumn(name = "educator_id"))
    @Column(name = "day_of_week", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> preferredDays = new HashSet<>();

    /**
     * Коллекция предпочитаемых временных слотов (пар) для работы.
     * Хранится в отдельной таблице 'educator_slot_priority'.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "educator_slot_priority", joinColumns = @JoinColumn(name = "educator_id"))
    @Column(name = "time_slot", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<TimeSlotPair> preferredTimeSlots = new HashSet<>();

    // Конструктор для удобства
    public Educator(String name) {
        this.name = name;
    }

    // Вспомогательные методы для удобного управления коллекциями
    public void addPreferredDay(DayOfWeek day) {
        this.preferredDays.add(day);
    }

    public void removePreferredDay(DayOfWeek day) {
        this.preferredDays.remove(day);
    }

    public void addPreferredTimeSlot(TimeSlotPair slot) {
        this.preferredTimeSlots.add(slot);
    }

    public void removePreferredTimeSlot(TimeSlotPair slot) {
        this.preferredTimeSlots.remove(slot);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Educator educator = (Educator) o;
        return id != null && Objects.equals(id, educator.id);
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

