package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.Assignment;

import java.util.Collection;
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
            "curriculumSlot.themeLesson",// Подгружаем тему из слота
            "curriculumSlot.disciplineCourse",
            "curriculumSlot.disciplineCourse.discipline",
            "curriculumSlot.kindOfStudy",   // Подгрузить и тип занятия
            "studyStream",              // Подгружаем связанный поток
            "studyStream.groups",       // Подгружаем группы внутри потока
            "educators"                 // Подгружаем преподавателей, назначенных на это занятие
    })
    @Query("SELECT a FROM Assignment a WHERE a.curriculumSlot.disciplineCourse.id = :courseId")
    List<Assignment> findAllByCourseIdWithDetails(@Param("courseId") Integer courseId);

    /**
     * То же, но сразу по НАБОРУ курсов — один запрос вместо запроса на каждый курс.
     *
     * <p>Потребители (доска раскладки, палитра неразмещённых, счётчики по дисциплинам) работают
     * с выбранным набором курсов и вызывали {@link #findAllByCourseIdWithDetails} в цикле: на 23
     * курсах это 23 запроса по 2–13 мс, то есть ~60–80 мс на каждое действие в планировщике.
     * Тот же {@code @EntityGraph}, тот же результат — но одним походом в БД. Группировку по курсу
     * делает вызывающий (в памяти, по {@code a.curriculumSlot.disciplineCourse.id}).</p>
     *
     * @param courseIds курсы ({@link ru.entity.logicSchema.DisciplineCourse})
     * @return полностью загруженные назначения всех этих курсов
     */
    @EntityGraph(attributePaths = {
            "curriculumSlot",
            "curriculumSlot.themeLesson",
            "curriculumSlot.disciplineCourse",
            "curriculumSlot.disciplineCourse.discipline",
            "curriculumSlot.kindOfStudy",
            "studyStream",
            "studyStream.groups",
            "educators"
    })
    @Query("SELECT a FROM Assignment a WHERE a.curriculumSlot.disciplineCourse.id IN :courseIds")
    List<Assignment> findAllByCourseIdsWithDetails(@Param("courseIds") Collection<Integer> courseIds);

    /**
     * Находит все назначения, связанные с одним конкретным слотом учебного плана.
     * Может быть полезно при удалении слота для проверки.
     *
     * @param slotId ID слота (CurriculumSlot).
     * @return Список назначений.
     */
    List<Assignment> findByCurriculumSlotId(Integer slotId);

    /**
     * Число назначений курса — для предпросмотра последствий удаления.
     *
     * @param courseId id курса (DisciplineCourse).
     * @return количество {@link Assignment} курса.
     */
    long countByCurriculumSlot_DisciplineCourse_Id(Integer courseId);

    /**
     * Сколько назначений у слота плана — для предпросмотра последствий его удаления
     * (они уйдут каскадом вместе с размещениями).
     */
    long countByCurriculumSlotId(Integer slotId);
}
