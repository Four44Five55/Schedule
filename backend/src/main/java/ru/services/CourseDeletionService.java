package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.disciplineCourse.CourseDeletionImpactDto;
import ru.entity.logicSchema.DisciplineCourse;
import ru.repository.AssignmentRepository;
import ru.repository.CurriculumSlotRepository;
import ru.repository.DisciplineCourseRepository;
import ru.repository.write.LessonPlacementRepository;

/**
 * Глубокое каскадное удаление курса. Выделено в отдельный сервис (SRP) — это
 * самостоятельная операция, зеркальная {@link CurriculumCloneService}, и не относится
 * к обычному CRUD курсов в {@link DisciplineCourseService}, который делегирует сюда.
 *
 * <p><b>Каскад write-стороны</b> ({@code curriculum_slot → assignment → lesson_placement},
 * join-таблицы, {@code slot_chain}) выполняет БД по {@code ON DELETE CASCADE} — при удалении
 * строки {@code discipline_course} граф сносится целиком.</p>
 *
 * <p><b>Read-модель</b> {@code schedule_view} уходит следом за размещениями тем же каскадом:
 * с миграции 017 у неё есть FK на {@code lesson_placement} с {@code ON DELETE CASCADE}.
 * Руками её здесь не чистим — иначе знание «у размещения есть проекция» пришлось бы повторять
 * в каждом сервисе, который что-то удаляет (ровно так и появлялись строки-призраки на путях,
 * где о нём забывали). Удаление остаётся атомарным: всё либо коммитится, либо откатывается.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseDeletionService {

    private final DisciplineCourseRepository disciplineCourseRepository;
    private final CurriculumSlotRepository curriculumSlotRepository;
    private final AssignmentRepository assignmentRepository;
    private final LessonPlacementRepository placementRepository;

    /**
     * Предпросмотр последствий удаления: сколько слотов/назначений/размещённых занятий
     * будет снесено. Не меняет состояние.
     */
    @Transactional(readOnly = true)
    public CourseDeletionImpactDto preview(Integer courseId) {
        DisciplineCourse course = disciplineCourseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Курс с id=" + courseId + " не найден."));

        int slots = curriculumSlotRepository.findByDisciplineCourseIdOrderByPosition(courseId).size();
        int assignments = (int) assignmentRepository.countByCurriculumSlot_DisciplineCourse_Id(courseId);
        long placedLessons = placementRepository.countByCourseId(courseId);

        return new CourseDeletionImpactDto(
                course.getId(),
                course.getDiscipline().getName(),
                course.getSemester(),
                slots,
                assignments,
                placedLessons);
    }

    /**
     * Каскадно удаляет курс: FK-каскад БД сносит слоты, назначения, сцепки, размещения
     * и — следом за размещениями — строки read-модели.
     *
     * @param courseId id курса
     * @throws EntityNotFoundException если курс не найден
     */
    @Transactional
    public void delete(Integer courseId) {
        if (!disciplineCourseRepository.existsById(courseId)) {
            throw new EntityNotFoundException("Курс с id=" + courseId + " не найден.");
        }

        long placements = placementRepository.countByCourseId(courseId);
        disciplineCourseRepository.deleteById(courseId);

        log.info("Удалён курс id={}: снято размещений={}", courseId, placements);
    }
}
