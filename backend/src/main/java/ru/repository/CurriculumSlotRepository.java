package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.CurriculumSlot;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumSlotRepository extends JpaRepository<CurriculumSlot, Integer> {

    /**
     * Находит все слоты для указанного курса и сортирует их по полю 'position'.
     * Это основной метод для получения упорядоченного учебного плана.
     */
    List<CurriculumSlot> findByDisciplineCourseIdOrderByPosition(Integer courseId);

    /** Сколько слотов держит план периода — часть цены отката, названной заранее. */
    long countByDisciplineCourse_StudyPeriod_Id(Integer periodId);

    /**
     * Увеличивает на 1 значение 'position' для всех слотов в указанном курсе,
     * чья позиция больше или равна указанной.
     * Используется для "раздвигания" списка при вставке нового слота.
     *
     * @param courseId      ID курса.
     * @param startPosition Позиция, начиная с которой нужно сдвинуть слоты.
     */
    @Modifying
    @Query("UPDATE CurriculumSlot cs SET cs.position = cs.position + 1 WHERE cs.disciplineCourse.id = :courseId AND cs.position >= :startPosition")
    void incrementPositionsFrom(@Param("courseId") Integer courseId, @Param("startPosition") Integer startPosition);

    /**
     * Уменьшает на 1 значение 'position' для всех слотов в указанном курсе,
     * чья позиция больше указанной.
     * Используется для "сдвигания" списка при удалении слота.
     *
     * @param courseId      ID курса.
     * @param startPosition Позиция, после которой нужно сдвинуть слоты.
     */
    @Modifying
    @Query("UPDATE CurriculumSlot cs SET cs.position = cs.position - 1 WHERE cs.disciplineCourse.id = :courseId AND cs.position > :startPosition")
    void decrementPositionsAfter(@Param("courseId") Integer courseId, @Param("startPosition") Integer startPosition);


    /**
     * Сколько слотов плана ссылается на аудиторию как на требуемую или приоритетную.
     *
     * <p>Эти ссылки идут <b>без каскада</b> ({@code required/priority_auditorium_id}), поэтому БД
     * не даст удалить такую аудиторию — раньше это вылетало сырым 500. Считаем заранее, чтобы
     * отказать осмысленно (см. {@code AuditoriumService.deleteImpact}).</p>
     */
    @Query("SELECT COUNT(cs) FROM CurriculumSlot cs " +
            "WHERE cs.requiredAuditorium.id = :auditoriumId OR cs.priorityAuditorium.id = :auditoriumId")
    long countReferencingAuditorium(@Param("auditoriumId") Integer auditoriumId);

    /**
     * Сколько слотов плана ссылается (требуемой/приоритетной) на любую аудиторию из набора.
     * Нужно для удаления КОРПУСА: его аудитории уходят каскадом, но эти FK — без каскада, и БД
     * откажет, если хоть одна из них указана в плане. Считаем заранее, чтобы вернуть осмысленный 409.
     */
    @Query("SELECT COUNT(cs) FROM CurriculumSlot cs " +
            "WHERE cs.requiredAuditorium.id IN :auditoriumIds OR cs.priorityAuditorium.id IN :auditoriumIds")
    long countReferencingAuditoriumIn(@Param("auditoriumIds") java.util.Collection<Integer> auditoriumIds);

    @Query("SELECT cs FROM CurriculumSlot cs " +
            "WHERE cs.disciplineCourse.id = :courseId " +
            "  AND cs.kindOfStudy = ru.enums.KindOfStudy.LECTURE " +
            "  AND cs.position < :currentPosition " +
            "ORDER BY cs.position DESC " +
            "LIMIT 1")
    Optional<CurriculumSlot> findPreviousLectureInCourse(
            @Param("courseId") Integer courseId,
            @Param("currentPosition") Integer currentPosition);
}
