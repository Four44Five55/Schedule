package ru.services.importing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.Assignment;
import ru.entity.Auditorium;
import ru.entity.Building;
import ru.entity.Location;
import ru.entity.StudyPeriod;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.PeriodType;
import ru.enums.PlacementSource;
import ru.enums.TimeSlotPair;
import ru.repository.AuditoriumRepository;
import ru.repository.StudyPeriodRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;
import ru.services.ScheduleSynchronizer;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ImportPlanService.PlanResolution;
import ru.services.importing.ImportPlanService.ResolvedLesson;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.session.ScheduleSessionGate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты записи импортированного расписания.
 *
 * <p>Зафиксировано главное: <b>размещение пишется закреплённым и с {@code source = IMPORTED}</b>,
 * занятие вне периода не размещается (И-8), комната берётся из файла, а не подбирается, и проекция
 * идёт только по явному требованию (И-11). База подменена заглушками — сервис пишет пачками и не
 * создаёт workspace вовсе.</p>
 */
class ImportScheduleServiceTest {

    private static final LocalDate DAY = LocalDate.of(2025, 9, 1);

    private final ImportPlanService plan = mock(ImportPlanService.class);
    private final StudyPeriodRepository periods = mock(StudyPeriodRepository.class);
    private final AuditoriumRepository auditoriums = mock(AuditoriumRepository.class);
    private final ScheduleSessionRepository sessions = mock(ScheduleSessionRepository.class);
    private final LessonPlacementRepository placements = mock(LessonPlacementRepository.class);
    private final ScheduleSessionGate gate = mock(ScheduleSessionGate.class);
    private final ScheduleSynchronizer synchronizer = mock(ScheduleSynchronizer.class);

    private final ImportScheduleService service = new ImportScheduleService(
            plan, periods, auditoriums, sessions, placements, gate, synchronizer);

    private StudyPeriod period;

    @BeforeEach
    void setUp() {
        period = new StudyPeriod("Осень 2025/2026", 2025, PeriodType.FALL_SEMESTER, DAY, DAY.plusMonths(4));
        when(periods.findById(any())).thenReturn(Optional.of(period));
        when(auditoriums.findAll()).thenReturn(List.of(room("252", "3", 7)));
        // Сессия сохраняется как есть — id ей выдаёт конструктор.
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(gate.forWrite(any(), any())).thenAnswer(call -> savedSession());
    }

    private ScheduleSession saved;

    private ScheduleSession savedSession() {
        return saved;
    }

    private static Auditorium room(String name, String building, int locationId) {
        Location location = new Location();
        location.setId(locationId);
        Building host = new Building();
        host.setName(building);
        host.setLocation(location);
        Auditorium auditorium = new Auditorium();
        auditorium.setId(1);
        auditorium.setName(name);
        auditorium.setBuilding(host);
        return auditorium;
    }

    /** Занятие, доведённое до назначения: вход шага записи. */
    private static ResolvedLesson lesson(LocalDate date, List<String> rooms, boolean outsidePeriod) {
        MergedLesson merged = new MergedLesson(date, TimeSlotPair.FIRST, "АСКС", "Автоматизированные системы",
                List.of("911"), rooms, "Л", "Т.1", List.of("Ветров Р.И."),
                Set.of(CutKind.GROUP), List.of("911.html"), outsidePeriod);
        Assignment assignment = new Assignment();
        assignment.setId(42);
        return new ResolvedLesson(merged, assignment);
    }

    private void resolves(ResolvedLesson... lessons) {
        PlanReport report = new PlanReport(lessons.length, lessons.length, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of());
        when(plan.createForSchedule(any(), any(), any()))
                .thenReturn(new PlanResolution(report, List.of(lessons)));
    }

    @SuppressWarnings("unchecked")
    private List<LessonPlacement> written() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(placements).saveAll(captor.capture());
        return captor.getValue();
    }

    private ScheduleWriteReport write(boolean project) {
        // Сессию перехватываем: дверь отдаёт её же, а размещения на неё ссылаются.
        when(sessions.save(any())).thenAnswer(call -> {
            saved = call.getArgument(0);
            return saved;
        });
        return service.write(List.of(), 1, 7, SuffixStyle.SLASH, project, "import");
    }

    @Test
    @DisplayName("Размещение пишется закреплённым и импортным — иначе первая перегенерация его снесёт")
    void placementIsLockedAndImported() {
        resolves(lesson(DAY, List.of("252-3"), false));

        ScheduleWriteReport report = write(false);

        assertThat(report.placements()).isEqualTo(1);
        assertThat(written()).singleElement().satisfies(placement -> {
            assertThat(placement.getSource()).isEqualTo(PlacementSource.IMPORTED);
            // regenerateKeepingLocked смотрит на locked, а НЕ на source.
            assertThat(placement.isLocked()).isTrue();
            assertThat(placement.getScheduledDate()).isEqualTo(DAY);
            assertThat(placement.getScheduledSlot()).isEqualTo(TimeSlotPair.FIRST);
            assertThat(placement.getAssignedAuditoriums()).extracting(Auditorium::getName).containsExactly("252");
        });
        assertThat(report.withoutRoom()).isZero();
    }

    @Test
    @DisplayName("Сессия всегда новая и привязана к периоду; берётся через единую дверь")
    void sessionIsNewAndTakenThroughTheGate() {
        resolves(lesson(DAY, List.of("252-3"), false));

        ScheduleWriteReport report = write(false);

        assertThat(saved.getStudyPeriod()).isSameAs(period);
        assertThat(report.sessionName()).contains("Импорт: Осень 2025/2026");
        // Путей записи в lesson_placement было восемь — импорт не должен стать девятым (И-9).
        verify(gate).forWrite(saved.getId(), null);
    }

    @Test
    @DisplayName("Занятие вне периода не размещается, но и не теряется — оно считается отдельно (И-8)")
    void lessonOutsideThePeriodIsNotPlaced() {
        resolves(lesson(DAY, List.of("252-3"), false),
                lesson(DAY.plusMonths(6), List.of("252-3"), true));

        ScheduleWriteReport report = write(false);

        assertThat(report.placements()).isEqualTo(1);
        assertThat(report.outsidePeriod()).isEqualTo(1);
        assertThat(written()).hasSize(1);
    }

    @Test
    @DisplayName("Комната из файла не нашлась — занятие всё равно встаёт, но без аудитории")
    void unresolvedRoomDoesNotCancelThePlacement() {
        // «Сп. зал» в базе нет: время занятия при этом верное, а чужая комната врала бы молча.
        resolves(lesson(DAY, List.of("Сп. зал"), false));

        ScheduleWriteReport report = write(false);

        assertThat(report.placements()).isEqualTo(1);
        assertThat(report.withoutRoom()).isEqualTo(1);
        assertThat(written()).singleElement()
                .satisfies(placement -> assertThat(placement.getAssignedAuditoriums()).isEmpty());
    }

    @Test
    @DisplayName("Проекция — по требованию: без неё расписание видно только датчиками Command Side")
    void projectionIsOptional() {
        resolves(lesson(DAY, List.of("252-3"), false));

        assertThat(write(false).projected()).isZero();
        verify(synchronizer, never()).reprojectSession(any());

        when(synchronizer.reprojectSession(any())).thenReturn(3);
        assertThat(write(true).projected()).isEqualTo(3);
    }
}
