package ru.services.order;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Правило порядка изучения. Чистая функция — ни Spring, ни БД (как
 * {@link ru.services.reindex.TrackReorderStrategy}), поэтому покрывается юнит-тестами.
 *
 * <p>Обе находки строятся вокруг одного понятия — <b>предшествующей лекции</b>: для занятия с
 * плановой позицией {@code p} это последняя лекция курса с позицией меньше {@code p}
 * (напр. лекция 20 → практика 21).</p>
 *
 * <ol>
 *   <li>{@link OrderViolation.Kind#BEFORE_LECTURE} — занятие стоит по времени <b>раньше</b> своей
 *       предшествующей лекции. Ошибка порядка.</li>
 *   <li>{@link OrderViolation.Kind#FAR_FROM_LECTURE} — занятие стоит <b>слишком далеко</b> после
 *       неё (больше {@code maxGapDays} календарных дней; порог — настройка
 *       {@code schedule.order.max-lecture-gap-days}). Предупреждение: материал забывается.
 *       <b>Аттестации сюда не попадают</b> — они по смыслу стоят в конце курса, и их отрыв от
 *       последней лекции нормален.</li>
 * </ol>
 *
 * <p><b>Почему через группу.</b> Лекция читается потоку, практика — группе, треки разные.
 * Единственный общий знаменатель — группа: правило считается независимо в дорожке каждой
 * группы, а находка схлопывается по {@code placementId} (одно занятие — одна отметка, даже если
 * оно «плохо» у нескольких групп потока). При схлопывании ошибка порядка приоритетнее отрыва.</p>
 *
 * <p><b>Чего этот шаг НЕ делает (сознательно).</b> Не учитывает пересортировку
 * {@link ru.services.reindex.TrackReorderService} (i-я по времени ячейка получает i-е по плану
 * занятие) и не предсказывает вердикт для ячеек-кандидатов. Известный предел описан в
 * {@code docs/ORDER_HIGHLIGHT_ROLLBACK.md}: правило видит «раньше своей лекции», но не видит
 * переполнения окна между лекциями («лишним стал кто-то другой»).</p>
 *
 * <p><b>Занятие без единой размещённой лекции перед собой находкой не считается</b> — сравнивать
 * не с чем (лекция могла быть ещё не размещена; ручная раскладка идёт постепенно).</p>
 */
public class LessonOrderRule {

    private static final Comparator<PlannedLesson> BY_TIME =
            Comparator.comparing(PlannedLesson::date)
                    .thenComparingInt(l -> l.slot().ordinal());

    private static final Comparator<PlannedLesson> BY_PLAN =
            Comparator.comparingInt(PlannedLesson::planPosition);

    /**
     * Находки среди занятий ОДНОГО курса.
     *
     * @param courseLessons размещённые занятия курса (лекции и практики), развёрнутые по группам
     * @param maxGapDays    порог отрыва в календарных днях; {@code <= 0} — проверку отрыва не делать
     * @return по одной находке на занятие (ошибка порядка приоритетнее отрыва)
     */
    public List<OrderViolation> violations(List<PlannedLesson> courseLessons, int maxGapDays) {
        if (courseLessons == null || courseLessons.size() < 2) {
            return List.of();
        }

        // Дорожки: группа → занятия курса, которые эта группа видит.
        Map<Integer, List<PlannedLesson>> byGroup = new HashMap<>();
        for (PlannedLesson lesson : courseLessons) {
            for (Integer groupId : lesson.groupIds()) {
                byGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(lesson);
            }
        }

        Map<UUID, OrderViolation> result = new LinkedHashMap<>();

        for (Map.Entry<Integer, List<PlannedLesson>> track : byGroup.entrySet()) {
            Integer groupId = track.getKey();
            List<PlannedLesson> lessons = track.getValue();

            List<PlannedLesson> lectures = lessons.stream()
                    .filter(PlannedLesson::lecture)
                    .sorted(BY_PLAN)
                    .toList();
            if (lectures.isEmpty()) {
                continue; // сравнивать не с чем
            }

            for (PlannedLesson lesson : lessons) {
                if (lesson.lecture()) {
                    continue;
                }
                PlannedLesson precedingLecture = lastLectureBefore(lectures, lesson.planPosition());
                if (precedingLecture == null) {
                    continue; // по плану идёт раньше любой лекции — предшественника нет
                }

                if (BY_TIME.compare(lesson, precedingLecture) < 0) {
                    put(result, new OrderViolation(lesson.placementId(), precedingLecture.placementId(),
                            groupId, OrderViolation.Kind.BEFORE_LECTURE, 0));
                    continue;
                }

                // Отрыв: аттестации не проверяем — их место в конце курса, это норма.
                if (maxGapDays > 0 && !lesson.assessment()) {
                    int gapDays = (int) ChronoUnit.DAYS.between(precedingLecture.date(), lesson.date());
                    if (gapDays > maxGapDays) {
                        put(result, new OrderViolation(lesson.placementId(), precedingLecture.placementId(),
                                groupId, OrderViolation.Kind.FAR_FROM_LECTURE, gapDays));
                    }
                }
            }
        }

        return List.copyOf(result.values());
    }

    /**
     * Кладёт находку, соблюдая приоритет: ошибка порядка важнее отрыва. Одно занятие может
     * «плохо выглядеть» в дорожках разных групп — отметка всё равно одна.
     */
    private static void put(Map<UUID, OrderViolation> result, OrderViolation finding) {
        OrderViolation existing = result.get(finding.placementId());
        if (existing == null
                || (existing.kind() == OrderViolation.Kind.FAR_FROM_LECTURE
                    && finding.kind() == OrderViolation.Kind.BEFORE_LECTURE)) {
            result.put(finding.placementId(), finding);
        }
    }

    /**
     * Последняя по плану лекция, стоящая в плане ПЕРЕД указанной позицией.
     *
     * @param lecturesByPlan лекции дорожки, отсортированные по {@code planPosition}
     * @param position       позиция занятия в плане
     * @return лекция-предшественник или {@code null}, если таких нет
     */
    private static PlannedLesson lastLectureBefore(List<PlannedLesson> lecturesByPlan, int position) {
        PlannedLesson found = null;
        for (PlannedLesson lecture : lecturesByPlan) {
            if (lecture.planPosition() < position) {
                found = lecture;
            } else {
                break; // список отсортирован — дальше только позиции больше
            }
        }
        return found;
    }
}
