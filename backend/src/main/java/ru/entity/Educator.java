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
    private Integer id;

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

    /**
     * Флаг компактности расписания.
     * Если true - занятия для разных групп стараются размещать в минимальное количество дней.
     * Если false - используется стандартная логика распределения.
     */
    @Column(name = "compact_schedule", nullable = false)
    private boolean compactSchedule = false;

    /**
     * Подразделение преподавателя — кафедра или отдел. Ровно одно: совместительство не
     * моделируем (решение заказчика), поэтому обычная ссылка, а не таблица членства.
     * {@code null} — ещё не распределён.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    /**
     * Специальное (воинское) звание — приставка перед фамилией в подписи. {@code null} — не указано.
     *
     * <p>Регалии ниже в планировании <b>не участвуют</b>: у звания нет занятости, солвер о них не
     * знает — то же решение, что по подразделению. Их место — карточка преподавателя, таблица
     * «Обозначения» выгрузки и разбор чужих файлов на импорте.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "special_rank_id")
    private ru.entity.dictionary.SpecialRank specialRank;

    /**
     * Род службы к званию («юстиции»): независимая ось, а не разновидность звания.
     * Почти всегда {@code null} — печатается только вместе со званием.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rank_service_id")
    private ru.entity.dictionary.RankService rankService;

    /**
     * Уровень учёной степени. Вторая её половина — {@link #scienceBranch}: вместе они дают
     * «ктн». Готовая строка сокращения не хранится намеренно — это было бы третье
     * представление одного факта.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "academic_degree", length = 50)
    private ru.enums.AcademicDegree academicDegree;

    /** Отрасль науки учёной степени: «технические» → «ктн». */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "science_branch_id")
    private ru.entity.dictionary.ScienceBranch scienceBranch;

    /**
     * Учёное звание (доцент/профессор). ⚠️ Не должность: «доцент кафедры» — другое поле, которого
     * в модели пока нет.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "academic_title", length = 50)
    private ru.enums.AcademicTitle academicTitle;

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

    public boolean isSaturdayLover() {
        return this.getPreferredDays().contains(ru.enums.DayOfWeek.SATURDAY);
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

