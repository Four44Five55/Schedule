package ru.services.distribution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.entity.Lesson;
import ru.services.distribution.strategy.PracticeSlotStrategy;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Для каждой цепочки заранее вычисляет целевую дату размещения,
 * равномерно распределяя занятия по доступным слотам семестра.
 */
@Slf4j
@RequiredArgsConstructor
public class PracticePreScheduler {

    private final   LessonDateFinder dateFinder;


    /**
     * Возвращает Map: lesson → targetDate
     * для всех практик одного преподавателя.
     */
    public Map<Lesson, LocalDate> buildTargetDates(
            List<Lesson> practices,
            PracticeSlotStrategy strategy,
            LocalDate semesterEnd) {

        Map<Lesson, LocalDate> result = new HashMap<>();

        // Группируем по ключу дисциплина+поток
        Map<String, List<Lesson>> byKey = practices.stream()
                .collect(Collectors.groupingBy(this::getPlacementKey,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparingInt(
                                                l -> l.getCurriculumSlot().getPosition()))
                                        .toList())));

        for (Map.Entry<String, List<Lesson>> entry : byKey.entrySet()) {
            List<Lesson> group = entry.getValue();
            Lesson representative = group.getFirst();

            // Все доступные дни для этой группы занятий
            List<LocalDate> available = dateFinder.getAvailableDates(
                    representative, semesterEnd, strategy.getSkipPairs());

            int needed = group.size();
            int total = available.size();

            if (total == 0) continue;

            if (needed <= total) {
                // Равномерно распределяем: шаг = total / needed
                // Например: 10 занятий, 62 слота → каждые ~6 слотов
                double step = (double) total / needed;
                for (int i = 0; i < needed; i++) {
                    int slotIndex = (int) Math.round(i * step);
                    slotIndex = Math.min(slotIndex, total - 1);
                    result.put(group.get(i), available.get(slotIndex));
                }
            } else {
                // Слотов меньше чем занятий — берём все подряд
                // (это ситуация перегрузки — логируем)
                log.warn("  [{}] недостаточно слотов: нужно={}, доступно={}",
                        entry.getKey(), needed, total);
                for (int i = 0; i < Math.min(needed, total); i++) {
                    result.put(group.get(i), available.get(i));
                }
            }
        }

        return result;
    }

    private String getPlacementKey(Lesson lesson) {
        int streamId = lesson.getStudyStream() != null
                ? lesson.getStudyStream().getId() : -1;
        int disciplineId = lesson.getDisciplineCourse().getDiscipline().getId();
        return streamId + "_" + disciplineId;
    }
}
