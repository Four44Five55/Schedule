package ru.services.auditorium;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.auditorium.AuditoriumViolationDto;
import ru.entity.Auditorium;
import ru.entity.write.LessonPlacement;
import ru.repository.write.LessonPlacementRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Находки по аудиториям в стоящем расписании — <b>по занятию</b>, для подсветки в сетке
 * (Command Side, только чтение). Зеркало {@link ru.services.LessonOrderService}: сборочный слой
 * над тем же чистым правилом {@link AuditoriumUsageRule}, но здесь находки не схлопываются в
 * счётчики (как в {@link AuditoriumHealthService} для дашборда), а возвращаются пофамильно.</p>
 *
 * <p><b>Один запрос на всё расписание</b> (как order-violations): фронт держит карту находок и
 * перезапрашивает её после каждого изменения, а не на каждое наведение.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriumViolationService {

    private final LessonPlacementRepository placementRepo;

    private final AuditoriumUsageRule rule = new AuditoriumUsageRule();

    /**
     * Находки по аудиториям во всём расписании сессии.
     *
     * @param sessionId сессия
     * @return находки по занятиям (пусто, если всё в порядке)
     */
    @Transactional(readOnly = true)
    public List<AuditoriumViolationDto> violationsOf(UUID sessionId) {
        List<LessonPlacement> placements = placementRepo.findBySessionId(sessionId);
        if (placements.isEmpty()) {
            return List.of();
        }

        // Разворот «размещение × комната» — как в AuditoriumHealthService (то же правило, тот же
        // вход). Плюс имена комнат и описания занятий для тултипа.
        Map<Integer, Auditorium> rooms = new HashMap<>();
        Map<UUID, String> describeById = new HashMap<>();
        List<RoomedLesson> roomed = new ArrayList<>();
        List<AuditoriumFinding> roomless = new ArrayList<>();

        for (LessonPlacement placement : placements) {
            describeById.put(placement.getId(), describe(placement));
            Set<Auditorium> assigned = placement.getAssignedAuditoriums();
            if (assigned == null || assigned.isEmpty()) {
                // Занятие без комнаты правилу не предъявить — оно про ИСПОЛЬЗОВАНИЕ комнаты, а её
                // нет. Но и молчать нельзя: занятие где-то идёт, а где — неизвестно. У импорта это
                // массовое состояние: комнаты из файла может не быть в справочнике.
                roomless.add(AuditoriumFinding.noAuditorium(placement.getId(),
                        placement.getScheduledDate(), placement.getScheduledSlot()));
                continue;
            }
            int headcount = placement.getAssignment().getStudyStream().calculateTotalSize();
            for (Auditorium room : assigned) {
                rooms.putIfAbsent(room.getId(), room);
                roomed.add(new RoomedLesson(placement.getId(), placement.getScheduledDate(),
                        placement.getScheduledSlot(), room.getId(), room.getCapacity(), headcount));
            }
        }

        List<AuditoriumFinding> findings = new ArrayList<>(rule.check(roomed));
        findings.addAll(roomless);
        List<AuditoriumViolationDto> result = new ArrayList<>(findings.size());
        for (AuditoriumFinding finding : findings) {
            Auditorium room = rooms.get(finding.auditoriumId());
            List<String> sharedWith = finding.sharedWith().stream()
                    .map(id -> describeById.getOrDefault(id, "другое занятие"))
                    .toList();
            result.add(new AuditoriumViolationDto(
                    finding.placementId().toString(),
                    finding.auditoriumId(),
                    room != null ? room.getName() : null,
                    finding.kind().name(),
                    finding.excess(),
                    sharedWith));
        }
        return result;
    }

    /** Человекочитаемое описание занятия для тултипа: «аббревиатура · поток». */
    private static String describe(LessonPlacement placement) {
        StringBuilder sb = new StringBuilder();
        var assignment = placement.getAssignment();
        if (assignment != null) {
            var slot = assignment.getCurriculumSlot();
            if (slot != null && slot.getDisciplineCourse() != null
                    && slot.getDisciplineCourse().getDiscipline() != null) {
                sb.append(slot.getDisciplineCourse().getDiscipline().getAbbreviation());
            }
            if (assignment.getStudyStream() != null) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(assignment.getStudyStream().getName());
            }
        }
        return sb.length() > 0 ? sb.toString() : "другое занятие";
    }
}
