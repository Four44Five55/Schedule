package ru.services.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.entity.logicSchema.AuditoriumPool;
import ru.entity.logicSchema.StudyStream;
import ru.enums.TimeSlotPair;
import ru.services.solver.model.AuditoriumResource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты политики подбора аудитории.
 *
 * <p>Раньше эта политика жила цепочкой из четырёх {@code if} внутри {@code ScheduleWorkspace} и
 * не была покрыта ничем. Цена: в живой базе оказалось <b>179</b> ячеек с двойным бронированием и
 * <b>366</b> занятий, не помещающихся в свою комнату (замер 2026-07-17) — потому что
 * {@code isFree} проверяли три ветки из четырёх, а вместимость одна из четырёх.</p>
 *
 * <p>Тесты фиксируют и то, что политика соблюдает, и <b>намеренную асимметрию</b>: занятость —
 * жёсткий отсев, теснота — только порядок.</p>
 */
class AuditoriumSelectorTest {

    private final AuditoriumSelector selector = new AuditoriumSelector();

    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);
    private static final CellForLesson CELL = new CellForLesson(DATE, TimeSlotPair.FIRST);
    private static final CellForLesson OTHER_CELL = new CellForLesson(DATE, TimeSlotPair.SECOND);

    // ===================== helpers =====================

    private static Auditorium room(int id, String name, int capacity) {
        Auditorium a = new Auditorium();
        a.setId(id);
        a.setName(name);
        a.setCapacity(capacity);
        return a;
    }

    private static AuditoriumResource res(Auditorium a) {
        return new AuditoriumResource(a);
    }

    private static Group group(int id, String name, int size, Auditorium base) {
        Group g = new Group();
        g.setId(id);
        g.setName(name);
        g.setSize(size);
        g.setBaseAuditorium(base);
        return g;
    }

    /** Занятие потока из перечисленных групп; требований к комнате нет (основной случай в базе). */
    private static Lesson lesson(Group... groups) {
        StudyStream stream = new StudyStream();
        stream.setId(1);
        stream.setName("поток");
        stream.setGroups(Set.of(groups));

        Lesson l = new Lesson();
        l.setStudyStream(stream);
        return l;
    }

    private static List<String> names(List<AuditoriumResource> rooms) {
        return rooms.stream().map(AuditoriumResource::getName).toList();
    }

    // ===================== Занятость — жёсткий отсев =====================

    @Test
    @DisplayName("Занятая комната не предлагается — даже если она домашняя и единственная подходящая")
    void busyRoomIsNeverOffered() {
        Auditorium home = room(1, "206-3", 30);
        AuditoriumResource homeRes = res(home);
        homeRes.occupy(CELL, new Lesson()); // комнату занял кто-то другой

        Lesson target = lesson(group(10, "46", 22, home));

        // Это и есть баг, из-за которого в базе 102 конфликта: резервная ветка возвращала
        // базовую аудиторию группы, ни разу не спросив isFree.
        assertThat(selector.select(target, CELL, List.of(homeRes))).isEmpty();
    }

    @Test
    @DisplayName("Занятость считается по ячейкам: в другой паре та же комната свободна")
    void busyIsPerCell() {
        Auditorium home = room(1, "206-3", 30);
        AuditoriumResource homeRes = res(home);
        homeRes.occupy(CELL, new Lesson());

        Lesson target = lesson(group(10, "46", 22, home));

        assertThat(names(selector.select(target, OTHER_CELL, List.of(homeRes)))).containsExactly("206-3");
    }

    @Test
    @DisplayName("Занята домашняя — берётся другая свободная, а не двойное бронирование")
    void fallsBackToAnotherFreeRoom() {
        Auditorium home = room(1, "206-3", 30);
        Auditorium spare = room(2, "205-3", 30);
        AuditoriumResource homeRes = res(home);
        homeRes.occupy(CELL, new Lesson());

        Lesson target = lesson(group(10, "46", 22, home));

        assertThat(names(selector.select(target, CELL, List.of(homeRes, res(spare)))))
                .containsExactly("205-3");
    }

    // ===================== Вместимость — порядок, а не отсев =====================

    @Test
    @DisplayName("Поток на 116 не сядет в кабинет на 70, если рядом свободен зал — это баг 366 занятий")
    void streamPrefersRoomThatFits() {
        Auditorium small = room(1, "107-3", 70);   // базовая одной из групп потока
        Auditorium big = room(2, "306-4", 100);

        // Поток 41+43: 58+58 = 116 человек. Раньше резервная ветка брала max(вместимость базовых)
        // = 107-3 (70) и сажала туда 116 человек — перебор на 46, ровно как в живой базе.
        Lesson target = lesson(group(10, "41", 58, small), group(11, "43", 58, null));

        assertThat(names(selector.select(target, CELL, List.of(res(small), res(big)))))
                .containsExactly("306-4", "107-3");
    }

    @Test
    @DisplayName("Тесная комната ОСТАЁТСЯ вариантом: если больше некуда — предлагаем её, а не пустоту")
    void tightRoomIsStillOffered() {
        Auditorium onlyRoom = room(1, "107-3", 70);
        Lesson target = lesson(group(10, "41", 58, null), group(11, "43", 58, null)); // 116 человек

        // Решение заказчика: вместимость мягкая. Жёсткий фильтр оставил бы поток на 116 человек
        // вовсе без комнаты — учебных аудиторий такого размера в базе нет (максимум 100).
        assertThat(names(selector.select(target, CELL, List.of(res(onlyRoom))))).containsExactly("107-3");
    }

    @Test
    @DisplayName("Среди тесных берётся та, где перебор меньше")
    void amongTightRoomsLeastOverflowWins() {
        Auditorium tiny = room(1, "206-3", 30);
        Auditorium less = room(2, "107-3", 70);
        Lesson target = lesson(group(10, "41", 58, tiny), group(11, "43", 58, null)); // 116

        assertThat(names(selector.select(target, CELL, List.of(res(tiny), res(less)))))
                .containsExactly("107-3", "206-3");
    }

    @Test
    @DisplayName("Перебор на одного человека не делает комнату хуже пустого результата (134 занятия в базе)")
    void offByOneIsStillOffered() {
        Auditorium room30 = room(1, "204-3", 30);
        Lesson target = lesson(group(10, "963", 16, room30), group(11, "964", 15, null)); // 31 человек

        assertThat(names(selector.select(target, CELL, List.of(res(room30))))).containsExactly("204-3");
    }

    @Test
    @DisplayName("Из достаточных берётся МЕНЬШАЯ: зал на 400 не занимаем под семинар на 20")
    void smallestSufficientWins() {
        Auditorium hall = room(1, "сп.зал", 400);
        Auditorium seminar = room(2, "205-3", 30);
        Auditorium medium = room(3, "107-3", 70);

        Lesson target = lesson(group(10, "46", 22, null));

        assertThat(names(selector.select(target, CELL, List.of(res(hall), res(seminar), res(medium)))))
                .containsExactly("205-3", "107-3", "сп.зал");
    }

    // ===================== Предпочтения =====================

    @Test
    @DisplayName("Приоритетная из плана выигрывает среди подходящих")
    void priorityRoomWinsAmongFitting() {
        Auditorium priority = room(1, "218-4", 36);
        Auditorium other = room(2, "205-4", 36);

        Lesson target = lesson(group(10, "46", 22, other)); // домашняя — другая
        target.setPriorityAuditorium(priority);

        assertThat(names(selector.select(target, CELL, List.of(res(other), res(priority)))))
                .containsExactly("218-4", "205-4");
    }

    @Test
    @DisplayName("Приоритетная НЕ выигрывает, если мала, а рядом свободна подходящая")
    void priorityLosesToCapacity() {
        Auditorium priority = room(1, "218-4", 36);
        Auditorium roomy = room(2, "107-3", 70);

        Lesson target = lesson(group(10, "41", 58, null)); // 58 человек, в 218-4 не влезут
        target.setPriorityAuditorium(priority);

        // Смена поведения (осознанная): раньше приоритетная бралась безусловно, даже если мала.
        // Осознанно сажать в тесноту, когда рядом свободна подходящая, алгоритм не должен.
        assertThat(names(selector.select(target, CELL, List.of(res(priority), res(roomy)))))
                .containsExactly("107-3", "218-4");
    }

    @Test
    @DisplayName("Домашняя аудитория предпочтительнее чужой при прочих равных")
    void homeRoomPreferred() {
        Auditorium home = room(1, "206-3", 30);
        Auditorium stranger = room(2, "205-3", 30);

        Lesson target = lesson(group(10, "46", 22, home));

        assertThat(names(selector.select(target, CELL, List.of(res(stranger), res(home)))))
                .containsExactly("206-3", "205-3");
    }

    // ===================== Область поиска =====================

    @Test
    @DisplayName("Жёсткое требование сужает область до одной комнаты")
    void requiredRoomNarrowsScope() {
        Auditorium required = room(1, "218-4", 36);
        Auditorium other = room(2, "306-4", 100);

        Lesson target = lesson(group(10, "46", 22, other));
        target.setRequiredAuditorium(required);

        assertThat(names(selector.select(target, CELL, List.of(res(required), res(other)))))
                .containsExactly("218-4");
    }

    @Test
    @DisplayName("Жёсткое требование занято — вариантов нет (подмену не ищем)")
    void requiredRoomBusyMeansNoOptions() {
        Auditorium required = room(1, "218-4", 36);
        AuditoriumResource requiredRes = res(required);
        requiredRes.occupy(CELL, new Lesson());
        Auditorium other = room(2, "306-4", 100);

        Lesson target = lesson(group(10, "46", 22, null));
        target.setRequiredAuditorium(required);

        assertThat(selector.select(target, CELL, List.of(requiredRes, res(other)))).isEmpty();
    }

    @Test
    @DisplayName("Пул сужает область до своих комнат")
    void poolNarrowsScope() {
        Auditorium inPool1 = room(1, "205-3", 30);
        Auditorium inPool2 = room(2, "206-3", 30);
        Auditorium outside = room(3, "306-4", 100);

        AuditoriumPool pool = new AuditoriumPool();
        pool.setId(1);
        pool.setName("Компьютерные");
        pool.setAuditoriums(Set.of(inPool1, inPool2));

        Lesson target = lesson(group(10, "46", 22, outside));
        target.setAllowedAuditoriumPool(pool);

        assertThat(names(selector.select(target, CELL, List.of(res(inPool1), res(inPool2), res(outside)))))
                .containsExactlyInAnyOrder("205-3", "206-3");
    }

    // ===================== Границы =====================

    @Test
    @DisplayName("Группа без базовой аудитории — не падаем, просто нет предпочтения")
    void groupWithoutBaseRoomDoesNotThrow() {
        Auditorium any = room(1, "205-3", 30);
        Lesson target = lesson(group(10, "46", 22, null));

        // Раньше здесь летел IllegalArgumentException прямо посреди подсветки — а вызывающий
        // (MoveLessonSuggestionService) успевал изъять занятие из workspace и не возвращал его.
        assertThat(names(selector.select(target, CELL, List.of(res(any))))).containsExactly("205-3");
    }

    @Test
    @DisplayName("Комнат нет вовсе — пусто, без падений")
    void noRoomsAtAll() {
        assertThat(selector.select(lesson(group(10, "46", 22, null)), CELL, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Все комнаты заняты — пусто (а не «возьми любую»)")
    void allRoomsBusy() {
        AuditoriumResource a = res(room(1, "205-3", 30));
        AuditoriumResource b = res(room(2, "206-3", 30));
        a.occupy(CELL, new Lesson());
        b.occupy(CELL, new Lesson());

        assertThat(selector.select(lesson(group(10, "46", 22, null)), CELL, List.of(a, b))).isEmpty();
    }
}
