package ru.repository.write;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.write.ScheduleSession;
import ru.enums.SessionStatus;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository для ScheduleSession (CQRS Command Side).
 *
 * <p>Предоставляет:</p>
 * <ul>
 *   <li>CRUD операции для сессий</li>
 *   <li>Optimistic locking (через {@link Lock})</li>
 *   <li>Запросы по статусу и пользователю</li>
 * </ul>
 *
 * <p><b>Optimistic Locking:</b></p>
 * <pre>
 * // Найти сессию с блокировкой
 * Optional&lt;ScheduleSession&gt; session = repository.findByIdWithLock(id);
 *
 * // Изменить и сохранить (версия автоматически проверится)
 * session.setStatus(SessionStatus.FINAL);
 * repository.save(session); // ✅ или ❌ OptimisticLockingFailureException
 * </pre>
 *
 * @see <a href="https://martinfowler.com/bliki/QueryResponsibilitySeparation.html">CQRS Pattern</a>
 * @see ru.entity.write.ScheduleSession
 */
@Repository
public interface ScheduleSessionRepository extends org.springframework.data.jpa.repository.JpaRepository<ScheduleSession, UUID> {

    /**
     * Найти сессию с optimistic lock.
     *
     * <p>Используется при редактировании сессии для предотвращения конфликтов.</p>
     *
     * <p>Внимание: Если version изменился между загрузкой и сохранением,
     * будет выброшено {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</p>
     *
     * @param id ID сессии
     * @return Сессия с блокировкой или пустой Optional
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM ScheduleSession s WHERE s.id = :id")
    Optional<ScheduleSession> findByIdWithLock(@Param("id") UUID id);

    /**
     * Найти сессию с pessimistic write lock.
     *
     * <p>Используется для длительных операций (генерация, оптимизация).</p>
     *
     * <p>Внимание: Блокирует таблицу на время транзакции!</p>
     *
     * @param id ID сессии
     * @return Сессия с блокировкой или пустой Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScheduleSession s WHERE s.id = :id")
    Optional<ScheduleSession> findByIdForEdit(@Param("id") UUID id);

    /**
     * Найти все сессии с определённым статусом.
     *
     * @param status Статус
     * @return Список сессий
     */
    List<ScheduleSession> findByStatus(SessionStatus status);

    /**
     * Найти активные сессии (неархивированные).
     *
     * @return Список активных сессий
     */
    @Query("SELECT s FROM ScheduleSession s WHERE s.status != :archivedStatus ORDER BY s.updatedAt DESC")
    List<ScheduleSession> findActiveSessions(@Param("archivedStatus") SessionStatus archivedStatus);

    /**
     * Найти сессии созданные пользователем.
     *
     * @param user Имя пользователя
     * @return Список сессий, отсортированный по дате обновления
     */
    @Query("SELECT s FROM ScheduleSession s WHERE s.createdBy = :user ORDER BY s.updatedAt DESC")
    List<ScheduleSession> findByCreatedByOrderByUpdatedAtDesc(@Param("user") String user);

    /**
     * Найти последнюю сессию пользователя.
     *
     * @param user Имя пользователя
     * @return Последняя сессия или пустой Optional
     */
    @Query("SELECT s FROM ScheduleSession s WHERE s.createdBy = :user ORDER BY s.updatedAt DESC")
    Optional<ScheduleSession> findTopByCreatedByOrderByUpdatedAtDesc(@Param("user") String user);

    /**
     * Найти сессии, созданные за период.
     *
     * @param daysWithin Порог в днях
     * @return Список "свежих" сессий
     */
    @Query("SELECT s FROM ScheduleSession s WHERE s.createdAt >= :threshold")
    List<ScheduleSession> findRecentlyCreated(@Param("threshold") LocalDateTime threshold);

    /**
     * Найти сессии, которые давно не обновлялись.
     *
     * <p>Используется для очистки старых сессий.</p>
     *
     * @param daysWithin Порог в днях
     * @return Список устаревших сессий
     */
    @Query("SELECT s FROM ScheduleSession s WHERE s.updatedAt < :threshold")
    List<ScheduleSession> findNotRecentlyUpdated(@Param("threshold") LocalDateTime threshold);

    /**
     * Подсчитать количество сессий по статусам.
     *
     * @return Список кортежей [статус, количество]
     */
    @Query("SELECT s.status, COUNT(s) FROM ScheduleSession s GROUP BY s.status")
    List<Object[]> countByStatus();

    /**
     * Подсчитать количество сессий пользователя.
     *
     * @param user Имя пользователя
     * @return Количество сессий
     */
    @Query("SELECT COUNT(s) FROM ScheduleSession s WHERE s.createdBy = :user")
    long countByCreatedBy(@Param("user") String user);

    /**
     * Найти сессии по имени (поиск).
     *
     * @param namePart Часть названия
     * @return Список сессий
     */
    @Query("SELECT s FROM ScheduleSession s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :namePart, '%'))")
    List<ScheduleSession> findByNameContaining(@Param("namePart") String namePart);

    /**
     * Удалить старые архивные сессии.
     *
     * <p>Используется для периодической очистки.</p>
     *
     * @param daysThreshold Порог в днях (старше N дней)
     * @return Количество удалённых сессий
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScheduleSession s WHERE s.status = :archivedStatus AND s.updatedAt < :threshold")
    int deleteOldArchivedSessions(
            @Param("archivedStatus") SessionStatus archivedStatus,
            @Param("threshold") LocalDateTime threshold
    );
}
