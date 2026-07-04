package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dto.ScheduledLessonDto;
import ru.entity.Lesson;
import ru.entity.read.ScheduleView;
import ru.mapper.command.ScheduledLessonMapper;
import ru.utils.GroupNameComparator;
import ru.services.solver.ScheduleWorkspace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        // Query Side хранит расписание денормализованно — по одной строке на каждую
        // группу потока (и на каждого преподавателя/аудиторию). Чтобы в ячейке было
        // одно занятие со всеми группами, склеиваем строки одного размещения обратно.
        Map<Object, List<ScheduleView>> byLesson = views.stream()
                .collect(Collectors.groupingBy(this::lessonKey, LinkedHashMap::new, Collectors.toList()));

        return byLesson.values().stream()
                .map(this::mergeViewsToDto)
                .collect(Collectors.groupingBy(dto ->
                        dto.date().toString() + "_" + dto.timeSlotPair().name()
                ));
    }

    /**
     * Ключ логического занятия. Все строки одного размещения (placement) относятся
     * к одному занятию; если placementId не задан — используем составной ключ.
     */
    private Object lessonKey(ScheduleView v) {
        if (v.getPlacementId() != null) {
            return v.getPlacementId();
        }
        return String.join("|",
                String.valueOf(v.getScheduledDate()),
                String.valueOf(v.getTimeSlot()),
                String.valueOf(v.getStudyStreamId()));
    }

    /**
     * Склеивает строки одного занятия в один DTO: скалярные поля берём из первой
     * строки (через маппер), а группы/преподавателей/аудитории объединяем без дублей.
     */
    private ScheduledLessonDto mergeViewsToDto(List<ScheduleView> rows) {
        ScheduledLessonDto base = lessonMapper.toDto(rows.get(0));

        // Порядок строк из запроса не гарантирован (нет тай-брейкера по группе), поэтому
        // фиксируем порядок групп здесь — иначе в ячейке номера «скачут» между перезагрузками.
        // Порядок по коду группы (уровни через «/»); у ScheduledLessonDto нет парного
        // groupIds, сортировать имена безопасно.
        List<String> groupNames = rows.stream()
                .map(ScheduleView::getGroupName).filter(Objects::nonNull).distinct()
                .sorted(GroupNameComparator.INSTANCE).collect(Collectors.toList());
        List<Integer> educatorIds = rows.stream()
                .map(ScheduleView::getEducatorId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<String> educatorNames = rows.stream()
                .map(ScheduleView::getEducatorName).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Integer> auditoriumIds = rows.stream()
                .map(ScheduleView::getAuditoriumId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<String> auditoriumNames = rows.stream()
                .map(ScheduleView::getAuditoriumName).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        return new ScheduledLessonDto(
                base.id(),
                base.date(),
                base.timeSlotPair(),
                base.disciplineName(),
                base.disciplineAbbreviation(),
                base.kindOfStudy(),
                base.kindOfStudyName(),
                base.kindOfStudyAbbr(),
                base.position(),
                base.themeNumber(),
                base.themeTitle(),
                educatorIds,
                educatorNames,
                base.streamName(),
                groupNames,
                auditoriumNames,
                auditoriumIds,
                base.placementId(),
                base.curriculumSlotId(),
                base.locked(),
                base.source()
        );
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