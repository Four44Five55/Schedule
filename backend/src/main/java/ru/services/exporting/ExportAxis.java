package ru.services.exporting;

import ru.dto.ScheduledLessonDto;
import ru.entity.constraints.ConstraintData;
import ru.entity.read.ScheduleView;
import ru.services.constraints.AllConstraints;

import java.util.List;
import java.util.Map;

/**
 * Перспектива выгрузки расписания (Strategy как enum) — «с чьей точки зрения» строится файл:
 * группа, преподаватель или аудитория. Прецедент — {@link ru.services.board.BoardAxis}.
 *
 * <p>Каждая ось знает три вещи, и все они — чистые функции над данными Query Side
 * (никаких Spring-зависимостей в enum, ровно как у {@code BoardAxis}):</p>
 * <ul>
 *   <li>{@link #entityId}/{@link #entityName} — как из строки {@code schedule_view} достать
 *       идентификатор и имя сущности этой оси (для группировки строк по сущности);</li>
 *   <li>{@link #cellLines} — какие 3 строки показать в ячейке занятия с этой точки зрения
 *       (перенос логики старого {@code getListDataLessonForEntity} без {@code instanceof}).</li>
 * </ul>
 *
 * <p>OCP: новая перспектива — это новая константа, без правок сервиса/рендерера/фронта. Spring
 * связывает значение из query-параметра ({@code ?axis=GROUP|EDUCATOR|AUDITORIUM}) прямо в enum.</p>
 *
 * <p><b>Замечание по колонке {@code study_stream_id}:</b> в проекции «вариант 3» одна строка на
 * группу потока, и id группы хранится именно в {@code study_stream_id} (см. {@code ScheduleSynchronizer}).
 * Поэтому {@code GROUP.entityId} читает {@code getStudyStreamId()}.</p>
 */
public enum ExportAxis {

    /** Группа: в ячейке — тема, дисциплина, аудитория (имя самой группы избыточно). */
    GROUP("Группа") {
        @Override public Integer entityId(ScheduleView v) { return v.getStudyStreamId(); }
        @Override public String entityName(ScheduleView v) { return v.getGroupName(); }
        @Override public List<String> cellLines(ScheduledLessonDto l) {
            return List.of(themeInfo(l), nn(l.disciplineAbbreviation()), join(l.auditoriumNames()));
        }
        @Override public Map<Integer, List<ConstraintData>> constraintsBy(AllConstraints all) {
            return all.groupConstraints();
        }
    },

    /** Преподаватель: в ячейке — дисциплина, группы, аудитория. */
    EDUCATOR("Преподаватель") {
        @Override public Integer entityId(ScheduleView v) { return v.getEducatorId(); }
        @Override public String entityName(ScheduleView v) { return v.getEducatorName(); }
        @Override public List<String> cellLines(ScheduledLessonDto l) {
            return List.of(nn(l.disciplineAbbreviation()), join(l.groupNames()), join(l.auditoriumNames()));
        }
        @Override public Map<Integer, List<ConstraintData>> constraintsBy(AllConstraints all) {
            return all.educatorConstraints();
        }
    },

    /** Аудитория: в ячейке — тема, группы, дисциплина. */
    AUDITORIUM("Аудитория") {
        @Override public Integer entityId(ScheduleView v) { return v.getAuditoriumId(); }
        @Override public String entityName(ScheduleView v) { return v.getAuditoriumName(); }
        @Override public List<String> cellLines(ScheduledLessonDto l) {
            return List.of(themeInfo(l), join(l.groupNames()), nn(l.disciplineAbbreviation()));
        }
        @Override public Map<Integer, List<ConstraintData>> constraintsBy(AllConstraints all) {
            return all.auditoriumConstraints();
        }
    };

    private final String title;

    ExportAxis(String title) { this.title = title; }

    /** Человеко-читаемое имя оси (для имени файла/подписи). */
    public String title() { return title; }

    /** Идентификатор сущности этой оси в строке проекции ({@code null} → строка к оси не относится). */
    public abstract Integer entityId(ScheduleView v);

    /** Имя сущности этой оси в строке проекции. */
    public abstract String entityName(ScheduleView v);

    /** Три строки содержимого ячейки занятия с точки зрения этой оси. */
    public abstract List<String> cellLines(ScheduledLessonDto l);

    /** Карта ограничений (id сущности → развёрнутые в ячейки) этой оси из {@link AllConstraints}. */
    public abstract Map<Integer, List<ConstraintData>> constraintsBy(AllConstraints all);

    /** Вид занятия + номер темы: «Л1.1»; без темы — только вид («ЭКЗ»). */
    protected static String themeInfo(ScheduledLessonDto l) {
        String kind = nn(l.kindOfStudyAbbr());
        return (l.themeNumber() != null && !l.themeNumber().isBlank()) ? kind + l.themeNumber() : kind;
    }

    protected static String join(List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    protected static String nn(String s) {
        return s == null ? "" : s;
    }
}
