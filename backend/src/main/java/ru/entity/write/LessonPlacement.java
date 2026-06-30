package ru.entity.write;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.entity.Auditorium;
import ru.entity.Assignment;
import ru.enums.PlacementSource;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Write-optimized entity для размещения занятий (CQRS Command Side).
 *
 * <p>Хранит персистентное состояние размещения занятия.</p>
 * <ul>
 *   <li>Ссылка на {@link Assignment} (что учим)</li>
 *   <li>Дата и время размещения (когда учим)</li>
 *   <li>Аудитории (где учим)</li>
 *   <li>Аудит (кто и когда изменил)</li>
 * </ul>
 *
 * <p><b>Роль в CQRS:</b></p>
 * <ul>
 *   <li><b>Command Side:</b> Источник правды для размещения</li>
 *   <li>Синхронизируется с {@link ru.entity.read.ScheduleView} (Query Side)</li>
 *   <li>Поддерживает транзакционную целостность</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/bliki/QueryResponsibilitySeparation.html">CQRS Pattern</a>
 * @see ScheduleSession
 * @see Assignment
 */
@Entity
@Table(name = "lesson_placement")
@Getter
@NoArgsConstructor
public class LessonPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ========== Ссылка на заявку (Assignment) ==========

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    // ========== Размещение (дата и время) ==========

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheduled_slot", nullable = false)
    private TimeSlotPair scheduledSlot;

    // ========== Аудитории ==========

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "placement_auditoriums",
        joinColumns = @JoinColumn(name = "placement_id"),
        inverseJoinColumns = @JoinColumn(name = "auditorium_id")
    )
    private Set<Auditorium> assignedAuditoriums = new HashSet<>();

    // ========== Пин (Фича 2: ручное размещение) ==========

    /**
     * Пин: {@code true} — размещение закреплено, распределитель его не двигает
     * и не удаляет при (ре)генерации (Фаза 0 засевает такие как «уже размещённые»).
     */
    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    /**
     * Происхождение размещения: алгоритм или диспетчер.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private PlacementSource source = PlacementSource.GENERATED;

    // ========== Аудит (кто, когда, что изменил) ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ========== Связь с сессией ==========

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ScheduleSession session;

    // ========== Конструкторы ==========

    /**
     * Конструктор для создания нового размещения.
     *
     * @param assignment Заявка на занятие
     * @param scheduledDate Дата занятия
     * @param scheduledSlot Временной слот
     * @param session Сессия редактирования
     * @param user Пользователь, который создаёт размещение
     */
    public LessonPlacement(
            Assignment assignment,
            LocalDate scheduledDate,
            TimeSlotPair scheduledSlot,
            ScheduleSession session,
            String user
    ) {
        this.id = UUID.randomUUID();
        this.assignment = assignment;
        this.scheduledDate = scheduledDate;
        this.scheduledSlot = scheduledSlot;
        this.session = session;
        this.createdAt = LocalDateTime.now();
        this.createdBy = user;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = user;
    }

    /**
     * Конструктор с явным происхождением и признаком закрепления.
     *
     * <p>Для ручного размещения (Фича 2): {@code source=MANUAL, locked=true}.
     * Базовый конструктор оставляет дефолт {@code GENERATED, locked=false}.</p>
     */
    public LessonPlacement(
            Assignment assignment,
            LocalDate scheduledDate,
            TimeSlotPair scheduledSlot,
            ScheduleSession session,
            String user,
            PlacementSource source,
            boolean locked
    ) {
        this(assignment, scheduledDate, scheduledSlot, session, user);
        this.source = source;
        this.locked = locked;
    }

    // ========== Методы для обновления ==========

    /**
     * Закрепить/снять закрепление размещения (пин).
     *
     * @param locked новое значение признака закрепления
     * @param user   автор изменения (для аудита)
     */
    public void setLock(boolean locked, String user) {
        this.locked = locked;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = user;
    }

    /**
     * Обновить размещение (например, при переносе занятия).
     *
     * @param newDate Новая дата
     * @param newSlot Новый временной слот
     * @param newAuditoriums Новые аудитории
     * @param user Пользователь, который изменяет
     */
    public void updatePlacement(
            LocalDate newDate,
            TimeSlotPair newSlot,
            Set<Auditorium> newAuditoriums,
            String user
    ) {
        this.scheduledDate = newDate;
        this.scheduledSlot = newSlot;
        this.setAuditoriums(newAuditoriums);
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = user;
    }

    /**
     * Обновить только аудитории.
     *
     * @param newAuditoriums Новые аудитории
     * @param user Пользователь, который изменяет
     */
    public void updateAuditoriums(Set<Auditorium> newAuditoriums, String user) {
        this.setAuditoriums(newAuditoriums);
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = user;
    }

    /**
     * Установить аудитории (вспомогательный метод).
     */
    private void setAuditoriums(Set<Auditorium> auditoriums) {
        this.assignedAuditoriums.clear();
        if (auditoriums != null) {
            this.assignedAuditoriums.addAll(auditoriums);
        }
    }

    // ========== Вспомогательные методы ==========

    /**
     * Проверить, является ли размещение "свежим" (создано недавно).
     *
     * @param minutesWithin Порог в минутах
     * @return true если создано менее чем minutesWithin минут назад
     */
    public boolean isRecentlyCreated(int minutesWithin) {
        return createdAt.isAfter(LocalDateTime.now().minusMinutes(minutesWithin));
    }

    /**
     * Проверить, изменялось ли размещение после создания.
     *
     * @return true если было изменено
     */
    public boolean isModified() {
        return updatedAt.isAfter(createdAt);
    }

    // ========== equals/hashCode/toString ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LessonPlacement that = (LessonPlacement) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "LessonPlacement{" +
                "id=" + id +
                ", assignment=" + (assignment != null ? assignment.getId() : "null") +
                ", date=" + scheduledDate +
                ", slot=" + scheduledSlot +
                ", auditoriums=" + (assignedAuditoriums != null ? assignedAuditoriums.size() : 0) +
                ", session=" + (session != null ? session.getId() : "null") +
                ", createdBy='" + createdBy + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
