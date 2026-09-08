package ru.services.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.enums.TimeSlotPair;
import ru.services.constraints.AllConstraints;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Изъять → поработать → вернуть как было»: подбор вариантов переноса обязан временно убрать само
 * занятие, иначе его собственные ресурсы выглядят занятыми и ответ вырождается в «переносить некуда».
 *
 * <p>Проверяется именно <b>гарантия возврата</b>. До 2026-08-27 её не было:
 * {@code MoveLessonSuggestionService} возвращал занятие последней строкой (то есть не возвращал при
 * исключении), а {@code LessonChainMoveService.findChainMoveOptions} не возвращал вовсе. Пока
 * workspace жил один запрос и выбрасывался, это ничего не стоило; с кэшем такой подбор молча
 * стирает занятие из общего снимка — без единой ошибки в логе. Поэтому тест на исключение здесь
 * главный, а не «на всякий случай».</p>
 */
class ScheduleWorkspaceBorrowTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);
    private static final LocalDate DATE = LocalDate.of(2026, 9, 7); // понедельник
    private static final CellForLesson CELL = new CellForLesson(DATE, TimeSlotPair.FIRST);

    private final Educator educator = educator(1);
    private final Group group = group(10);
    private final Auditorium room = auditorium(100);

    private final ScheduleWorkspace workspace = new ScheduleWorkspace(
            START, END, List.of(educator), List.of(group), List.of(room),
            new AllConstraints(Map.of(), Map.of(), Map.of()));

    @Test
    @DisplayName("После чтения занятие стоит на прежнем месте, с прежней аудиторией")
    void placementIsRestoredAfterReading() {
        Lesson lesson = placedLesson();

        List<String> result = workspace.withoutPlacements(List.of(lesson), () -> List.of("готово"));

        assertThat(result).containsExactly("готово");
        assertThat(workspace.getCellForLesson(lesson)).isEqualTo(CELL);
        assertThat(lesson.getAssignedAuditoriums()).containsExactly(room);
        assertThat(workspace.getResourceManager().getEducatorResource(1).isFree(CELL))
                .as("ресурсы снова заняты — иначе следующий подбор увидит свободное место, которого нет")
                .isFalse();
        assertThat(workspace.getResourceManager().getGroupResource(10).isFree(CELL)).isFalse();
        assertThat(workspace.getResourceManager().getAuditoriumResource(100).isFree(CELL)).isFalse();
    }

    @Test
    @DisplayName("Во время чтения занятие действительно изъято — иначе изъятие бессмысленно")
    void placementIsAbsentInsideTheBody() {
        Lesson lesson = placedLesson();

        Boolean freeInside = workspace.withoutPlacements(List.of(lesson),
                () -> workspace.getResourceManager().getEducatorResource(1).isFree(CELL));

        assertThat(freeInside).isTrue();
    }

    @Test
    @DisplayName("Исключение внутри чтения не оставляет занятие изъятым")
    void placementIsRestoredOnException() {
        Lesson lesson = placedLesson();

        assertThatThrownBy(() -> workspace.withoutPlacements(List.of(lesson), () -> {
            throw new IllegalStateException("сбой посреди подбора");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(workspace.getCellForLesson(lesson))
                .as("это и есть смысл finally: обработчик исключений вернуть объект в памяти не может")
                .isEqualTo(CELL);
        assertThat(lesson.getAssignedAuditoriums()).containsExactly(room);
        assertThat(workspace.getResourceManager().getAuditoriumResource(100).isFree(CELL)).isFalse();
    }

    @Test
    @DisplayName("Цепочка возвращается целиком — каждое звено в свою ячейку")
    void wholeChainIsRestored() {
        Lesson first = placedLesson();
        Lesson second = lesson(2); // свой слот плана: занятия сравниваются по (слот · состав · поток)
        CellForLesson secondCell = new CellForLesson(DATE, TimeSlotPair.SECOND);
        workspace.forcePlacement(second, secondCell, List.of(room));

        assertThatThrownBy(() -> workspace.withoutPlacements(List.of(first, second), () -> {
            throw new IllegalStateException("сбой на середине перебора");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(workspace.getCellForLesson(first)).isEqualTo(CELL);
        assertThat(workspace.getCellForLesson(second)).isEqualTo(secondCell);
    }

    @Test
    @DisplayName("Неразмещённое занятие пропускается — в сетке не появляется ячейки null")
    void unplacedLessonIsSkipped() {
        Lesson notPlaced = lesson(3);

        Boolean executed = workspace.withoutPlacements(List.of(notPlaced), () -> true);

        assertThat(executed).isTrue();
        assertThat(workspace.getCellForLesson(notPlaced)).isNull();
        assertThat(workspace.getGrid().getGridMap())
                .as("прежний возврат звал forcePlacement(lesson, null, …) и клал занятие под ключ null")
                .doesNotContainKey(null);
    }

    /** Занятие, уже стоящее в сетке в {@link #CELL} с комнатой. */
    private Lesson placedLesson() {
        Lesson lesson = lesson(1);
        workspace.forcePlacement(lesson, CELL, List.of(room));
        return lesson;
    }

    private Lesson lesson(int slotId) {
        StudyStream stream = new StudyStream();
        stream.setId(1);
        stream.setName("поток");
        stream.setGroups(Set.of(group));

        CurriculumSlot slot = new CurriculumSlot();
        slot.setId(slotId);

        Lesson lesson = new Lesson();
        lesson.setCurriculumSlot(slot);
        lesson.setStudyStream(stream);
        lesson.getEducators().add(educator);
        return lesson;
    }

    private static Educator educator(int id) {
        Educator educator = new Educator();
        educator.setId(id);
        educator.setName("Преподаватель " + id);
        return educator;
    }

    private static Group group(int id) {
        Group group = new Group();
        group.setId(id);
        group.setName("Группа " + id);
        group.setSize(25);
        return group;
    }

    private static Auditorium auditorium(int id) {
        Auditorium auditorium = new Auditorium();
        auditorium.setId(id);
        auditorium.setName("Ауд. " + id);
        auditorium.setCapacity(30);
        return auditorium;
    }
}
