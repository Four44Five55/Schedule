package ru.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.entity.Assignment;
import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.write.LessonPlacement;
import ru.services.solver.ScheduleWorkspace;

import java.util.ArrayList;
import java.util.List;

/**
 * Единый посев сохранённого размещения в in-memory workspace решателя.
 *
 * <p>Связка {@code Assignment → Lesson → forcePlacement} нужна в нескольких местах:
 * пересоздание workspace для переноса ({@link WorkspaceRecreationService}), а в Фиче 2 —
 * засев закреплённых занятий (пинов) перед генерацией (Фаза 0). Чтобы реконструкция
 * не разъезжалась между путями, она живёт здесь (DRY, единый источник истины посева).</p>
 *
 * <p>Компонент намеренно не знает про сессии/пины/персист — только «возьми размещение,
 * собери доменный {@link Lesson} и принудительно поставь его в сетку». Решение, какие
 * размещения сеять (например, {@code where locked}) и что с ними делать дальше —
 * ответственность вызывающего (оркестратора).</p>
 */
@Slf4j
@Component
public class WorkspacePlacementSeeder {

    /**
     * Собирает доменный {@link Lesson} из {@link Assignment} (требования к аудитории
     * копируются из {@code curriculumSlot}). Идентичность занятия — по бизнес-ключу
     * {@code (curriculumSlot, educators, studyStream)} (см. {@code AbstractLesson.equals}),
     * поэтому пересозданный экземпляр эквивалентен «живому» из {@code scope.lessons()}.
     */
    public Lesson buildLesson(Assignment assignment) {
        Lesson lesson = new Lesson();
        lesson.setDisciplineCourse(assignment.getCurriculumSlot().getDisciplineCourse());
        lesson.setCurriculumSlot(assignment.getCurriculumSlot());
        lesson.setStudyStream(assignment.getStudyStream());
        lesson.setEducators(assignment.getEducators());

        if (assignment.getCurriculumSlot().getRequiredAuditorium() != null) {
            lesson.setRequiredAuditorium(assignment.getCurriculumSlot().getRequiredAuditorium());
        }
        if (assignment.getCurriculumSlot().getPriorityAuditorium() != null) {
            lesson.setPriorityAuditorium(assignment.getCurriculumSlot().getPriorityAuditorium());
        }
        if (assignment.getCurriculumSlot().getAllowedAuditoriumPool() != null) {
            lesson.setAllowedAuditoriumPool(assignment.getCurriculumSlot().getAllowedAuditoriumPool());
        }

        return lesson;
    }

    /**
     * Реконструирует занятие из размещения и принудительно ставит его в workspace
     * на сохранённые дату/слот/аудитории.
     *
     * @return размещённый {@link Lesson}, либо {@code null}, если у размещения нет
     *         {@link Assignment} (битые данные — пропускаем, не валим весь посев).
     */
    public Lesson seedInto(ScheduleWorkspace workspace, LessonPlacement placement) {
        Assignment assignment = placement.getAssignment();
        if (assignment == null) {
            log.warn("⚠️  Placement без assignment: {} — пропуск посева", placement.getId());
            return null;
        }

        Lesson lesson = buildLesson(assignment);
        CellForLesson cell = new CellForLesson(placement.getScheduledDate(), placement.getScheduledSlot());

        List<Auditorium> auditoriums = (placement.getAssignedAuditoriums() != null)
                ? new ArrayList<>(placement.getAssignedAuditoriums())
                : new ArrayList<>();
        workspace.forcePlacement(lesson, cell, auditoriums);

        return lesson;
    }
}
