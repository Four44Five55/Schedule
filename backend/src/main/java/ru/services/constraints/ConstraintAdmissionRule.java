package ru.services.constraints;

import ru.abstracts.AbstractLesson;
import ru.entity.constraints.ConstraintKindRef;
import ru.enums.AssessmentWindow;
import ru.enums.ConstraintMode;
import ru.enums.KindOfStudy;

/**
 * Пускает ли ограничение занятие в свою ячейку.
 *
 * <p>Чистая функция без Spring и БД — как {@code LessonOrderRule}, {@code AuditoriumUsageRule} и
 * {@code OrgUnitHierarchyRule}. Вся семантика «жёсткое / окно сессии / окно со свободными днями»
 * живёт здесь одна, и покрыта быстрыми тестами.</p>
 *
 * <p><b>Ни одного кода вида ограничения в коде.</b> Правило смотрит на {@link ConstraintMode} —
 * атрибут, чей набор значений принадлежит релизу, — а не на то, называется ли вид «Экзаменационная
 * сессия». Пользователь заведёт «Сессия зимняя 2027», выберет тот же режим, и правило заработает
 * без единой правки. Симметрично со стороны занятия смотрим на {@link AssessmentWindow} слота и на
 * {@link KindOfStudy.Category}, а не на конкретный вид: «экзамены да, зачёты нет» категорией не
 * выражается (все три — {@code ASSESSMENT}), поэтому различает их именно план.</p>
 *
 * <p><b>Кто это спрашивает.</b> Только ручные пути (установка из палитры, перенос, подсказка
 * «куда можно»). Генерация продолжает пользоваться строгой проверкой и о послаблениях не знает
 * вовсе — иначе {@link ConstraintMode#ASSESSMENT_WINDOW_OPEN} открыл бы свободные дни группы и для
 * автоматической раскладки зачётов. Это свойство шва, а не дисциплина вызывающих: путь, который не
 * передаёт {@link Admission}, послаблением воспользоваться не может.</p>
 */
public final class ConstraintAdmissionRule {

    private ConstraintAdmissionRule() {
    }

    /**
     * Занятие с точки зрения правила: где оно сдаётся по плану и к какой категории относится.
     *
     * <p>Отдельная запись, а не {@code Lesson}: правилу не нужна JPA-сущность с ленивыми связями,
     * а тесту не нужен контекст. Ровно тот же приём, что {@code UnitNode} при обходе дерева
     * подразделений.</p>
     *
     * @param window   норма плана: {@code SESSION} — аттестация запланирована в сессию
     * @param category категория вида занятия; для внепланового допуска важна только
     *                 {@link KindOfStudy.Category#ASSESSMENT}
     */
    public record Admission(AssessmentWindow window, KindOfStudy.Category category) {

        /** Занятие solver'а → вход правила. {@code null} у окна читается как «в учебное время». */
        public static Admission of(AssessmentWindow window, KindOfStudy kind) {
            return new Admission(
                    window == null ? AssessmentWindow.STUDY_TIME : window,
                    kind == null ? null : kind.getCategory());
        }

        /**
         * Единственная сборка входа из занятия — чтобы проверка при постановке и подсказка «куда
         * можно» не разошлись. Дублировать эти две строки по вызывающим нельзя: расхождение
         * проявится как зелёная ячейка, дающая 409 (аудит §1.1 — «правило общее, сборка входа нет»).
         */
        public static Admission of(AbstractLesson lesson) {
            return lesson == null ? null : of(lesson.getAssessmentWindow(), lesson.getKindOfStudy());
        }
    }

    /**
     * @param constraint вид ограничения, занявший ячейку (снимок для домена)
     * @param admission  занятие, которое пытаются поставить
     * @return {@code true}, если ограничение не мешает этому занятию встать в свою ячейку
     */
    public static boolean admits(ConstraintKindRef constraint, Admission admission) {
        // Ограничения нет — решать нечего. Ветка нужна, чтобы вызывающему не приходилось
        // проверять null перед каждым обращением: «свободно» — законный ответ на «нет ограничения».
        if (constraint == null) {
            return true;
        }
        // Занятие неизвестно (проверяем «занята ли ячейка вообще») — ограничение считается
        // запрещающим. Строгость по умолчанию: послабление даётся только тому, кто назвал себя.
        if (admission == null) {
            return false;
        }
        ConstraintMode mode = constraint.mode() == null ? ConstraintMode.BLOCKING : constraint.mode();
        return switch (mode) {
            case BLOCKING -> false;
            case ASSESSMENT_WINDOW -> admission.window() == AssessmentWindow.SESSION;
            // Внеплановую аттестацию впускаем по КАТЕГОРИИ: диспетчер двигает в свободные дни
            // зачёт, который планом стоял в учебном времени, — его слот помечен STUDY_TIME, и по
            // норме плана он сюда не проходит. Учебные занятия не проходят и здесь.
            case ASSESSMENT_WINDOW_OPEN -> admission.window() == AssessmentWindow.SESSION
                    || admission.category() == KindOfStudy.Category.ASSESSMENT;
        };
    }
}
