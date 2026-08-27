package ru.services.solver.model;

import ru.entity.CellForLesson;
import ru.enums.TimeSlotPair;
import ru.services.ScheduleDaysSlotsConfig;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Учебный календарь периода: <b>какие ячейки (дата · пара) в нём существуют</b>.
 *
 * <p>Неизменяемое значение, вычисляемое от трёх вещей: начала периода, конца периода и политики
 * доступных пар. Ни Spring, ни БД, ни статики — поэтому календарь создаётся столько раз, сколько
 * нужно, и два календаря разных периодов спокойно живут рядом.</p>
 *
 * <p><b>Зачем он появился (2026-08-27).</b> Раньше на этот вопрос отвечал
 * {@code CellForLessonFactory} — статическая карта на всю JVM, которую <b>очищал и перезаполнял
 * каждый</b> запрос, пересоздающий workspace (подсветка, перенос, ручная раскладка, генерация).
 * То есть «какие ячейки бывают» и, что хуже, «принадлежит ли слот планируемому периоду» зависели
 * от того, кто вызвал последним. Один пользователь этого не замечал; для веб-приложения с
 * несколькими вкладками, сессиями разных периодов и несколькими инстансами это три тихих отказа
 * сразу:</p>
 * <ul>
 *   <li>ложное «выбранный слот вне планируемого периода» на законном слоте
 *       ({@code LessonMoveService}, {@code ManualPlacementService}, {@code LessonChainMoveService}
 *       спрашивали границы у глобала, а не у своей сессии);</li>
 *   <li>пустой либо чужой набор вариантов переноса ({@code MoveLessonSuggestionService}) — то есть
 *       утверждение «переносить некуда», сделанное по чужой сетке;</li>
 *   <li>сетка генерации, построенная по периоду соседнего запроса ({@code ScheduleGrid} принимал
 *       даты в конструктор и <b>не использовал их</b>, заполняясь из глобала).</li>
 * </ul>
 *
 * <p><b>Почему значение, а не бин с кэшем.</b> {@link CellForLesson} — неизменяемое значение с
 * честными {@code equals}/{@code hashCode}, ячеек в семестре порядка пятисот, и стоят они одного
 * прохода по датам. Общий кэш экономил бы вызовы {@code new} ценой разделяемого состояния — размен,
 * который и привёл к трём отказам выше. Владелец календаря — {@code ScheduleWorkspace}: он и так
 * знает свой период, и он есть у каждого, кто раньше ходил в глобал.</p>
 *
 * <p><b>Порядок — часть контракта</b> (и закреплён тестом): {@link #cells()} идут по датам
 * возрастающе, внутри дня — в порядке {@link TimeSlotPair}. На него опирается поиск места под
 * цепочку скользящим окном; раньше каждый вызывающий пересортировывал список сам, надеясь, что
 * фабрика вернула изменяемую копию.</p>
 */
public final class AcademicCalendar {

    /**
     * Политика доступных пар: «бывает ли такая пара в этот день».
     *
     * <p>Вынесена в параметр (Strategy), а не зашита вызовом: сегодня правило одно на всю систему
     * ({@link ScheduleDaysSlotsConfig} — воскресенье не учебное, суббота без 4-й пары), но оно
     * доменное и уже названо кандидатом на пересмотр «не через {@code ScheduleDaysSlotsConfig}»
     * (см. NORTH_STAR §2 про Пт-4). Когда сетка станет свойством периода или факультета, менять
     * придётся <b>сборку календаря</b>, а не двенадцать мест, где раньше стоял статический вызов.</p>
     */
    @FunctionalInterface
    public interface SlotPolicy {

        /** Умолчание системы: правила {@link ScheduleDaysSlotsConfig}. */
        SlotPolicy DEFAULT = ScheduleDaysSlotsConfig::isSlotAvailable;

        boolean isAvailable(LocalDate date, TimeSlotPair slot);
    }

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<CellForLesson> cells;
    private final List<LocalDate> dates;
    private final Map<LocalDate, List<CellForLesson>> byDate;

    private AcademicCalendar(LocalDate startDate, LocalDate endDate, SlotPolicy policy) {
        this.startDate = startDate;
        this.endDate = endDate;

        List<CellForLesson> allCells = new ArrayList<>();
        List<LocalDate> allDates = new ArrayList<>();
        Map<LocalDate, List<CellForLesson>> cellsByDate = new LinkedHashMap<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<CellForLesson> dayCells = new ArrayList<>(TimeSlotPair.values().length);
            for (TimeSlotPair slot : TimeSlotPair.values()) { // порядок enum'а = порядок пар в дне
                if (policy.isAvailable(date, slot)) {
                    dayCells.add(new CellForLesson(date, slot));
                }
            }
            if (dayCells.isEmpty()) {
                continue; // выходной: дня без единой пары в календаре нет вовсе
            }
            List<CellForLesson> frozen = Collections.unmodifiableList(dayCells);
            cellsByDate.put(date, frozen);
            allDates.add(date);
            allCells.addAll(frozen);
        }

        this.cells = Collections.unmodifiableList(allCells);
        this.dates = Collections.unmodifiableList(allDates);
        this.byDate = Collections.unmodifiableMap(cellsByDate);
    }

    /**
     * Календарь периода по умолчанию системы.
     *
     * @param startDate начало периода (включительно)
     * @param endDate   конец периода (включительно); раньше начала — календарь пуст, а не исключение
     */
    public static AcademicCalendar of(LocalDate startDate, LocalDate endDate) {
        return of(startDate, endDate, SlotPolicy.DEFAULT);
    }

    /**
     * То же, но с явной политикой пар — для периодов с иной сеткой и для тестов, которым нужна
     * предсказуемая сетка без правил производственного конфига.
     *
     * <p><b>Пустой период — законный ответ, а не отказ.</b> {@code endDate} раньше
     * {@code startDate} даёт календарь без ячеек: вызывающий (пустая сессия, период без дат)
     * получит «размещать некуда», и это правда, тогда как исключение из конструктора значения
     * уронило бы запрос на ровном месте.</p>
     */
    public static AcademicCalendar of(LocalDate startDate, LocalDate endDate, SlotPolicy policy) {
        if (startDate == null || endDate == null || policy == null) {
            throw new IllegalArgumentException("Календарь периода строится по датам и политике пар");
        }
        return new AcademicCalendar(startDate, endDate, policy);
    }

    /** Все ячейки периода: по датам возрастающе, внутри дня — в порядке {@link TimeSlotPair}. */
    public List<CellForLesson> cells() {
        return cells;
    }

    /** Учебные даты периода по возрастанию: дни без единой доступной пары сюда не попадают. */
    public List<LocalDate> dates() {
        return dates;
    }

    /** Ячейки одного дня в порядке пар; день вне периода (или выходной) — пустой список. */
    public List<CellForLesson> cellsOn(LocalDate date) {
        return byDate.getOrDefault(date, List.of());
    }

    /**
     * Ячейка периода, если она в нём есть.
     *
     * <p>Это и есть проверка «слот принадлежит планируемому периоду» — та самая, что раньше
     * держалась на {@code null} из глобальной статики. Ответ {@link Optional#empty()} означает
     * ровно одно: у <b>этого</b> периода такой ячейки нет.</p>
     */
    public Optional<CellForLesson> cellAt(LocalDate date, TimeSlotPair slot) {
        if (date == null || slot == null) {
            return Optional.empty();
        }
        return cellsOn(date).stream().filter(cell -> cell.getTimeSlotPair() == slot).findFirst();
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }
}
