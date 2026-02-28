package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.CurriculumSlotService;
import ru.services.SlotChainService;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Оркестратор двухфазного распределения занятий.
 * Spring-сервис с динамическим созданием обработчиков.
 */
@Slf4j
@Service
public class DistributionDiscipline {
    private final EducatorPrioritizer educatorPrioritizer;
    private final SlotChainService slotChainService;
    private final CurriculumSlotService curriculumSlotService;

    private DistributionContext context;
    private LectureDistributionHandler lectureHandler;
    private PracticeDistributionHandler practiceHandler;

    /**
     * Конструктор для Spring DI.
     */
    @Autowired
    public DistributionDiscipline(EducatorPrioritizer educatorPrioritizer,
                                  SlotChainService slotChainService,
                                  CurriculumSlotService curriculumSlotService) {
        this.educatorPrioritizer = educatorPrioritizer;
        this.slotChainService = slotChainService;
        this.curriculumSlotService = curriculumSlotService;
    }

    /**
     * Инициализирует оркестратор с необходимыми параметрами.
     */
    private void initialize(ScheduleWorkspace workspace,
                            List<Lesson> lessons,
                            List<Educator> educators) {
        // Создаём контекст распределения
        this.context = DistributionContext.of(workspace, lessons, educators);

        // Сортируем преподавателей по приоритету и обновляем контекст
        List<Educator> sortedEducators = educatorPrioritizer.sortByPriority(educators, lessons);
        this.context.setEducators(sortedEducators);

        // Создаём компоненты
        LessonPlacementService placementService = new LessonPlacementService(workspace, context);
        ChainPlacementHandler chainHandler = new ChainPlacementHandler(slotChainService, context);

        // Создаём обработчики фаз
        this.lectureHandler = new LectureDistributionHandler(context, placementService);
        this.practiceHandler = new PracticeDistributionHandler(context, placementService, chainHandler);
    }

    /**
     * Factory-метод для создания и запуска распределения.
     */
    public void distribute(ScheduleWorkspace workspace,
                           List<Lesson> lessons,
                           List<Educator> educators) {
        initialize(workspace, lessons, educators);
        distributeLessons();
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

        log.info("=== НАЧАЛО Распределения. Всего занятий: {} ===", regularLessons.size());


        // 3. Двухфазное распределение
        log.info("=== ФАЗА 1: Распределение лекций ===");
        lectureHandler.distributeLectures(semesterEnd);

        log.info("=== ФАЗА 2: Распределение практик ===");
        practiceHandler.distributePractices(semesterEnd);

        // 4. Итоги
        logResults();
    }

    /**
     * Распределяет занятия для конкретного преподавателя.
     * Используется для обратной совместимости.
     */
    public void distributeLessonsForEducator(Educator educator, List<Lesson> educatorLessons, LocalDate semesterEnd) {
        lectureHandler.distributeForEducator(educator, semesterEnd);
        practiceHandler.distributeForEducator(educator, semesterEnd);
    }

    /**
     * Выводит итоги распределения.
     */
    private void logResults() {
        int total = context.getLessons().size();
        int placed = context.getDistributedLessons().size();
        int unplaced = total - placed;

        log.info("=== ИТОГИ Распределения ===");
        log.info("Всего занятий: {}", total);
        log.info("Размещено: {}", placed);
        log.info("Не размещено: {}", unplaced);

        if (unplaced > 0) {
            log.warn("=== НЕРАЗМЕЩЁННЫЕ занятия ===");
            for (Lesson lesson : context.getLessons()) {
                if (!context.isLessonDistributed(lesson)) {
                    String theme = lesson.getCurriculumSlot().getThemeLesson() != null
                            ? lesson.getCurriculumSlot().getThemeLesson().getThemeNumber()
                            : "N/A";
                    log.warn("  {}/{}, тема: {}, преподаватель: {}, группы: {}",
                            lesson.getKindOfStudy().getAbbreviationName(),
                            lesson.getCurriculumSlot().getPosition(),
                            theme,
                            lesson.getEducators(),
                            lesson.getStudyStream().getGroups());
                }
            }
        }
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
