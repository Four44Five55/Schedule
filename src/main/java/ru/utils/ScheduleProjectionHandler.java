package ru.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.read.ScheduleView;
import ru.entity.write.LessonPlacement;
import ru.events.ScheduleGeneratedEvent;
import ru.events.PlacementChangedEvent;
import ru.repository.read.ScheduleViewRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleProjectionHandler {

    private final ScheduleViewRepository viewRepository;

    /**
     * Слушает событие полной генерации расписания.
     * Превращает LessonPlacement (Command Model) в ScheduleView (Read Model).
     *
     * ⚠️ ОТКЛЮЧЕНО: Используется ScheduleSynchronizer вместо этого.
     * ScheduleSynchronizer имеет более правильную логику с поддержкой нескольких групп.
     */
    // @EventListener  // ОТКЛЮЧЕНО
    @Transactional
    public void handleScheduleGenerated(ScheduleGeneratedEvent event) {
        log.info("Синхронизация Query Side: получено {} размещений", event.getPlacements().size());

        // ВРЕМЕННОЕ РЕШЕНИЕ: очищаем всю schedule_view перед вставкой
        // ПРИМЕЧАНИЕ: Это плохое решение для production, так как ломает concurrency
        // Нужно добавить session_id в schedule_view для правильной реализации
        long oldCount = viewRepository.count();
        viewRepository.deleteAll();
        log.info("🗑️ Очищено {} старых записей из schedule_view", oldCount);

        // Превращаем Placements в View
        List<ScheduleView> views = event.getPlacements().stream()
                .map(this::mapToView)
                .collect(Collectors.toList());

        // Сохраняем в таблицу schedule_view
        viewRepository.saveAll(views);
        log.info("✅ Синхронизация завершена для сессии {}. Вставлено {} записей.",
                 event.getSessionId(), views.size());
    }

    /**
     * Слушает событие изменения одного занятия (Drag-and-Drop на фронте).
     *
     * ⚠️ ОТКЛЮЧЕНО: Используется ScheduleSynchronizer вместо этого.
     */
    // @EventListener  // ОТКЛЮЧЕНО
    @Transactional
    public void handlePlacementChanged(PlacementChangedEvent event) {
        // Удаляем старую версию отображения и записываем новую
        viewRepository.deleteByPlacementId(event.getPlacementId());
        viewRepository.save(mapToView(event.getPlacement()));
        log.info("🔄 Обновлено отображение для занятия {}", event.getPlacementId());
    }

    private ScheduleView mapToView(LessonPlacement p) {
        ScheduleView view = new ScheduleView(p.getId(), p.getScheduledDate(), p.getScheduledSlot());

        var discipline = p.getAssignment().getCurriculumSlot().getDisciplineCourse().getDiscipline();
        view.setDiscipline(discipline.getName(), discipline.getAbbreviation());

        // Мапим первого преподавателя (или объединяем в строку, если нужно)
        if (!p.getAssignment().getEducators().isEmpty()) {
            var ed = p.getAssignment().getEducators().iterator().next();
            view.setEducator(ed.getId(), ed.getName());
        }

        // Мапим поток и название группы
        var stream = p.getAssignment().getStudyStream();
        view.setGroup(stream.getId(), stream.getName());

        // Мапим аудиторию
        if (!p.getAssignedAuditoriums().isEmpty()) {
            var aud = p.getAssignedAuditoriums().iterator().next();
            view.setAuditorium(aud.getId(), aud.getName());
        }

        // Тема
        var theme = p.getAssignment().getCurriculumSlot().getThemeLesson();
        if (theme != null) {
            view.setTheme(theme.getThemeNumber(), theme.getTitle());
        }

        view.setKindOfStudy(p.getAssignment().getCurriculumSlot().getKindOfStudy().name());

        return view;
    }
}