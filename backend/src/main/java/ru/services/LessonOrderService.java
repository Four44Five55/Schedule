package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.order.OrderViolationDto;
import ru.entity.Assignment;
import ru.entity.Group;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.write.LessonPlacement;
import ru.enums.KindOfStudy;
import ru.repository.write.LessonPlacementRepository;
import ru.services.order.LessonOrderRule;
import ru.services.order.OrderViolation;
import ru.services.order.PlannedLesson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Нарушения порядка изучения в стоящем расписании (Command Side, только чтение).
 *
 * <p>Сборочный слой над чистым правилом {@link LessonOrderRule}: достаёт размещения сессии,
 * разворачивает их в {@link PlannedLesson} (по группам — лекция у потока, практика у группы),
 * группирует по курсу и прогоняет правило. Сам вердикт живёт в правиле и тестируется юнитами.</p>
 *
 * <p><b>Один запрос на всё расписание</b> (требование заказчика): фронт получает карту нарушений
 * целиком и подсвечивает без HTTP на каждое наведение.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonOrderService {

    private final LessonPlacementRepository placementRepo;

    private final LessonOrderRule rule = new LessonOrderRule();

    /**
     * Норма отрыва занятия от предшествующей лекции, календарных дней. Настройка, а не константа:
     * подходящее число зависит от интенсивности дисциплины (лекция раз в неделю → две недели
     * отрыва почти норма; блочный курс → и три дня много), и его хочется крутить без пересборки.
     * {@code <= 0} — проверку отрыва выключить (ошибки порядка продолжат ловиться).
     */
    @Value("${schedule.order.max-lecture-gap-days:14}")
    private int maxLectureGapDays;

    /**
     * Нарушения порядка во всём расписании сессии.
     *
     * @param sessionId сессия
     * @return нарушители с указанием лекции-причины (пусто, если всё в порядке)
     */
    @Transactional(readOnly = true)
    public List<OrderViolationDto> violationsOf(UUID sessionId) {
        List<LessonPlacement> placements = placementRepo.findBySessionId(sessionId);
        if (placements.isEmpty()) {
            return List.of();
        }

        // Правило считается в пределах курса: сравнивать практику с лекцией чужой дисциплины
        // бессмысленно.
        Map<Integer, List<PlannedLesson>> byCourse = new HashMap<>();
        for (LessonPlacement placement : placements) {
            Assignment assignment = placement.getAssignment();
            if (assignment == null) {
                continue;
            }
            CurriculumSlot slot = assignment.getCurriculumSlot();
            Integer courseId = slot.getDisciplineCourse().getId();

            Set<Integer> groupIds = assignment.getStudyStream().getGroups().stream()
                    .map(Group::getId)
                    .collect(Collectors.toSet());

            KindOfStudy kind = slot.getKindOfStudy();
            byCourse.computeIfAbsent(courseId, k -> new ArrayList<>())
                    .add(new PlannedLesson(
                            placement.getId(),
                            placement.getScheduledDate(),
                            placement.getScheduledSlot(),
                            slot.getPosition(),
                            kind == KindOfStudy.LECTURE,
                            ASSESSMENT_KINDS.contains(kind),
                            groupIds));
        }

        List<OrderViolationDto> result = new ArrayList<>();
        for (List<PlannedLesson> courseLessons : byCourse.values()) {
            for (OrderViolation violation : rule.violations(courseLessons, maxLectureGapDays)) {
                result.add(new OrderViolationDto(
                        violation.placementId().toString(),
                        violation.lecturePlacementId().toString(),
                        violation.groupId(),
                        violation.kind().name(),
                        violation.gapDays()));
            }
        }

        long errors = result.stream()
                .filter(v -> OrderViolation.Kind.BEFORE_LECTURE.name().equals(v.kind()))
                .count();
        log.info("↕ Порядок изучения: сессия={}, курсов={}, раньше лекции={}, отрыв>{}д={}",
                sessionId, byCourse.size(), errors, maxLectureGapDays, result.size() - errors);
        return result;
    }

    /**
     * Аттестации: стоят в конце курса, через месяцы после последней лекции — это норма, а не
     * дефект. Из проверки ОТРЫВА исключены (иначе загорелись бы все и утопили полезный сигнал),
     * но в проверке «раньше лекции» участвуют наравне с практиками.
     */
    private static final Set<KindOfStudy> ASSESSMENT_KINDS = Set.of(
            KindOfStudy.EXAM,
            KindOfStudy.CREDIT_WITH_GRADE,
            KindOfStudy.CREDIT_WITHOUT_GRADE);
}
