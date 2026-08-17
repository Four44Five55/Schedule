package ru.services.importing;

import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.List;

/**
 * Что импорт сделает с учебным планом — <b>до того, как что-либо создаст</b>.
 *
 * <p>Порядок «сначала отчёт, потом запись» тут жёстче, чем со справочниками (И-10): заводится не
 * одна строка, а четыре уровня — курс, тема, слот, назначение, — и каждая из них потом окажется под
 * ссылкой размещения, то есть станет неудаляемой.</p>
 *
 * <p><b>План восстановленный — слепок, а не норма</b> (следствие 3 к И-18): он описывает, как
 * занятия прошли, а не как задумывались. Поэтому «Кол-во часов» из подвала сюда не пишется, а
 * сверяется отдельно.</p>
 *
 * @param lessons             сведённых занятий на входе
 * @param resolved            сколько из них доходит до назначения
 * @param coursesToCreate     курсов ({@code discipline_course}) будет заведено
 * @param themesToCreate      тем ({@code theme_lesson})
 * @param slotsToCreate       слотов плана ({@code curriculum_slot})
 * @param assignmentsToCreate назначений ({@code assignment} + преподаватели)
 * @param coursesExisting     курсов уже есть — на них ляжем
 * @param slotsExisting       слотов уже есть
 * @param assignmentsExisting назначений уже есть
 * @param positionsByTheme    позиций слота выведено из номера темы (И-20)
 * @param positionsByOrder    позиций выведено хронологией — там, где темы в ячейке нет
 * @param blockers            почему занятия не разрешились, схлопнуто по тексту
 * @param sample              первые разрешённые занятия «на глаз»
 */
public record PlanReport(
        int lessons,
        int resolved,
        int coursesToCreate,
        int themesToCreate,
        int slotsToCreate,
        int assignmentsToCreate,
        int coursesExisting,
        int slotsExisting,
        int assignmentsExisting,
        int positionsByTheme,
        int positionsByOrder,
        List<MergeReport.Finding> blockers,
        List<PlanRow> sample
) {

    /**
     * Разрешённое занятие: во что именно оно превратится.
     *
     * @param date       дата занятия
     * @param slot       пара
     * @param discipline дисциплина, как она называется у нас
     * @param semester   семестр курса — вычислен из года набора и года периода (И-18)
     * @param kind       наш вид занятия
     * @param position   позиция слота в плане
     * @param theme      номер темы либо {@code null} (позиция тогда выведена хронологией)
     * @param stream     поток (состав групп)
     * @param educators  преподаватели назначения
     * @param newCourse  курс будет заведён импортом
     * @param newSlot    слот будет заведён импортом
     */
    public record PlanRow(
            LocalDate date,
            TimeSlotPair slot,
            String discipline,
            int semester,
            KindOfStudy kind,
            int position,
            String theme,
            String stream,
            List<String> educators,
            boolean newCourse,
            boolean newSlot
    ) {
    }
}
