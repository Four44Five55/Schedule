package ru.entity.write;

import jakarta.persistence.*;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Сессия редактирования расписания (CQRS Command Side).
 *
 * <p>Поддерживает:</p>
 * <ul>
 *   <li>Оптимистичную блокировку ({@code @Version}) для конкурентного доступа</li>
 *   <li>Жизненный цикл сессии ({@link SessionStatus})</li>
 *   <li>Аудит изменений (кто, когда)</li>
 *   <li>Опциональный snapshot workspace для быстрого восстановления</li>
 * </ul>
 *
 * <p><b>Optimistic Locking:</b></p>
 * <pre>
 * // Загрузка сессии с version=1
 * ScheduleSession session = repository.findById(id).orElseThrow();
 *
 * // Кто-то другой изменил сессию (version стал 2)
 *
 * // Попытка сохранить (версии не совпадают!)
 * repository.save(session); // ❌ OptimisticLockingFailureException
 * </pre>
 *
 * @see <a href="https://martinfowler.com/bliki/QueryResponsibilitySeparation.html">CQRS Pattern</a>
 * @see LessonPlacement
 * @see SessionStatus
 */
@Entity
@Table(name = "schedule_session")
@Getter
@NoArgsConstructor
public class ScheduleSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ========== Основные поля ==========

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    // ========== Аудит (кто, когда создал) ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    // ========== Аудит (кто, когда изменил) ==========

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ========== ✅ OPTIMISTIC LOCKING ==========

    /**
     * Версия для оптимистичной блокировки.
     *
     * <p>Автоматически увеличивается при каждом сохранении (UPDATE).</p>
     * <p>Предотвращает конфликты параллельного редактирования.</p>
     *
     * @see org.hibernate.StaleObjectStateException
     * @see org.springframework.orm.ObjectOptimisticLockingFailureException
     */
    @Version
    private Long version;

    // ========== Связь с placements ==========

    /**
     * Размещения занятий в этой сессии.
     * Cascade ALL = при удалении сессии удаляются все placements.
     * Orphan removal = при удалении placement из коллекции он удаляется из БД.
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LessonPlacement> placements = new HashSet<>();

    // ========== Опциональный snapshot workspace ==========

    /**
     * Сериализованный workspace для быстрого восстановления.
     *
     * <p>Хранит {@link ru.services.solver.ScheduleWorkspace} в JSON.</p>
     * <p>Опционально: если null - workspace пересоздаётся из placements.</p>
     */
    @Lob
    @Column(name = "workspace_snapshot", columnDefinition = "TEXT")
    private String workspaceSnapshot;

    // ========== Конструкторы ==========

    /**
     * Конструктор для создания новой сессии.
     *
     * @param name Название сессии (например: "Расписание 2025 весна")
     * @param user Пользователь, который создал сессию
     */
    public ScheduleSession(String name, String user) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.status = SessionStatus.INITIALIZED;
        this.createdAt = LocalDateTime.now();
        this.createdBy = user;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = user;
        this.version = 0L;
    }

    // ========== Методы для управления статусом ==========

    /**
     * Обновить статус сессии.
     *
     * @param newStatus Новый статус
     * @param user Пользователь, который изменяет
     */
    public void updateStatus(SessionStatus newStatus, String user) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = user;
    }

    /**
     * Проверить, можно ли редактировать расписание в этой сессии.
     *
     * @return true если редактирование разрешено
     */
    public boolean isEditable() {
        return status.isEditable();
    }

    // ========== Методы для управления placements ==========

    /**
     * Добавить размещение в сессию.
     *
     * @param placement Размещение
     */
    public void addPlacement(LessonPlacement placement) {
        // Связь устанавливается через placement.session (setter не нужен для @ManyToOne)
        this.placements.add(placement);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Удалить размещение из сессии.
     *
     * @param placement Размещение
     */
    public void removePlacement(LessonPlacement placement) {
        // Связь разрывается через orphanRemoval
        this.placements.remove(placement);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Очистить все размещения.
     */
    public void clearPlacements() {
        this.placements.clear();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Получить количество размещений в сессии.
     *
     * @return Количество размещений
     */
    public int getPlacementsCount() {
        return placements.size();
    }

    // ========== Методы для workspace snapshot ==========

    /**
     * Сохранить snapshot workspace.
     *
     * @param snapshot JSON строка с workspace
     */
    public void setWorkspaceSnapshot(String snapshot) {
        this.workspaceSnapshot = snapshot;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Проверить, есть ли snapshot workspace.
     *
     * @return true если snapshot есть
     */
    public boolean hasWorkspaceSnapshot() {
        return workspaceSnapshot != null && !workspaceSnapshot.isEmpty();
    }

    // ========== Вспомогательные методы ==========

    /**
     * Проверить, является ли сессия "свежей" (создана недавно).
     *
     * @param daysWithin Порог в днях
     * @return true если создана менее чем daysWithin дней назад
     */
    public boolean isRecentlyCreated(int daysWithin) {
        return createdAt.isAfter(LocalDateTime.now().minusDays(daysWithin));
    }

    /**
     * Проверить, изменялась ли сессия после создания.
     *
     * @return true если была изменена
     */
    public boolean isModified() {
        return updatedAt.isAfter(createdAt);
    }

    /**
     * Получить возраст сессии в днях.
     *
     * @return Количество дней с момента создания
     */
    public long getAgeInDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
    }

    // ========== equals/hashCode/toString ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleSession that = (ScheduleSession) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ScheduleSession{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", version=" + version +
                ", placements=" + placements.size() +
                ", createdBy='" + createdBy + '\'' +
                ", createdAt=" + createdAt +
                ", hasSnapshot=" + hasWorkspaceSnapshot() +
                '}';
    }
}
