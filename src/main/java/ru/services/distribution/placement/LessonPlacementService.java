package ru.services.distribution.placement;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.services.distribution.core.DistributionContext;
import ru.services.distribution.validator.PlacementValidator;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Базовый сервис для размещения занятий.
 * Вынесены методы из DistributionDiscipline:
 * - distributeSingleLesson()
 * - getAvailableCellsForLesson()
 * - findNearestAvailableDate()
 * - findMinDateForPractice()
 * - findMinDateForSeminar()
 */
@Slf4j
public class LessonPlacementService {
    private final ScheduleWorkspace workspace;
    private final PlacementValidator validator;
    private final DistributionContext context;

    public LessonPlacementService(ScheduleWorkspace workspace, DistributionContext context) {
        this.workspace = workspace;
        this.validator = new PlacementValidator(workspace);
        this.context = context;
    }

    /**
     * Размещает одиночное занятие в указанные ячейки.
     */
    public void placeSingleLesson(Lesson lesson, List<CellForLesson> listCells) {
        List<CellForLesson> cells = getAvailableCells(lesson, listCells);
        for (Iterator<CellForLesson> it = cells.iterator(); it.hasNext(); ) {
            CellForLesson cell = it.next();
            if (isCellFree(lesson, cell)) {
                PlacementOption option = workspace.findPlacementOption(lesson, cell);
                if (option.isPossible()) {
                    workspace.executePlacement(option);
                    context.addDistributedLesson(lesson);
                }
                it.remove();
                break;
            }
        }
    }

    /**
     * Возвращает список доступных ячеек для распределения занятия.
     */
    public List<CellForLesson> getAvailableCells(Lesson lesson, List<CellForLesson> cells) {
        if (lesson == null || cells == null || cells.isEmpty()) {
            return new ArrayList<>();
        }
        Lesson previousLesson = findPreviousLesson(lesson);

        CellForLesson currentCell = workspace.getCellForLesson(previousLesson);
        if (currentCell == null) {
            return new ArrayList<>(cells);
        }

        return cells.stream()
                .filter(cell -> {
                    int dateComparison = cell.getDate().compareTo(currentCell.getDate());

                    if (dateComparison > 0) {
                        return true;
                    } else if (dateComparison == 0) {
                        return cell.getTimeSlotPair().ordinal() > currentCell.getTimeSlotPair().ordinal();
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Ищет ближайшую дату к idealDate, в которую возможно поставить урок.
     */
    public LocalDate findNearestAvailableDate(LocalDate start, Lesson lesson, List<LocalDate> allDates) {
        int startIndex = allDates.indexOf(start);
        if (startIndex == -1) return null;

        // Ищем в радиусе (например, 14 дней)
        for (int offset = 0; offset < 14; offset++) {
            int[] tryIndices = (offset == 0) ? new int[]{startIndex} : new int[]{startIndex + offset, startIndex - offset};

            for (int idx : tryIndices) {
                if (idx >= 0 && idx < allDates.size()) {
                    LocalDate candidateDate = allDates.get(idx);

                    if (validator.isDayViable(candidateDate, lesson)) {
                        return candidateDate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Находит минимально допустимую дату для практики.
     */
    public LocalDate findMinDate(Lesson practice, Educator educator) {
        List<Lesson> educatorLessons = context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .sorted(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()))
                .collect(Collectors.toList());

        int practiceIndex = educatorLessons.indexOf(practice);
        if (practiceIndex == -1) {
            log.warn("findMinDateForPractice: практика ID={} не найдена в списке занятий преподавателя {}",
                    practice.getCurriculumSlot().getId(), educator.getName());
            return null;
        }

        LocalDate minDate = null;

        // 1. Для СЕМИНАРОВ: ищем последнюю лекцию с той же темой
        if (practice.getKindOfStudy() == ru.enums.KindOfStudy.SEMINAR) {
            LocalDate seminarMinDate = findMinDateForSeminar(practice, educator, educatorLessons, practiceIndex);
            if (seminarMinDate != null) {
                minDate = seminarMinDate;
            }
        }

        // 2. Общий случай: ищем предыдущую лекцию
        if (minDate == null) {
            for (int i = practiceIndex - 1; i >= 0; i--) {
                Lesson previous = educatorLessons.get(i);
                if (previous.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE) {
                    CellForLesson cell = workspace.getCellForLesson(previous);
                    if (cell != null) {
                        minDate = cell.getDate();
                        break;
                    }
                }
            }
        }

        // Если не нашли лекцию — берём начало семестра
        if (minDate == null) {
            minDate = LocalDate.of(2026, 1, 1);
        }

        return minDate;
    }

    /**
     * Находит минимально допустимую дату для СЕМИНАРА.
     */
    private LocalDate findMinDateForSeminar(Lesson seminar, Educator educator,
                                            List<Lesson> educatorLessons, int seminarIndex) {
        if (seminar.getCurriculumSlot().getThemeLesson() == null) {
            return null;
        }

        Integer themeId = seminar.getCurriculumSlot().getThemeLesson().getId();

        for (int i = seminarIndex - 1; i >= 0; i--) {
            Lesson candidate = educatorLessons.get(i);

            if (candidate.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE) {
                continue;
            }

            if (candidate.getCurriculumSlot().getThemeLesson() == null) {
                continue;
            }

            Integer candidateThemeId = candidate.getCurriculumSlot().getThemeLesson().getId();
            if (themeId.equals(candidateThemeId)) {
                CellForLesson cell = workspace.getCellForLesson(candidate);
                if (cell != null) {
                    LocalDate lectureDate = cell.getDate();
                    return lectureDate.plusDays(3);
                }
            }
        }

        return null;
    }

    /**
     * Проверяет возможность назначения занятия в заданную ячейку.
     */
    private boolean isCellFree(Lesson lesson, CellForLesson cell) {
        return workspace.findPlacementOption(lesson, cell).isPossible();
    }

    /**
     * Находит предыдущее занятие для указанного урока.
     */
    private Lesson findPreviousLesson(Lesson currentLesson) {
        if (currentLesson == null || currentLesson.getCurriculumSlot() == null) {
            return null;
        }

        Integer currentDisciplineId = currentLesson.getDisciplineCourse().getDiscipline().getId();
        Integer currentSlotId = currentLesson.getCurriculumSlot().getId();
        Set<Group> currentGroups = currentLesson.getStudyStream() != null
                ? currentLesson.getStudyStream().getGroups()
                : Collections.emptySet();

        for (int i = context.getLessons().indexOf(currentLesson) - 1; i >= 0; i--) {
            Lesson candidate = context.getLessons().get(i);

            boolean sameDiscipline = candidate.getDisciplineCourse().getDiscipline().getId().equals(currentDisciplineId);
            boolean earlierSlot = candidate.getCurriculumSlot().getId() < currentSlotId;

            if (sameDiscipline && earlierSlot) {
                Set<Group> candidateGroups = candidate.getStudyStream() != null
                        ? candidate.getStudyStream().getGroups()
                        : Collections.emptySet();

                if (hasCommonGroups(currentGroups, candidateGroups)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /**
     * Проверяет, есть ли общие группы между двумя наборами.
     */
    private static boolean hasCommonGroups(Set<Group> groups1, Set<Group> groups2) {
        return !Collections.disjoint(groups1, groups2);
    }
}
