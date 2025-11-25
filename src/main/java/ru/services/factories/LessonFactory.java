package ru.services.factories;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.entity.Assignment;
import ru.entity.Lesson;
import ru.services.AssignmentService; // Наш новый, уже существующий сервис

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Фабрика, отвечающая за создание объектов Lesson на основе "Назначений".
 * Преобразует персистентную модель учебного плана (Assignment)
 * в объекты Lesson, готовые для использования в алгоритме-решателе.
 */
@Component
@RequiredArgsConstructor
public class LessonFactory {

    private final AssignmentService assignmentService;

    /**
     * Создает полный список занятий для указанного учебного курса.
     *
     * @param courseId ID курса (DisciplineCourse), для которого нужно создать занятия.
     * @return Список объектов Lesson, готовых к планированию.
     */
    public List<Lesson> createLessonsForCourse(Integer courseId) {
        // 1. Загружаем все назначения для курса с полной информацией
        List<Assignment> assignments = assignmentService.findAllEntitiesByCourseId(courseId); // Используем метод сервиса

        // 2. Группируем назначения по curriculumSlotId, чтобы найти параллельные занятия
        var assignmentsBySlot = assignments.stream()
                .collect(Collectors.groupingBy(a -> a.getCurriculumSlot().getId()));

        List<Lesson> allLessons = new ArrayList<>();

        // 3. Итерируемся по сгруппированным назначениям
        for (var entry : assignmentsBySlot.entrySet()) {
            List<Assignment> parallelAssignments = entry.getValue();

            // Если занятие не "параллельное", а обычное, ID все равно создается. Это не страшно.
            String parallelGroupId = UUID.randomUUID().toString();

            for (Assignment assignment : parallelAssignments) {
                // 4. Для каждого назначения создаем один объект Lesson
                Lesson lesson = new Lesson();

                // 5. Копируем всю информацию
                lesson.setDisciplineCourse(assignment.getCurriculumSlot().getDisciplineCourse());
                lesson.setCurriculumSlot(assignment.getCurriculumSlot());
                lesson.setStudyStream(assignment.getStudyStream());
                lesson.setEducators(assignment.getEducators());

                // Копируем требования к аудитории из слота
                lesson.setRequiredAuditorium(assignment.getCurriculumSlot().getRequiredAuditorium());
                lesson.setPriorityAuditorium(assignment.getCurriculumSlot().getPriorityAuditorium());
                lesson.setAllowedAuditoriumPool(assignment.getCurriculumSlot().getAllowedAuditoriumPool());

                // Устанавливаем ID параллельной группы
                lesson.setParallelGroupId(parallelGroupId);

                allLessons.add(lesson);
            }
        }
        return allLessons;
    }
}
