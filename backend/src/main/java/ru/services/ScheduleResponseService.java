package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dto.ScheduledLessonDto;
import ru.entity.Lesson;
import ru.entity.read.ScheduleView;
import ru.mapper.command.ScheduledLessonMapper;
import ru.services.solver.ScheduleWorkspace;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleResponseService {

    private final ScheduledLessonMapper lessonMapper;

    /**
     * Вариант 1: Для COMMAND SIDE (после генерации).
     * Принимает сетку из Workspace, где уроки привязаны к ячейкам.
     */
    public Map<String, List<ScheduledLessonDto>> buildGridFromWorkspace(ScheduleWorkspace workspace) {
        return workspace.getGrid().getGridMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getDate().toString() + "_" + entry.getKey().getTimeSlotPair().name(),
                        entry -> entry.getValue().stream()
                                .filter(al -> al instanceof Lesson)
                                .map(al -> lessonMapper.toDto((Lesson) al, entry.getKey()))
                                .collect(Collectors.toList())
                ));
    }

    /**
     * Вариант 2: Для QUERY SIDE (при чтении из БД).
     * Принимает плоский список денормализованных View.
     */
    public Map<String, List<ScheduledLessonDto>> buildGridFromViews(List<ScheduleView> views) {
        return views.stream()
                .map(lessonMapper::toDto)
                .collect(Collectors.groupingBy(dto ->
                        dto.date().toString() + "_" + dto.timeSlotPair().name()
                ));
    }

    /**
     * ✅ Вариант 3 (ГИБРИДНЫЙ): Для COMMAND SIDE (сразу после генерации).
     * Читает placements напрямую из Command Side, минуя Query Side.
     * Гарантированно возвращает данные без race conditions.
     *
     * <p>Используется в {@link ru.controllers.ScheduleGenerationController}
     * для немедленного возврата данных фронтенду после генерации.</p>
     *
     * <p>Query Side синхронизируется асинхронно в фоне (@Async) для будущих запросов.</p>
     *
     * @param placements Список размещений из Command Side
     * @return Карта для быстрого O(1) доступа на фронтенде
     */
    public Map<String, List<ScheduledLessonDto>> buildGridFromPlacements(
            List<ru.entity.write.LessonPlacement> placements
    ) {
        if (placements == null || placements.isEmpty()) {
            return Map.of();
        }

        return placements.stream()
                .map(placement -> {
                    // Конвертируем placement в DTO через существующий маппер
                    // Используем перегруженный метод, который принимает placement
                    return lessonMapper.toDto(placement);
                })
                .collect(Collectors.groupingBy(dto ->
                        dto.date().toString() + "_" + dto.timeSlotPair().name()
                ));
    }
}