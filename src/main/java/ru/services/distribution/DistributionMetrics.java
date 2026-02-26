package ru.services.distribution;

import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;

/**
 * Вычисляет метрики для распределения.
 * Вынесены методы из DistributionDiscipline:
 * - countPracticesInDate()
 * - calculateCompactnessBonus()
 * - countLessonsInDateForEducator()
 * - countNearbyDaysWithLessons()
 */
public class DistributionMetrics {
    private final DistributionContext context;
    private final ScheduleWorkspace workspace;

    public DistributionMetrics(DistributionContext context) {
        this.context = context;
        this.workspace = context.getWorkspace();
    }

    /**
     * Подсчитывает количество практик уже размещённых в указанную дату
     * для того же преподавателя и тех же групп.
     */
    public int countPracticesInDate(LocalDate date, Lesson practice) {
        return (int) context.getDistributedLessons().stream()
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .filter(l -> l.getEducators().equals(practice.getEducators()))
                .filter(l -> {
                    CellForLesson cell = workspace.getCellForLesson(l);
                    return cell != null && cell.getDate().equals(date);
                })
                .count();
    }

    /**
     * Рассчитывает бонус к приоритету за компактность расписания.
     * Компактность означает размещение занятий в минимальное количество дней:
     * - +500 если в этот же день уже есть занятия преподавателя
     * - +200 если есть занятия в соседний день (±1 день)
     * - +100 если есть занятия в ±2 дня
     */
    public int calculateCompactnessBonus(LocalDate targetDate, Lesson practice, Educator educator) {
        int lessonsInTargetDay = countLessonsInDate(targetDate, educator);
        if (lessonsInTargetDay > 0) {
            return 500;
        }

        for (int dayOffset = 1; dayOffset <= 2; dayOffset++) {
            LocalDate prevDay = targetDate.minusDays(dayOffset);
            LocalDate nextDay = targetDate.plusDays(dayOffset);

            int lessonsInPrevDay = countLessonsInDate(prevDay, educator);
            int lessonsInNextDay = countLessonsInDate(nextDay, educator);

            if (lessonsInPrevDay > 0 || lessonsInNextDay > 0) {
                return dayOffset == 1 ? 200 : 100;
            }
        }

        return 0;
    }

    /**
     * Подсчитывает количество занятий преподавателя в указанную дату.
     */
    public int countLessonsInDate(LocalDate date, Educator educator) {
        return (int) context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> {
                    CellForLesson cell = workspace.getCellForLesson(l);
                    return cell != null && cell.getDate().equals(date);
                })
                .count();
    }

    /**
     * Подсчитывает количество дней в радиусе ±2 дней, в которых есть занятия преподавателя.
     */
    public int countNearbyDaysWithLessons(LocalDate targetDate, Lesson practice, Educator educator) {
        int count = 0;
        for (int dayOffset = -2; dayOffset <= 2; dayOffset++) {
            if (dayOffset == 0) continue;
            LocalDate checkDate = targetDate.plusDays(dayOffset);
            if (countLessonsInDate(checkDate, educator) > 0) {
                count++;
            }
        }
        return count;
    }
}
