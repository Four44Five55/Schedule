package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.CurriculumSlot;

import java.util.List;

@Repository
public interface CurriculumSlotRepository extends JpaRepository<CurriculumSlot, Integer> {

    /**
     * Находит все слоты для указанного курса и сортирует их по полю 'position'.
     * Это основной метод для получения упорядоченного учебного плана.
     */
    List<CurriculumSlot> findByDisciplineCourseIdOrderByPosition(Integer courseId);

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
}
