package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для размещения занятий.
 * Упрощённая версия, объединяющая LessonPlacementService и PlacementValidator.
 */
@Slf4j
public class LessonPlacementService {
    private final ScheduleWorkspace workspace;
    private final DistributionContext context;

    public LessonPlacementService(ScheduleWorkspace workspace, DistributionContext context) {
        this.workspace = workspace;
        this.context = context;
    }

    /**
     * Проверяет, можно ли разместить занятие в указанную дату.
     */
    public boolean canPlace(Lesson lesson, LocalDate date) {
        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);

        for (CellForLesson cell : dayCells) {
            if (shouldSkipCell(cell)) continue;

            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (option.isPossible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Размещает занятие в указанную дату.
     * @return true если размещение успешно
     */
    public boolean place(Lesson lesson, LocalDate date) {
        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);

        for (CellForLesson cell : dayCells) {
            if (shouldSkipCell(cell)) continue;

            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (option.isPossible()) {
                workspace.executePlacement(option);
                context.addDistributedLesson(lesson);
                log.info("Размещено: {} на {}", lesson.getCurriculumSlot().getId(), date);
                return true;
            }
        }

        log.warn("Не удалось разместить: {} на {}", lesson.getCurriculumSlot().getId(), date);
        return false;
    }

    /**
     * Размещает занятие в первую доступную ячейку указанной даты.
     */
    public boolean placeInDay(Lesson lesson, LocalDate date) {
        return place(lesson, date);
    }

    /**
     * Находит первую доступную дату для занятия из списка.
     */
    public LocalDate findAvailableDate(Lesson lesson, List<LocalDate> dates) {
        for (LocalDate date : dates) {
            if (canPlace(lesson, date)) {
                return date;
            }
        }
        return null;
    }

    /**
     * Проверяет, следует ли пропустить ячейку (4-я пара).
     */
    private boolean shouldSkipCell(CellForLesson cell) {
        return cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH;
    }

    /**
     * Получает список занятий преподавателя.
     */
    public List<Lesson> getLessonsForEducator(Educator educator) {
        return context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .collect(Collectors.toList());
    }

    /**
     * Получает лекции преподавателя.
     */
    public List<Lesson> getLecturesForEducator(Educator educator) {
        return context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    /**
     * Получает практики преподавателя (не лекции).
     */
    public List<Lesson> getPracticesForEducator(Educator educator) {
        return context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    /**
     * Получает даты уже размещённых лекций преподавателя.
     */
    public Set<LocalDate> getLectureDates(Educator educator) {
        return context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE)
                .map(l -> workspace.getCellForLesson(l))
                .filter(cell -> cell != null)
                .map(CellForLesson::getDate)
                .collect(Collectors.toSet());
    }

    /**
     * Получает список доступных дат для указанного занятия.
     */
    public List<LocalDate> getAvailableDates(Lesson lesson, LocalDate startDate, LocalDate endDate) {
        return CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isBefore(startDate))
                .filter(d -> !d.isAfter(endDate))
                .filter(d -> canPlace(lesson, d))
                .sorted()
                .collect(Collectors.toList());
    }
}
