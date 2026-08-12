package ru.services.constraints;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.constraints.ConstraintKindRef;
import ru.enums.AssessmentWindow;
import ru.enums.ConstraintMode;
import ru.enums.KindOfStudy;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.services.constraints.ConstraintAdmissionRule.Admission;
import static ru.services.constraints.ConstraintAdmissionRule.admits;

/**
 * Юнит-тесты правила «пускает ли ограничение занятие в свою ячейку».
 *
 * <p>Фиксируют домен, разобранный с заказчиком 2026-08-12: экз. сессия жёсткая для всего, кроме
 * запланированных в неё аттестаций; редкий зачёт попадает туда только через явно помеченные
 * свободные дни группы; командировка не пускает ничего и никогда.</p>
 */
class ConstraintAdmissionRuleTest {

    private static ConstraintKindRef kind(ConstraintMode mode) {
        return new ConstraintKindRef("ANY_CODE", "Любой вид", "Вид", 10, mode);
    }

    private static final ConstraintKindRef BLOCKING = kind(ConstraintMode.BLOCKING);
    private static final ConstraintKindRef SESSION_WINDOW = kind(ConstraintMode.ASSESSMENT_WINDOW);
    private static final ConstraintKindRef OPEN_WINDOW = kind(ConstraintMode.ASSESSMENT_WINDOW_OPEN);

    /** Экзамен, запланированный планом в сессию. */
    private static final Admission PLANNED_EXAM =
            Admission.of(AssessmentWindow.SESSION, KindOfStudy.EXAM);
    /** Зачёт, который по плану сдаётся в учебное время (его и двигают в свободные дни). */
    private static final Admission CREDIT_IN_STUDY_TIME =
            Admission.of(AssessmentWindow.STUDY_TIME, KindOfStudy.CREDIT_WITHOUT_GRADE);
    /** Обычное учебное занятие. */
    private static final Admission LECTURE =
            Admission.of(AssessmentWindow.STUDY_TIME, KindOfStudy.LECTURE);
    /** Экзамен по физической подготовке: сессии не требует, стоит в учебном времени. */
    private static final Admission PHYSICAL_EXAM =
            Admission.of(AssessmentWindow.STUDY_TIME, KindOfStudy.EXAM);

    @Test
    @DisplayName("Жёсткое ограничение не пускает ничего — даже плановый экзамен")
    void blockingAdmitsNothing() {
        assertThat(admits(BLOCKING, PLANNED_EXAM)).isFalse();
        assertThat(admits(BLOCKING, CREDIT_IN_STUDY_TIME)).isFalse();
        assertThat(admits(BLOCKING, LECTURE)).isFalse();
    }

    @Test
    @DisplayName("Окно сессии пускает аттестацию, запланированную в сессию")
    void sessionWindowAdmitsPlannedAssessment() {
        assertThat(admits(SESSION_WINDOW, PLANNED_EXAM)).isTrue();
    }

    @Test
    @DisplayName("Окно сессии НЕ пускает учебные занятия — иначе в сессию уедут лекции")
    void sessionWindowRejectsRegularLessons() {
        assertThat(admits(SESSION_WINDOW, LECTURE)).isFalse();
    }

    @Test
    @DisplayName("Окно сессии НЕ пускает зачёт, который планом стоит в учебном времени")
    void sessionWindowRejectsUnplannedCredit() {
        // Ровно тот случай, ради которого заведён режим OPEN: иначе пришлось бы либо открывать
        // сессию всем зачётам разом, либо править план ради одного перемещения.
        assertThat(admits(SESSION_WINDOW, CREDIT_IN_STUDY_TIME)).isFalse();
    }

    @Test
    @DisplayName("Свободные дни пускают внеплановый зачёт")
    void openWindowAdmitsUnplannedCredit() {
        assertThat(admits(OPEN_WINDOW, CREDIT_IN_STUDY_TIME)).isTrue();
    }

    @Test
    @DisplayName("Свободные дни пускают и плановую аттестацию — они шире, а не иные")
    void openWindowAdmitsPlannedAssessmentToo() {
        assertThat(admits(OPEN_WINDOW, PLANNED_EXAM)).isTrue();
    }

    @Test
    @DisplayName("Свободные дни всё равно НЕ пускают учебные занятия")
    void openWindowRejectsRegularLessons() {
        // Смысл свободных дней — отдать их аттестации, а не вернуть в них учебное время.
        assertThat(admits(OPEN_WINDOW, LECTURE)).isFalse();
    }

    @Test
    @DisplayName("Экзамен по физподготовке в сессию не проходит: решает план, а не вид занятия")
    void physicalTrainingExamIsNotAdmittedByKind() {
        // Он EXAM, но планом помечен STUDY_TIME — окно сессии его не пускает. Правило не смотрит
        // на вид занятия, поэтому «экзамен» сам по себе пропуском не является.
        assertThat(admits(SESSION_WINDOW, PHYSICAL_EXAM)).isFalse();
        // А в свободные дни попадёт — он аттестация по категории.
        assertThat(admits(OPEN_WINDOW, PHYSICAL_EXAM)).isTrue();
    }

    @Test
    @DisplayName("Нет ограничения — ячейка свободна для кого угодно")
    void noConstraintAdmitsAnything() {
        assertThat(admits(null, LECTURE)).isTrue();
        assertThat(admits(null, null)).isTrue();
    }

    @Test
    @DisplayName("Занятие не названо — ограничение считается запрещающим (строгость по умолчанию)")
    void unknownLessonIsRejected() {
        // Так работает генерация и любой вызывающий, который спрашивает «занята ли ячейка вообще»:
        // послабление получает только тот, кто явно сказал, что ставит.
        assertThat(admits(SESSION_WINDOW, null)).isFalse();
        assertThat(admits(OPEN_WINDOW, null)).isFalse();
        assertThat(admits(BLOCKING, null)).isFalse();
    }

    @Test
    @DisplayName("Режим не задан (старые данные) читается как жёсткое ограничение")
    void nullModeIsBlocking() {
        ConstraintKindRef legacy = new ConstraintKindRef("OLD", "Старый вид", "Ст", 0, null);
        assertThat(admits(legacy, PLANNED_EXAM)).isFalse();
    }

    @Test
    @DisplayName("Окно плана не задано (старые слоты) читается как «в учебное время»")
    void nullWindowIsStudyTime() {
        Admission legacy = Admission.of(null, KindOfStudy.EXAM);
        assertThat(admits(SESSION_WINDOW, legacy)).isFalse();
    }
}
