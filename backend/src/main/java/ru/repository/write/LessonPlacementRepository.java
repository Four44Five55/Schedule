package ru.repository.write;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.write.LessonPlacement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository для LessonPlacement (CQRS Command Side).
 *
 * <p>Предоставляет:</p>
 * <ul>
 *   <li>CRUD операции для размещений</li>
 *   <li>Запросы в рамках сессии</li>
 *   <li>Поиск по assignment</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/bliki/QueryResponsibilitySeparation.html">CQRS Pattern</a>
 * @see ru.entity.write.LessonPlacement
 */
@Repository
public interface LessonPlacementRepository extends org.springframework.data.jpa.repository.JpaRepository<LessonPlacement, UUID> {

    /**
     * Найти все размещения в сессии.
     *
     * @param sessionId ID сессии
     * @return Список размещений
     */
    List<LessonPlacement> findBySessionId(UUID sessionId);

    /**
     * Найти placement по ID в рамках сессии.
     *
     * @param placementId ID размещения
     * @param sessionId ID сессии
     * @return Placement или пустой Optional
     */
    Optional<LessonPlacement> findByIdAndSessionId(UUID placementId, UUID sessionId);

    /**
     * Найти все размещения для assignment.
     *
     * @param assignmentId ID заявки
     * @return Список размещений (может быть несколько при поэтапной генерации)
     */
    @Query("SELECT lp FROM LessonPlacement lp WHERE lp.assignment.id = :assignmentId")
    List<LessonPlacement> findByAssignmentId(@Param("assignmentId") Integer assignmentId);

    /**
     * Найти размещения по дате в рамках сессии.
     *
     * @param sessionId ID сессии
     * @param date Дата
     * @return Список размещений
     */
    @Query("SELECT lp FROM LessonPlacement lp WHERE lp.session.id = :sessionId AND lp.scheduledDate = :date")
    List<LessonPlacement> findBySessionIdAndDate(
            @Param("sessionId") UUID sessionId,
            @Param("date") LocalDate date
    );

    /**
     * Найти размещения на период в рамках сессии.
     *
     * @param sessionId ID сессии
     * @param start Начальная дата
     * @param end Конечная дата
     * @return Список размещений
     */
    @Query("SELECT lp FROM LessonPlacement lp " +
           "WHERE lp.session.id = :sessionId " +
           "AND lp.scheduledDate BETWEEN :start AND :end " +
           "ORDER BY lp.scheduledDate, lp.scheduledSlot")
    List<LessonPlacement> findBySessionIdAndPeriod(
            @Param("sessionId") UUID sessionId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Найти размещения, созданные конкретным пользователем.
     *
     * @param createdBy Имя пользователя
     * @return Список размещений
     */
    List<LessonPlacement> findByCreatedBy(String createdBy);

    /**
     * Найти размещения, изменённые конкретным пользователем.
     *
     * @param updatedBy Имя пользователя
     * @return Список размещений
     */
    List<LessonPlacement> findByUpdatedBy(String updatedBy);

    /**
     * Подсчитать количество размещений в сессии.
     *
     * @param sessionId ID сессии
     * @return Количество размещений
     */
    @Query("SELECT COUNT(lp) FROM LessonPlacement lp WHERE lp.session.id = :sessionId")
    long countBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * Удалить все размещения в сессии.
     *
     * @param sessionId ID сессии
     */
    @Query("DELETE FROM LessonPlacement lp WHERE lp.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * Найти размещения без аудиторий.
     *
     * <p>Используется для валидации - все размещения должны иметь аудитории.</p>
     *
     * @return Список размещений без аудиторий
     */
    @Query("SELECT lp FROM LessonPlacement lp " +
           "WHERE SIZE(lp.assignedAuditoriums) = 0 " +
           "ORDER BY lp.scheduledDate")
    List<LessonPlacement> findWithoutAuditoriums();

    /**
     * Найти размещения с конфликтами (одновременно в одной аудитории).
     *
     * <p>Используется для валидации.</p>
     *
     * @return Список конфликтующих размещений
     */
    @Query("SELECT lp1 FROM LessonPlacement lp1, LessonPlacement lp2 " +
           "WHERE lp1.id < lp2.id " +
           "AND lp1.scheduledDate = lp2.scheduledDate " +
           "AND lp1.scheduledSlot = lp2.scheduledSlot " +
           "AND EXISTS (" +
           "  SELECT a FROM lp1.assignedAuditoriums a " +
           "  WHERE a MEMBER OF lp2.assignedAuditoriums" +
           ")")
    List<LessonPlacement> findConflictingPlacements();
}
