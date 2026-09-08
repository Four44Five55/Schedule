package ru.services.auditorium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.dto.auditorium.AuditoriumViolationDto;
import ru.entity.Assignment;
import ru.entity.Auditorium;
import ru.entity.Group;
import ru.entity.logicSchema.StudyStream;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.TimeSlotPair;
import ru.repository.write.LessonPlacementRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты сборочного слоя находок по аудиториям.
 *
 * <p>Зафиксировано то, чего правило не умеет по построению: <b>занятие без комнаты — тоже находка</b>.
 * Правило отвечает на вопросы об использовании комнаты, а тут комнаты нет; но молчать нельзя —
 * занятие где-то идёт, а где, неизвестно. У импортированного расписания это массовое состояние.</p>
 */
class AuditoriumViolationServiceTest {

    private static final LocalDate DAY = LocalDate.of(2025, 9, 1);

    private final LessonPlacementRepository placements = mock(LessonPlacementRepository.class);
    private final AuditoriumViolationService service = new AuditoriumViolationService(placements);

    private static LessonPlacement placement(Set<Auditorium> rooms) {
        Group group = new Group();
        group.setName("911");
        group.setSize(25);
        StudyStream stream = new StudyStream("911", 1);
        stream.setGroups(Set.of(group));
        Assignment assignment = new Assignment();
        assignment.setId(1);
        assignment.setStudyStream(stream);

        LessonPlacement placement = new LessonPlacement(assignment, DAY, TimeSlotPair.FIRST,
                new ScheduleSession("s", "user"), "user");
        placement.getAssignedAuditoriums().addAll(rooms);
        return placement;
    }

    private static Auditorium room(int id, String name, int capacity) {
        Auditorium auditorium = new Auditorium();
        auditorium.setId(id);
        auditorium.setName(name);
        auditorium.setCapacity(capacity);
        return auditorium;
    }

    @Test
    @DisplayName("Занятие без комнаты даёт находку NO_AUDITORIUM, а не молчание")
    void placementWithoutRoomIsReported() {
        when(placements.findBySessionId(any())).thenReturn(List.of(placement(Set.of())));

        List<AuditoriumViolationDto> found = service.violationsOf(UUID.randomUUID());

        assertThat(found).singleElement().satisfies(violation -> {
            assertThat(violation.kind()).isEqualTo("NO_AUDITORIUM");
            // Комнаты нет — подставлять сюда нечего.
            assertThat(violation.auditoriumId()).isNull();
            assertThat(violation.auditoriumName()).isNull();
            assertThat(violation.excess()).isZero();
        });
    }

    @Test
    @DisplayName("Занятие с комнатой без нарушений находок не даёт")
    void healthyPlacementGivesNothing() {
        when(placements.findBySessionId(any()))
                .thenReturn(List.of(placement(Set.of(room(1, "252", 30)))));

        assertThat(service.violationsOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Безкомнатное соседствует с обычными находками, а не вытесняет их")
    void roomlessAndCapacityFindingsLiveTogether() {
        // Комната на 10 мест против потока в 25 человек — перебор; плюс занятие вовсе без комнаты.
        when(placements.findBySessionId(any())).thenReturn(List.of(
                placement(Set.of(room(1, "252", 10))),
                placement(Set.of())));

        assertThat(service.violationsOf(UUID.randomUUID()))
                .extracting(AuditoriumViolationDto::kind)
                .containsExactlyInAnyOrder("OVER_CAPACITY", "NO_AUDITORIUM");
    }
}
