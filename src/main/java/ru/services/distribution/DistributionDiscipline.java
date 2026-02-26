package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.CurriculumSlotService;
import ru.services.LessonSortingService;
import ru.services.SlotChainService;
import ru.services.distribution.core.DistributionContext;
import ru.services.distribution.core.EducatorPrioritizer;
import ru.services.distribution.lecture.LectureDistributionHandler;
import ru.services.distribution.placement.ChainPlacementHandler;
import ru.services.distribution.placement.LessonPlacementService;
import ru.services.distribution.practice.PracticeDistributionHandler;
import ru.services.distribution.practice.PracticeSwapService;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Оркестратор двухфазного распределения занятий.
 * После рефакторинга делегирует всю работу специализированным компонентам.
 */
@Slf4j
public class DistributionDiscipline {
    private final DistributionContext context;
    private final LectureDistributionHandler lectureHandler;
    private final PracticeDistributionHandler practiceHandler;
    private final LessonSortingService lessonSortingService;

    /**
     * Создаёт оркестратор распределения с необходимыми зависимостями.
     */
    public DistributionDiscipline(ScheduleWorkspace workspace,
                                  List<Lesson> lessons,
                                  List<Educator> educators,
                                  SlotChainService slotChainService,
                                  CurriculumSlotService curriculumSlotService,
                                  LessonSortingService lessonSortingService) {
        this.lessonSortingService = lessonSortingService;

        // Создаём контекст распределения
        this.context = DistributionContext.of(workspace, lessons, educators);

        // Создаём компоненты
        EducatorPrioritizer prioritizer = new EducatorPrioritizer(lessonSortingService);
        LessonPlacementService placementService = new LessonPlacementService(workspace, context);
        ChainPlacementHandler chainHandler = new ChainPlacementHandler(slotChainService, context);
        PracticeSwapService swapService = new PracticeSwapService(context, slotChainService, chainHandler);

        // Создаём обработчики фаз
        this.lectureHandler = new LectureDistributionHandler(context, prioritizer, placementService, chainHandler);
        this.practiceHandler = new PracticeDistributionHandler(context, lessonSortingService,
                slotChainService, placementService, chainHandler, swapService);
    }

    /**
     * Основной метод распределения занятий.
     * Разделяет на экзамены и обычные занятия, затем запускает двухфазное распределение.
     */
    public void distributeLessons() {
        // 1. Разделяем занятия на экзамены и обычные
        List<Lesson> examLessons = new ArrayList<>();
        List<Lesson> regularLessons = new ArrayList<>();

        for (Lesson lesson : context.getLessons()) {
            if (lesson.getKindOfStudy() == ru.enums.KindOfStudy.EXAM) {
                examLessons.add(lesson);
            } else {
                regularLessons.add(lesson);
            }
        }

        // TODO: реализовать распределение экзаменов
        // distributeExams(examLessons);

        // 2. Обновляем контекст только с регулярными занятиями
        context.setLessons(regularLessons);
        LocalDate semesterEnd = LocalDate.of(2026, 8, 16);

        // 3. Двухфазное распределение
        lectureHandler.distributeLectures(semesterEnd);
        practiceHandler.distributePractices(semesterEnd);
    }

    /**
     * Распределяет занятия для конкретного преподавателя.
     * Используется для обратной совместимости.
     */
    public void distributeLessonsForEducator(Educator educator, List<Lesson> educatorLessons, LocalDate semesterEnd) {
        lectureHandler.distributeLecturesForEducator(educator, semesterEnd);
    }

    // ========== Getters для обратной совместимости ==========

    public ScheduleWorkspace getWorkspace() {
        return context.getWorkspace();
    }

    public List<Lesson> getLessons() {
        return context.getLessons();
    }

    public List<Educator> getEducators() {
        return context.getEducators();
    }

    public List<Lesson> getDistributedLessons() {
        return context.getDistributedLessons();
    }
}
