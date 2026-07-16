package ru.services.auditorium;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Правило использования аудиторий. Чистая функция — ни Spring, ни БД (как
 * {@link ru.services.order.LessonOrderRule} и {@link ru.services.reindex.TrackReorderStrategy}),
 * поэтому покрывается юнит-тестами.
 *
 * <p>Отвечает на два вопроса про комнату у стоящего занятия:</p>
 * <ol>
 *   <li>{@link AuditoriumFinding.Kind#DOUBLE_BOOKED} — в этой комнате и ячейке стоит кто-то ещё;</li>
 *   <li>{@link AuditoriumFinding.Kind#OVER_CAPACITY} — поток в комнату не помещается.</li>
 * </ol>
 *
 * <p><b>Зачем это отдельное правило, если такие расписания строит сам алгоритм.</b> Занятость
 * в решателе — это {@code Map<ячейка, занятие>} ({@code SchedulableResource}), куда второе
 * занятие просто перезаписывает первое. Конфликт там непредставим, поэтому workspace его не
 * видит <i>по построению</i> и сообщить о нём не может. Пока датчика нет, единственный способ
 * узнать о двойном бронировании — SQL снаружи приложения, то есть на практике никак: расписание
 * с конфликтами выглядит совершенно нормальным. Тот же случай, что был с проекцией до
 * {@code ProjectionHealthService}: сбой оставлял след только в логе, то есть не оставлял.</p>
 *
 * <p><b>Правило только смотрит.</b> Оно ничего не чинит и ни на что не влияет: это шаг 1, датчик.
 * Он же станет измерительным инструментом для последующей починки подбора — «сделали, посмотрели
 * на счётчик» — и источником фактов для интерфейса выбора комнаты (тот же вопрос, другая
 * проекция).</p>
 *
 * <p><b>Чего правило НЕ делает (сознательно).</b> Не проверяет назначение комнаты и оснащение
 * (эти требования есть в {@code CurriculumSlot}, но сейчас не участвуют в подборе нигде, кроме
 * пула — сначала чиним подбор). Не знает про занятия <i>без</i> комнаты: такое занятие сюда
 * просто не попадёт, это отдельный симптом с отдельной причиной (аудиторию удалили). Не
 * смотрит на постоянные ограничения комнаты (ремонт): их разворот живёт в
 * {@code ConstraintServiceImpl} и это вход для следующего шага.</p>
 */
public class AuditoriumUsageRule {

    /**
     * Находки по всем переданным занятиям.
     *
     * <p>Занятия должны быть из ОДНОЙ сессии: разные сессии (архив, соседний период) делят
     * комнаты законно, и сравнивать их между собой бессмысленно. Скоуп — на сборочном слое.</p>
     *
     * @param lessons размещённые занятия, развёрнутые по комнатам
     * @return находки; у одного занятия их может быть две (комната и занята, и мала)
     */
    public List<AuditoriumFinding> check(List<RoomedLesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return List.of();
        }

        List<AuditoriumFinding> findings = new ArrayList<>();
        findings.addAll(doubleBookings(lessons));
        findings.addAll(overCapacity(lessons));
        return List.copyOf(findings);
    }

    /**
     * Двойное бронирование: в одной комнате и ячейке больше одного занятия.
     *
     * <p>Находка выписывается КАЖДОМУ участнику, а не «первому лишнему»: кто из них лишний —
     * решение, а правило сообщает факт. Диспетчер увидит проблему, с какой бы стороны он на
     * неё ни смотрел (расписание группы, преподавателя или самой аудитории).</p>
     */
    private static List<AuditoriumFinding> doubleBookings(List<RoomedLesson> lessons) {
        Map<RoomCell, List<RoomedLesson>> byRoomCell = new LinkedHashMap<>();
        for (RoomedLesson lesson : lessons) {
            RoomCell key = new RoomCell(lesson.auditoriumId(), lesson.date(), lesson.slot());
            byRoomCell.computeIfAbsent(key, k -> new ArrayList<>()).add(lesson);
        }

        List<AuditoriumFinding> findings = new ArrayList<>();
        for (List<RoomedLesson> sharing : byRoomCell.values()) {
            if (sharing.size() < 2) {
                continue;
            }
            for (RoomedLesson lesson : sharing) {
                Set<UUID> others = new LinkedHashSet<>();
                for (RoomedLesson other : sharing) {
                    if (!other.placementId().equals(lesson.placementId())) {
                        others.add(other.placementId());
                    }
                }
                findings.add(new AuditoriumFinding(
                        lesson.placementId(), lesson.auditoriumId(), lesson.date(), lesson.slot(),
                        AuditoriumFinding.Kind.DOUBLE_BOOKED, 0, Set.copyOf(others)));
            }
        }
        return findings;
    }

    /**
     * Переполнение: людей больше, чем мест.
     *
     * <p>Ровно по местам ({@code headcount == capacity}) — не находка: комната рассчитана на
     * столько людей. Порога «сколько перебора допустимо» здесь нет намеренно — это политика,
     * а не факт; правило отдаёт {@code excess} числом, а решает вызывающий.</p>
     */
    private static List<AuditoriumFinding> overCapacity(List<RoomedLesson> lessons) {
        List<AuditoriumFinding> findings = new ArrayList<>();
        for (RoomedLesson lesson : lessons) {
            int excess = lesson.headcount() - lesson.capacity();
            if (excess > 0) {
                findings.add(new AuditoriumFinding(
                        lesson.placementId(), lesson.auditoriumId(), lesson.date(), lesson.slot(),
                        AuditoriumFinding.Kind.OVER_CAPACITY, excess, Set.of()));
            }
        }
        return findings;
    }

    /** Ключ группировки: комната в конкретной ячейке. */
    private record RoomCell(Integer auditoriumId, LocalDate date, TimeSlotPair slot) {
    }
}
