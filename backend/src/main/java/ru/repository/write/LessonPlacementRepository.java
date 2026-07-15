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
     * Только id размещений сессии — для сверки с проекцией (грузить сущности незачем).
     *
     * @param sessionId сессия
     * @return id размещений
     */
    @Query("SELECT lp.id FROM LessonPlacement lp WHERE lp.session.id = :sessionId")
    List<UUID> findIdsBySessionId(@Param("sessionId") UUID sessionId);

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
     * Найти размещения по набору id, сразу подтянув всех преподавателей занятия.
     *
     * <p>Нужно аналитике качества: {@code schedule_view} денормализует лишь одного
     * преподавателя на строку, а занятие могут вести несколько. Через {@code JOIN FETCH}
     * восстанавливаем полный состав, чтобы учитывать со-преподавателей.</p>
     *
     * @param ids id размещений
     * @return размещения с инициализированной коллекцией {@code assignment.educators}
     */
    @Query("SELECT DISTINCT lp FROM LessonPlacement lp " +
            "JOIN FETCH lp.assignment a LEFT JOIN FETCH a.educators " +
            "WHERE lp.id IN :ids")
    List<LessonPlacement> findByIdInWithEducators(@Param("ids") java.util.Collection<UUID> ids);

    /**
     * Id всех размещений курса (через слот → назначение). Нужны при удалении курса,
     * чтобы синхронно вычистить строки read-модели {@code schedule_view} (у неё нет FK
     * на {@code lesson_placement}), захватив их ДО FK-каскадного удаления write-стороны.
     *
     * @param courseId id курса ({@link ru.entity.logicSchema.DisciplineCourse})
     * @return id размещений курса
     */
    @Query("SELECT lp.id FROM LessonPlacement lp " +
            "WHERE lp.assignment.curriculumSlot.disciplineCourse.id = :courseId")
    List<UUID> findIdsByCourseId(@Param("courseId") Integer courseId);

    /**
     * Число размещённых занятий курса — для предпросмотра последствий удаления.
     *
     * @param courseId id курса
     * @return количество {@link LessonPlacement} курса
     */
    @Query("SELECT COUNT(lp) FROM LessonPlacement lp " +
            "WHERE lp.assignment.curriculumSlot.disciplineCourse.id = :courseId")
    long countByCourseId(@Param("courseId") Integer courseId);

    /**
     * Id размещений набора назначений. Нужны при удалении назначений, чтобы синхронно
     * вычистить строки read-модели {@code schedule_view} (у неё нет FK на placement),
     * захватив их ДО FK-каскадного удаления назначений.
     *
     * @param assignmentIds id назначений
     * @return id размещений этих назначений
     */
    @Query("SELECT lp.id FROM LessonPlacement lp WHERE lp.assignment.id IN :assignmentIds")
    List<UUID> findIdsByAssignmentIdIn(@Param("assignmentIds") java.util.Collection<Integer> assignmentIds);

    /**
     * Сколько размещений набора назначений ЗАКРЕПЛЕНО (замок). Нужно предупредить перед
     * удалением назначений: их размещения уносит FK-каскад БД, который про {@code locked}
     * ничего не знает, — то есть ручная раскладка теряется бесшумно.
     *
     * @param assignmentIds id назначений
     * @return число закреплённых размещений этих назначений
     */
    @Query("SELECT COUNT(lp) FROM LessonPlacement lp " +
            "WHERE lp.assignment.id IN :assignmentIds AND lp.locked = true")
    long countLockedByAssignmentIdIn(@Param("assignmentIds") java.util.Collection<Integer> assignmentIds);

    /**
     * Размещения набора назначений. Нужны при ПРАВКЕ назначения (смена состава преподавателей
     * или потока): {@code schedule_view} денормализует преподавателя/группу снимком, поэтому
     * уже стоящие занятия надо перепроецировать — иначе сетка и отчёты показывают прежнего
     * преподавателя.
     *
     * @param assignmentIds id назначений
     * @return размещения этих назначений
     */
    @Query("SELECT lp FROM LessonPlacement lp WHERE lp.assignment.id IN :assignmentIds")
    List<LessonPlacement> findByAssignmentIdIn(@Param("assignmentIds") java.util.Collection<Integer> assignmentIds);

    // ========== Охваты устаревания снимка (ProjectionSource) ==========
    // schedule_view хранит имена преподавателя/группы/аудитории, тему и вид занятия СНИМКОМ.
    // При правке master-данных надо найти размещения, чей снимок протух, и перепроецировать их.
    // Запросы живут здесь, а стратегия выбора — в ru.services.projection.ProjectionSource.

    /** Размещения, которые ведут этих преподавателей (переименование → снимок протух). */
    @Query("SELECT DISTINCT lp.id FROM LessonPlacement lp " +
            "JOIN lp.assignment a JOIN a.educators e WHERE e.id IN :educatorIds")
    List<UUID> findIdsByEducatorIdIn(@Param("educatorIds") java.util.Collection<Integer> educatorIds);

    /** Размещения, в потоке которых есть эти группы (переименование/удаление группы). */
    @Query("SELECT DISTINCT lp.id FROM LessonPlacement lp " +
            "JOIN lp.assignment a JOIN a.studyStream s JOIN s.groups g WHERE g.id IN :groupIds")
    List<UUID> findIdsByGroupIdIn(@Param("groupIds") java.util.Collection<Integer> groupIds);

    /**
     * Размещения назначений этих потоков. Охват для смены СОСТАВА потока: если группу из него
     * убрали, по группе размещения уже не найти (связи нет) — поток находит их все.
     */
    @Query("SELECT lp.id FROM LessonPlacement lp WHERE lp.assignment.studyStream.id IN :streamIds")
    List<UUID> findIdsByStreamIdIn(@Param("streamIds") java.util.Collection<Integer> streamIds);

    /** Размещения в этих аудиториях (переименование/удаление комнаты). */
    @Query("SELECT DISTINCT lp.id FROM LessonPlacement lp " +
            "JOIN lp.assignedAuditoriums aud WHERE aud.id IN :auditoriumIds")
    List<UUID> findIdsByAuditoriumIdIn(@Param("auditoriumIds") java.util.Collection<Integer> auditoriumIds);

    /** Размещения этих дисциплин (сменилось название/аббревиатура). */
    @Query("SELECT lp.id FROM LessonPlacement lp " +
            "WHERE lp.assignment.curriculumSlot.disciplineCourse.discipline.id IN :disciplineIds")
    List<UUID> findIdsByDisciplineIdIn(@Param("disciplineIds") java.util.Collection<Integer> disciplineIds);

    /** Размещения занятий по этим темам (сменился номер/название темы). */
    @Query("SELECT lp.id FROM LessonPlacement lp " +
            "WHERE lp.assignment.curriculumSlot.themeLesson.id IN :themeIds")
    List<UUID> findIdsByThemeIdIn(@Param("themeIds") java.util.Collection<Integer> themeIds);

    /** Размещения этих слотов плана (сменился вид занятия или привязка темы). */
    @Query("SELECT lp.id FROM LessonPlacement lp WHERE lp.assignment.curriculumSlot.id IN :slotIds")
    List<UUID> findIdsBySlotIdIn(@Param("slotIds") java.util.Collection<Integer> slotIds);

    // ========== Предпросмотр последствий удаления ==========
    // Каскады БД уносят размещения молча и про `locked` ничего не знают, поэтому цену удаления
    // (особенно потерю ручной раскладки) надо назвать ДО подтверждения. Прецедент —
    // countLockedByAssignmentIdIn выше.

    /** Занятий стоит в этой аудитории (при её удалении останутся без комнаты). */
    @Query("SELECT COUNT(DISTINCT lp) FROM LessonPlacement lp " +
            "JOIN lp.assignedAuditoriums aud WHERE aud.id = :auditoriumId")
    long countByAuditoriumId(@Param("auditoriumId") Integer auditoriumId);

    /** Из них закреплено вручную. */
    @Query("SELECT COUNT(DISTINCT lp) FROM LessonPlacement lp " +
            "JOIN lp.assignedAuditoriums aud WHERE aud.id = :auditoriumId AND lp.locked = true")
    long countLockedByAuditoriumId(@Param("auditoriumId") Integer auditoriumId);

    /** Занятий стоит в аудиториях этого набора (удаление корпуса уносит их каскадом). */
    @Query("SELECT COUNT(DISTINCT lp) FROM LessonPlacement lp " +
            "JOIN lp.assignedAuditoriums aud WHERE aud.id IN :auditoriumIds")
    long countByAuditoriumIdIn(@Param("auditoriumIds") java.util.Collection<Integer> auditoriumIds);

    /** Из них закреплено вручную. */
    @Query("SELECT COUNT(DISTINCT lp) FROM LessonPlacement lp " +
            "JOIN lp.assignedAuditoriums aud WHERE aud.id IN :auditoriumIds AND lp.locked = true")
    long countLockedByAuditoriumIdIn(@Param("auditoriumIds") java.util.Collection<Integer> auditoriumIds);

    /** Размещений у слота плана (уйдут каскадом при его удалении). */
    @Query("SELECT COUNT(lp) FROM LessonPlacement lp WHERE lp.assignment.curriculumSlot.id = :slotId")
    long countBySlotId(@Param("slotId") Integer slotId);

    /** Из них закреплено вручную. */
    @Query("SELECT COUNT(lp) FROM LessonPlacement lp " +
            "WHERE lp.assignment.curriculumSlot.id = :slotId AND lp.locked = true")
    long countLockedBySlotId(@Param("slotId") Integer slotId);

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
