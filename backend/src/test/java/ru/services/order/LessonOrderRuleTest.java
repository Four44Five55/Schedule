package ru.services.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты правила порядка изучения: «раньше своей лекции» (ошибка) и «слишком далеко от
 * своей лекции» (предупреждение).
 *
 * <p>Прошлая реализация этой фичи была откачена, ни разу не будучи запущенной, и тестов на
 * правило не писали — см. {@code docs/ORDER_HIGHLIGHT_ROLLBACK.md}. Здесь фиксируем и то, что
 * правило ловит, и то, чего оно (пока) сознательно НЕ ловит.</p>
 */
class LessonOrderRuleTest {

    private final LessonOrderRule rule = new LessonOrderRule();

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);
    private static final LocalDate D4 = LocalDate.of(2026, 9, 4);

    private static final int GROUP = 101;
    private static final int MAX_GAP = 14;

    private static PlannedLesson lecture(UUID id, LocalDate date, int position) {
        return new PlannedLesson(id, date, TimeSlotPair.FIRST, position, true, false, Set.of(GROUP));
    }

    private static PlannedLesson practice(UUID id, LocalDate date, int position) {
        return new PlannedLesson(id, date, TimeSlotPair.FIRST, position, false, false, Set.of(GROUP));
    }

    private static PlannedLesson assessment(UUID id, LocalDate date, int position) {
        return new PlannedLesson(id, date, TimeSlotPair.FIRST, position, false, true, Set.of(GROUP));
    }

    // ===================== Порядок: раньше своей лекции =====================

    @Test
    @DisplayName("Практика стоит раньше своей лекции — ошибка порядка, причина указывает на лекцию")
    void practiceBeforeItsLecture() {
        UUID lectureId = UUID.randomUUID();
        UUID practiceId = UUID.randomUUID();

        // План: лекция поз.20, практика поз.21. По времени практика в D1, лекция в D2 — криво.
        List<OrderViolation> found = rule.violations(List.of(
                lecture(lectureId, D2, 20),
                practice(practiceId, D1, 21)
        ), MAX_GAP);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).placementId()).isEqualTo(practiceId);
        assertThat(found.get(0).lecturePlacementId()).isEqualTo(lectureId);
        assertThat(found.get(0).kind()).isEqualTo(OrderViolation.Kind.BEFORE_LECTURE);
        assertThat(found.get(0).groupId()).isEqualTo(GROUP);
    }

    @Test
    @DisplayName("Практика вскоре после своей лекции — находок нет")
    void practiceRightAfterItsLecture() {
        List<OrderViolation> found = rule.violations(List.of(
                lecture(UUID.randomUUID(), D1, 20),
                practice(UUID.randomUUID(), D2, 21)
        ), MAX_GAP);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("В один день сравнение идёт по паре: практика на 1-й паре раньше лекции на 2-й — ошибка")
    void sameDayComparesBySlot() {
        UUID lectureId = UUID.randomUUID();
        UUID practiceId = UUID.randomUUID();

        List<OrderViolation> found = rule.violations(List.of(
                new PlannedLesson(lectureId, D1, TimeSlotPair.SECOND, 20, true, false, Set.of(GROUP)),
                new PlannedLesson(practiceId, D1, TimeSlotPair.FIRST, 21, false, false, Set.of(GROUP))
        ), MAX_GAP);

        assertThat(found).extracting(OrderViolation::placementId).containsExactly(practiceId);
    }

    @Test
    @DisplayName("Практика раньше ЛЮБОЙ лекции по плану — предшественника нет, находки нет")
    void practiceBeforeAnyLectureInPlan() {
        List<OrderViolation> found = rule.violations(List.of(
                practice(UUID.randomUUID(), D1, 1),
                lecture(UUID.randomUUID(), D2, 2)
        ), MAX_GAP);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Лекция ещё не размещена — сравнивать не с чем")
    void noLecturePlacedYet() {
        List<OrderViolation> found = rule.violations(List.of(
                practice(UUID.randomUUID(), D1, 21),
                practice(UUID.randomUUID(), D2, 22)
        ), MAX_GAP);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Предшественник — БЛИЖАЙШАЯ лекция слева по плану, а не первая попавшаяся")
    void takesNearestPrecedingLecture() {
        UUID l1 = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();
        UUID practiceId = UUID.randomUUID();

        // Л1(поз.10, D1) — Л2(поз.20, D3) — практика(поз.21, D2): позже Л1, но раньше Л2.
        List<OrderViolation> found = rule.violations(List.of(
                lecture(l1, D1, 10),
                lecture(l2, D3, 20),
                practice(practiceId, D2, 21)
        ), MAX_GAP);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).lecturePlacementId()).isEqualTo(l2);
    }

    @Test
    @DisplayName("Занятие потока нарушает у нескольких групп — отметка одна")
    void violationIsReportedOncePerPlacement() {
        UUID lectureId = UUID.randomUUID();
        UUID practiceId = UUID.randomUUID();

        List<OrderViolation> found = rule.violations(List.of(
                new PlannedLesson(lectureId, D2, TimeSlotPair.FIRST, 20, true, false, Set.of(101, 102)),
                new PlannedLesson(practiceId, D1, TimeSlotPair.FIRST, 21, false, false, Set.of(101, 102))
        ), MAX_GAP);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).placementId()).isEqualTo(practiceId);
    }

    @Test
    @DisplayName("Дорожки групп независимы: у чужой группы занятия не сравниваются")
    void tracksOfDifferentGroupsAreIndependent() {
        List<OrderViolation> found = rule.violations(List.of(
                new PlannedLesson(UUID.randomUUID(), D2, TimeSlotPair.FIRST, 20, true, false, Set.of(101)),
                new PlannedLesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, 21, false, false, Set.of(102))
        ), MAX_GAP);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Аттестация раньше лекции — ошибка порядка ловится (из этой проверки она НЕ исключена)")
    void assessmentBeforeLectureIsStillAnError() {
        UUID examId = UUID.randomUUID();

        List<OrderViolation> found = rule.violations(List.of(
                lecture(UUID.randomUUID(), D4, 29),
                assessment(examId, D1, 30)
        ), MAX_GAP);

        assertThat(found).extracting(OrderViolation::placementId).containsExactly(examId);
        assertThat(found.get(0).kind()).isEqualTo(OrderViolation.Kind.BEFORE_LECTURE);
    }

    // ===================== Отрыв: слишком далеко после лекции =====================

    @Test
    @DisplayName("Отрыв больше нормы — предупреждение с числом дней")
    void practiceTooFarFromItsLecture() {
        UUID lectureId = UUID.randomUUID();
        UUID practiceId = UUID.randomUUID();

        // Лекция 1 сентября, практика 20 сентября → 19 дней при норме 14.
        List<OrderViolation> found = rule.violations(List.of(
                lecture(lectureId, D1, 20),
                practice(practiceId, LocalDate.of(2026, 9, 20), 21)
        ), MAX_GAP);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).placementId()).isEqualTo(practiceId);
        assertThat(found.get(0).lecturePlacementId()).isEqualTo(lectureId);
        assertThat(found.get(0).kind()).isEqualTo(OrderViolation.Kind.FAR_FROM_LECTURE);
        assertThat(found.get(0).gapDays()).isEqualTo(19);
    }

    @Test
    @DisplayName("Отрыв ровно на границе нормы — ещё не предупреждение")
    void gapExactlyAtThresholdIsFine() {
        List<OrderViolation> found = rule.violations(List.of(
                lecture(UUID.randomUUID(), D1, 20),
                practice(UUID.randomUUID(), D1.plusDays(MAX_GAP), 21)
        ), MAX_GAP);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Аттестация далеко от последней лекции — это НОРМА, предупреждения нет")
    void assessmentFarFromLectureIsNormal() {
        // Экзамен через 4 месяца после лекции — так и должно быть.
        List<OrderViolation> found = rule.violations(List.of(
                lecture(UUID.randomUUID(), D1, 29),
                assessment(UUID.randomUUID(), D1.plusMonths(4), 30)
        ), MAX_GAP);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Порог <= 0 выключает проверку отрыва (ошибки порядка продолжают ловиться)")
    void zeroThresholdDisablesGapCheck() {
        List<OrderViolation> found = rule.violations(List.of(
                lecture(UUID.randomUUID(), D1, 20),
                practice(UUID.randomUUID(), D1.plusMonths(6), 21)
        ), 0);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Ошибка порядка приоритетнее отрыва при схлопывании по занятию")
    void beforeLectureWinsOverFarFromLecture() {
        UUID practiceId = UUID.randomUUID();

        // У группы 101 практика раньше лекции (ошибка), у группы 102 — далеко после (отрыв).
        // Занятие одно → отметка одна, и она должна быть ошибкой.
        List<OrderViolation> found = rule.violations(List.of(
                new PlannedLesson(UUID.randomUUID(), LocalDate.of(2026, 10, 1), TimeSlotPair.FIRST, 20, true, false, Set.of(101)),
                new PlannedLesson(UUID.randomUUID(), LocalDate.of(2026, 8, 1), TimeSlotPair.FIRST, 20, true, false, Set.of(102)),
                new PlannedLesson(practiceId, LocalDate.of(2026, 9, 1), TimeSlotPair.FIRST, 21, false, false, Set.of(101, 102))
        ), MAX_GAP);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).kind()).isEqualTo(OrderViolation.Kind.BEFORE_LECTURE);
    }

    // ===================== Границы шага =====================

    @Test
    @DisplayName("ГРАНИЦА: переполнение окна между лекциями НЕ ловится (контрпример из ROLLBACK.md)")
    void knownLimitation_windowOverflowIsNotDetected() {
        // План: Л1(поз.1) — ПЗ(2) — ПЗ(3) — Л2(4) — ПЗ(5). Каждая практика стоит позже своей
        // лекции и в пределах нормы, поэтому находок нет. Кумулятивное правило (шаг 3) увидело бы
        // переполнение окна, текущее — нет. Тест фиксирует ПРЕДЕЛ, а не желаемое поведение.
        List<OrderViolation> found = rule.violations(List.of(
                lecture(UUID.randomUUID(), D1, 1),
                practice(UUID.randomUUID(), D2, 2),
                practice(UUID.randomUUID(), D2, 3),
                lecture(UUID.randomUUID(), D3, 4),
                practice(UUID.randomUUID(), D4, 5)
        ), MAX_GAP);

        assertThat(found).isEmpty();
    }
}
