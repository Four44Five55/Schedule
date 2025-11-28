package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.*;
import ru.repository.*;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;
import ru.services.factories.LessonFactory;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.legacy.LegacyAlgorithmRunner;
import ru.services.solver.model.ScheduleGrid;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    // --- Зависимости для загрузки данных ---
    private final EducatorRepository educatorRepository;
    private final GroupRepository groupRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final ConstraintService constraintService;
    private final LessonFactory lessonFactory;

    /**
     * Основной метод, запускающий процесс генерации расписания для одного курса.
     * @param courseId ID курса (DisciplineCourse), для которого генерируется расписание.
     * @return Заполненный объект ScheduleGrid.
     */
    @Transactional(readOnly = true) // Транзакция нужна для загрузки всех LAZY-полей
    public ScheduleGrid generateForCourse(Integer courseId) {

        // --- 1. ПОДГОТОВКА ДАННЫХ ---
        // Загружаем все ресурсы, которые могут понадобиться.
        List<Educator> allEducators = educatorRepository.findAll();
        List<Group> allGroups = groupRepository.findAll();
        List<Auditorium> allAuditoriums = auditoriumRepository.findAll();
        AllConstraints allConstraints = constraintService.loadAllConstraints();

        // Создаем "заявки на занятия"
        List<Lesson> lessonsToPlace = lessonFactory.createLessonsForCourse(courseId);

        // --- 2. ИНИЦИАЛИЗАЦИЯ ЯДРА РЕШАТЕЛЯ ---
        // Создаем рабочее пространство, передавая ему все загруженные данные.
        // Даты пока можно захардкодить, потом будем брать их из StudyPeriod.
        ScheduleWorkspace workspace = new ScheduleWorkspace(
                java.time.LocalDate.of(2025, 9, 1),
                java.time.LocalDate.of(2026, 1, 31),
                allEducators,
                allGroups,
                allAuditoriums,
                allConstraints
        );

        // --- 3. ЗАПУСК АЛГОРИТМА ---
        // Создаем и запускаем адаптер для вашего старого алгоритма.
        LegacyAlgorithmRunner runner = new LegacyAlgorithmRunner(workspace);
        runner.distribute(lessonsToPlace); // Метод, который мы напишем на следующем шаге

        // --- 4. ВОЗВРАТ РЕЗУЛЬТАТА ---
        return workspace.getGrid();
    }
}
