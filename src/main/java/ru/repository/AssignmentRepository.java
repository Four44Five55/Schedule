package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.Assignment;

import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {

    /**
     * Находит все назначения для указанного курса и "жадно" (EAGER)
     * подгружает все необходимые связанные сущности за один или несколько эффективных запросов.
     * <p>
     * Это основной метод для получения данных при генерации расписания,
     * он решает проблему "N+1 запросов".
     * </p>
     *
     * @param courseId ID курса (DisciplineCourse).
     * @return Список полностью загруженных назначений.
     */
    @EntityGraph(attributePaths = {
            "curriculumSlot",           // Подгружаем связанный слот
            "studyStream",              // Подгружаем связанный поток
            "studyStream.groups",       // Подгружаем группы внутри потока
            "educators"                 // Подгружаем преподавателей, назначенных на это занятие
    })
    @Query("SELECT a FROM Assignment a WHERE a.curriculumSlot.disciplineCourse.id = :courseId")
    List<Assignment> findAllByCourseIdWithDetails(@Param("courseId") Integer courseId);

    /**
     * Находит все назначения, связанные с одним конкретным слотом учебного плана.
     * Может быть полезно при удалении слота для проверки.
     *
     * @param slotId ID слота (CurriculumSlot).
     * @return Список назначений.
     */
    List<Assignment> findByCurriculumSlotId(Integer slotId);
}
