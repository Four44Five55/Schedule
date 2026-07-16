package ru.services.auditorium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты правила использования аудиторий: «комната занята дважды» (физика) и «поток не
 * влезает» (суждение).
 *
 * <p>Оба состояния сейчас реально существуют в живой базе (замер 2026-07-16: 179 конфликтов на
 * трёх комнатах, 646 переполнений с перебором до 88 человек), и ни одно из них система не
 * показывает. Тесты фиксируют, что именно датчик обязан поймать — и что он сознательно
 * оставляет следующим шагам.</p>
 */
class AuditoriumUsageRuleTest {

    private final AuditoriumUsageRule rule = new AuditoriumUsageRule();

    private static final LocalDate D1 = LocalDate.of(2026, 10, 23);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 24);

    private static final int ROOM_A = 109; // 218-4 в живой базе
    private static final int ROOM_B = 102;

    /** Занятие, которое влезает в комнату: 20 человек на 30 мест. */
    private static RoomedLesson lesson(UUID id, LocalDate date, TimeSlotPair slot, int roomId) {
        return new RoomedLesson(id, date, slot, roomId, 30, 20);
    }

    private static RoomedLesson lesson(UUID id, LocalDate date, TimeSlotPair slot, int roomId,
                                       int capacity, int headcount) {
        return new RoomedLesson(id, date, slot, roomId, capacity, headcount);
    }

    // ===================== Двойное бронирование =====================

    @Test
    @DisplayName("Два занятия в одной комнате и ячейке — находка у КАЖДОГО, со ссылкой на соседа")
    void twoLessonsShareRoomAndCell() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(a, D1, TimeSlotPair.FIRST, ROOM_A),
                lesson(b, D1, TimeSlotPair.FIRST, ROOM_A)
        ));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(f -> f.kind() == AuditoriumFinding.Kind.DOUBLE_BOOKED);
        assertThat(found).allMatch(f -> f.auditoriumId().equals(ROOM_A));

        AuditoriumFinding forA = found.stream().filter(f -> f.placementId().equals(a)).findFirst().orElseThrow();
        AuditoriumFinding forB = found.stream().filter(f -> f.placementId().equals(b)).findFirst().orElseThrow();
        assertThat(forA.sharedWith()).containsExactly(b);
        assertThat(forB.sharedWith()).containsExactly(a);
        assertThat(forA.excess()).isZero();
    }

    @Test
    @DisplayName("Та же комната, но разные ячейки — не находка")
    void sameRoomDifferentCells() {
        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A),
                lesson(UUID.randomUUID(), D2, TimeSlotPair.FIRST, ROOM_A),
                lesson(UUID.randomUUID(), D1, TimeSlotPair.SECOND, ROOM_A)
        ));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Та же ячейка, но разные комнаты — не находка")
    void sameCellDifferentRooms() {
        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A),
                lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_B)
        ));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Трое в одной комнате — каждый видит двух других")
    void threeLessonsShareRoomAndCell() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(a, D1, TimeSlotPair.FIRST, ROOM_A),
                lesson(b, D1, TimeSlotPair.FIRST, ROOM_A),
                lesson(c, D1, TimeSlotPair.FIRST, ROOM_A)
        ));

        assertThat(found).hasSize(3);
        assertThat(found).allMatch(f -> f.sharedWith().size() == 2);
        AuditoriumFinding forA = found.stream().filter(f -> f.placementId().equals(a)).findFirst().orElseThrow();
        assertThat(forA.sharedWith()).containsExactlyInAnyOrder(b, c);
    }

    // ===================== Переполнение =====================

    @Test
    @DisplayName("Людей больше, чем мест — находка с числом лишних")
    void streamDoesNotFit() {
        UUID a = UUID.randomUUID();

        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(a, D1, TimeSlotPair.FIRST, ROOM_A, 36, 38)
        ));

        assertThat(found).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(AuditoriumFinding.Kind.OVER_CAPACITY);
            assertThat(f.excess()).isEqualTo(2);
            assertThat(f.sharedWith()).isEmpty();
        });
    }

    @Test
    @DisplayName("Ровно по местам — не находка: комната на столько и рассчитана")
    void exactlyAtCapacityIsFine() {
        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A, 30, 30)
        ));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Порога «допустимого перебора» в правиле нет: +2 и +88 — одинаково находки, разной величины")
    void ruleReportsExcessWithoutJudgingIt() {
        UUID small = UUID.randomUUID();
        UUID huge = UUID.randomUUID();

        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(small, D1, TimeSlotPair.FIRST, ROOM_A, 30, 32),
                lesson(huge, D2, TimeSlotPair.FIRST, ROOM_B, 30, 118)
        ));

        // Правило отдаёт факт числом. Где проходит граница «терпимо / недопустимо» — политика
        // вызывающего: диспетчер сажает +2 не задумываясь, +88 не сажает никогда.
        assertThat(found).hasSize(2);
        assertThat(found).extracting(AuditoriumFinding::excess).containsExactlyInAnyOrder(2, 88);
    }

    @Test
    @DisplayName("Поток без групп (0 человек) — не находка")
    void emptyStreamFits() {
        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A, 30, 0)
        ));

        assertThat(found).isEmpty();
    }

    // ===================== Две находки на одном занятии =====================

    @Test
    @DisplayName("Комната и занята, и мала — ДВЕ находки: причины независимы и чинятся по-разному")
    void bothFindingsOnSameLesson() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(a, D1, TimeSlotPair.FIRST, ROOM_A, 30, 45),
                lesson(b, D1, TimeSlotPair.FIRST, ROOM_A, 30, 20)
        ));

        assertThat(found).hasSize(3);
        assertThat(found).filteredOn(f -> f.placementId().equals(a))
                .extracting(AuditoriumFinding::kind)
                .containsExactlyInAnyOrder(
                        AuditoriumFinding.Kind.DOUBLE_BOOKED,
                        AuditoriumFinding.Kind.OVER_CAPACITY);
        assertThat(found).filteredOn(f -> f.placementId().equals(b))
                .extracting(AuditoriumFinding::kind)
                .containsExactly(AuditoriumFinding.Kind.DOUBLE_BOOKED);
    }

    @Test
    @DisplayName("Одно занятие в двух комнатах: каждая судится отдельно")
    void lessonInTwoRoomsJudgedPerRoom() {
        UUID a = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        // Одно и то же размещение занимает две комнаты; вторая делится с чужим занятием.
        List<AuditoriumFinding> found = rule.check(List.of(
                new RoomedLesson(a, D1, TimeSlotPair.FIRST, ROOM_A, 30, 20),
                new RoomedLesson(a, D1, TimeSlotPair.FIRST, ROOM_B, 30, 20),
                lesson(other, D1, TimeSlotPair.FIRST, ROOM_B)
        ));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(f -> f.auditoriumId().equals(ROOM_B));
        assertThat(found).allMatch(f -> f.kind() == AuditoriumFinding.Kind.DOUBLE_BOOKED);
    }

    // ===================== Границы =====================

    @Test
    @DisplayName("Пустой вход и null — пусто, без падений")
    void emptyInput() {
        assertThat(rule.check(List.of())).isEmpty();
        assertThat(rule.check(null)).isEmpty();
    }

    @Test
    @DisplayName("Одно занятие — сравнивать не с чем")
    void singleLesson() {
        assertThat(rule.check(List.of(lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A)))).isEmpty();
    }

    @Test
    @DisplayName("Чистое расписание — ни одной находки")
    void cleanScheduleProducesNothing() {
        List<AuditoriumFinding> found = rule.check(List.of(
                lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A),
                lesson(UUID.randomUUID(), D1, TimeSlotPair.SECOND, ROOM_A),
                lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_B),
                lesson(UUID.randomUUID(), D2, TimeSlotPair.FIRST, ROOM_A)
        ));

        assertThat(found).isEmpty();
    }

    // ===================== Зафиксированные пределы датчика =====================

    @Test
    @DisplayName("ПРЕДЕЛ: правило не знает про назначение комнаты и оснащение")
    void knownLimitation_purposeAndFeaturesNotChecked() {
        // Компьютерный класс под лекцию, лекционная под лабораторную — правило промолчит.
        // Требования есть в CurriculumSlot, но в подборе сейчас не участвуют нигде, кроме пула;
        // включать их в датчик раньше, чем в подбор, — значит показывать нарушения правила,
        // которого система не соблюдает. Сначала подбор (шаг 3), потом сюда.
        assertThat(rule.check(List.of(lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A)))).isEmpty();
    }

    @Test
    @DisplayName("ПРЕДЕЛ: правило не видит занятий БЕЗ комнаты")
    void knownLimitation_lessonsWithoutRoomAreInvisible() {
        // Занятие без аудитории (комнату удалили — каскад уносит связь молча) сюда просто не
        // попадёт: вход — пары «размещение × комната». Это отдельный симптом с отдельной
        // причиной, и считать его должен сборочный слой, а не это правило.
        assertThat(rule.check(List.of())).isEmpty();
    }

    @Test
    @DisplayName("ПРЕДЕЛ: правило не смотрит постоянные ограничения комнаты (ремонт)")
    void knownLimitation_constraintsNotChecked() {
        // Занятие в комнате, закрытой на ремонт, находкой не станет: разворот ограничений в
        // ячейки живёт в ConstraintServiceImpl и в чистую функцию не передан. Вход для шага 2,
        // когда у комнаты появится свой ресурс (AuditoriumResource) с её ограничениями.
        assertThat(rule.check(List.of(lesson(UUID.randomUUID(), D1, TimeSlotPair.FIRST, ROOM_A)))).isEmpty();
    }
}
