package ru.repository.read;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.read.ScheduleView;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository для ScheduleView (CQRS Query Side).
 *
 * <p>Оптимизирован для быстрых чтений:</p>
 * <ul>
 *   <li>Индексированные поля (date, educator_id, group_id, auditorium_id)</li>
 *   <li>Денормализованные данные (избегаем JOIN)</li>
 *   <li>Специализированные запросы для конкретных UI</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/bliki/QueryResponsibilitySeparation.html">CQRS Pattern</a>
 */
@Repository
public interface ScheduleViewRepository extends org.springframework.data.jpa.repository.JpaRepository<ScheduleView, UUID> {

    /**
     * Быстрый запрос для студентов: расписание группы на период.
     *
     * <p>Используется когда студент открывает своё расписание на неделю/семестр.</p>
     *
     * @param streamId ID потока/подгруппы
     * @param start    Начальная дата
     * @param end      Конечная дата
     * @return Список занятий, отсортированный по дате и времени
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.studyStreamId = :streamId " +
            "AND s.scheduledDate BETWEEN :start AND :end " +
            "ORDER BY s.scheduledDate, s.timeSlot")
    List<ScheduleView> findByStudentGroup(
            @Param("streamId") Integer streamId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Поиск всех записей для списка потоков.
     * Используется для получения расписания сразу по нескольким курсам/потокам.
     */
    List<ScheduleView> findByStudyStreamIdIn(List<Integer> studyStreamIds);

    /**
     * Быстрый запрос для преподавателей: расписание на конкретную дату.
     *
     * <p>Используется когда преподаватель открывает расписание на завтра.</p>
     *
     * @param educatorId ID преподавателя
     * @param date       Дата
     * @return Список занятий на эту дату, отсортированный по времени
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.educatorId = :educatorId " +
            "AND s.scheduledDate = :date " +
            "ORDER BY s.timeSlot")
    List<ScheduleView> findByEducatorAndDate(
            @Param("educatorId") Integer educatorId,
            @Param("date") LocalDate date
    );

    /**
     * Расписание преподавателя на период (неделя/месяц).
     *
     * @param educatorId ID преподавателя
     * @param start      Начальная дата
     * @param end        Конечная дата
     * @return Список занятий, отсортированный по дате и времени
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.educatorId = :educatorId " +
            "AND s.scheduledDate BETWEEN :start AND :end " +
            "ORDER BY s.scheduledDate, s.timeSlot")
    List<ScheduleView> findByEducatorAndPeriod(
            @Param("educatorId") Integer educatorId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Расписание в аудитории на дату.
     *
     * <p>Используется для проверки свободности аудитории.</p>
     *
     * @param auditoriumId ID аудитории
     * @param date         Дата
     * @return Список занятий в аудитории, отсортированный по времени
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.auditoriumId = :auditoriumId " +
            "AND s.scheduledDate = :date " +
            "ORDER BY s.timeSlot")
    List<ScheduleView> findByAuditoriumAndDate(
            @Param("auditoriumId") Integer auditoriumId,
            @Param("date") LocalDate date
    );

    /**
     * Агрегация для отчётов: загруженность аудиторий.
     *
     * <p>Возвращает: [auditoriumId, count, date]</p>
     *
     * @return Список кортежей [ID аудитории, количество занятий, дата]
     */
    @Query("SELECT s.auditoriumId, COUNT(s), s.scheduledDate " +
            "FROM ScheduleView s " +
            "WHERE s.auditoriumId IS NOT NULL " +
            "GROUP BY s.auditoriumId, s.scheduledDate " +
            "ORDER BY s.scheduledDate")
    List<Object[]> countByAuditoriumAndDate();

    /**
     * Агрегация для отчётов: загруженность преподавателей.
     *
     * <p>Возвращает: [educatorId, educatorName, count, date]</p>
     *
     * @param start Начальная дата
     * @param end   Конечная дата
     * @return Список кортежей [ID, имя, количество занятий, дата]
     */
    @Query("SELECT s.educatorId, s.educatorName, COUNT(s), s.scheduledDate " +
            "FROM ScheduleView s " +
            "WHERE s.educatorId IS NOT NULL " +
            "AND s.scheduledDate BETWEEN :start AND :end " +
            "GROUP BY s.educatorId, s.educatorName, s.scheduledDate " +
            "ORDER BY s.scheduledDate, COUNT(s) DESC")
    List<Object[]> countByEducatorAndDate(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Найти view по placement_id (для синхронизации с Command Side).
     *
     * @param placementId ID размещения
     * @return ScheduleView или пустой Optional
     */
    Optional<ScheduleView> findByPlacementId(UUID placementId);

    /**
     * ✅ Найти view по placement_id и group_id (для варианта 3 с несколькими группами).
     *
     * @param placementId ID размещения
     * @param groupId ID группы
     * @return ScheduleView или пустой Optional
     */
    @Query("SELECT s FROM ScheduleView s WHERE s.placementId = :placementId AND s.studyStreamId = :groupId")
    Optional<ScheduleView> findByPlacementIdAndGroupId(
            @Param("placementId") UUID placementId,
            @Param("groupId") Integer groupId
    );

    /**
     * Удалить устаревшие view по placement_id.
     *
     * <p>Используется при синхронизации (удаляем старое перед вставкой нового).</p>
     *
     * @param placementId ID размещения
     */
    void deleteByPlacementId(UUID placementId);

    /**
     * Удалить устаревшие view по списку placement_id.
     *
     * <p>Используется при перегенерации расписания (удаляем старые записи для сессии).</p>
     *
     * @param placementIds Список ID размещений
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScheduleView s WHERE s.placementId IN :placementIds")
    void deleteAllByPlacementIdIn(@Param("placementIds") List<UUID> placementIds);

    /**
     * Найти все занятия для конкретной дисциплины.
     *
     * @param disciplineAbbr Аббревиатура дисциплины
     * @return Список занятий
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.disciplineAbbr = :abbr " +
            "ORDER BY s.scheduledDate, s.timeSlot")
    List<ScheduleView> findByDisciplineAbbr(@Param("abbr") String disciplineAbbr);

    /**
     * Найти все занятия определённого типа.
     *
     * @param kindOfStudy Тип занятия (LECTURE, PRACTICE, etc.)
     * @return Список занятий
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.kindOfStudy = :kind " +
            "ORDER BY s.scheduledDate, s.timeSlot")
    List<ScheduleView> findByKindOfStudy(@Param("kind") String kindOfStudy);

    /**
     * Найти занятия по теме (для тематического планирования).
     *
     * @param themeNumber Номер темы
     * @return Список занятий
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.themeNumber = :themeNumber " +
            "ORDER BY s.scheduledDate, s.timeSlot")
    List<ScheduleView> findByThemeNumber(@Param("themeNumber") String themeNumber);

    /**
     * Проверить свободность аудитории на дату/время.
     *
     * @param auditoriumId ID аудитории
     * @param date         Дата
     * @param timeSlot     Временной слот
     * @return true если свободна, false если занята
     */
    @Query("SELECT CASE WHEN COUNT(s) = 0 THEN true ELSE false END " +
            "FROM ScheduleView s " +
            "WHERE s.auditoriumId = :auditoriumId " +
            "AND s.scheduledDate = :date " +
            "AND s.timeSlot = :timeSlot")
    boolean isAuditoriumFree(
            @Param("auditoriumId") Integer auditoriumId,
            @Param("date") LocalDate date,
            @Param("timeSlot") String timeSlot
    );

    /**
     * Удалить все записи для конкретных потоков.
     * Используется при перегенерации расписания.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScheduleView s WHERE s.studyStreamId IN :streamIds")
    void deleteByStudyStreamIdIn(@Param("streamIds") List<Integer> streamIds);

    /**
     * Удалить все записи view в диапазоне дат (Путь 2: скоуп проекции по периоду).
     *
     * <p>При перегенерации расписания периода чистим только его строки (а не весь
     * view), чтобы расписания других семестров не затирались.</p>
     *
     * @param start начало периода
     * @param end   конец периода
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScheduleView s WHERE s.scheduledDate BETWEEN :start AND :end")
    void deleteByPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Получить все занятия за период.
     * Используется для загрузки существующего расписания на фронтенд.
     *
     * @param start Начальная дата
     * @param end   Конечная дата
     * @return Список всех занятий за период, отсортированный по дате и времени
     */
    @Query("SELECT s FROM ScheduleView s " +
            "WHERE s.scheduledDate BETWEEN :start AND :end " +
            "ORDER BY s.scheduledDate, s.timeSlot")
    List<ScheduleView> findByPeriod(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
