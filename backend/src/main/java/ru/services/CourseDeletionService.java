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
import ru.repository.read.ScheduleViewRepository;
import ru.repository.write.LessonPlacementRepository;

import java.util.List;
import java.util.UUID;

/**
 * Глубокое каскадное удаление курса. Выделено в отдельный сервис (SRP) — это
 * самостоятельная операция, зеркальная {@link CurriculumCloneService}, и не относится
 * к обычному CRUD курсов в {@link DisciplineCourseService}, который делегирует сюда.
 *
 * <p><b>Каскад write-стороны</b> ({@code curriculum_slot → assignment → lesson_placement},
 * join-таблицы, {@code slot_chain}) выполняет БД по {@code ON DELETE CASCADE} — при удалении
 * строки {@code discipline_course} граф сносится целиком.</p>
 *
 * <p><b>Read-модель</b> {@code schedule_view} не имеет FK на {@code lesson_placement}
 * (CQRS-развязка), поэтому FK-каскад её не трогает. Чистим её здесь <b>синхронно в той же
 * транзакции</b>: захватываем id размещений курса ДО удаления и массово удаляем строки view.
 * Это даёт атомарность (всё либо коммитится, либо откатывается) — критично для
 * многопользовательского режима: нет окна «призраков» и нет осиротевших read-строк.
 * Осознанно НЕ используем событийную async-очистку (как {@link ScheduleSynchronizer}):
 * для удаления важнее строгая консистентность.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseDeletionService {

    private final DisciplineCourseRepository disciplineCourseRepository;
    private final CurriculumSlotRepository curriculumSlotRepository;
    private final AssignmentRepository assignmentRepository;
    private final LessonPlacementRepository placementRepository;
    private final ScheduleViewRepository scheduleViewRepository;

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
     * Каскадно удаляет курс: сначала синхронно чистит read-модель по размещениям курса,
     * затем удаляет курс (FK-каскад БД сносит слоты, назначения, размещения и сцепки).
     *
     * @param courseId id курса
     * @throws EntityNotFoundException если курс не найден
     */
    @Transactional
    public void delete(Integer courseId) {
        if (!disciplineCourseRepository.existsById(courseId)) {
            throw new EntityNotFoundException("Курс с id=" + courseId + " не найден.");
        }

        // Захватываем id размещений ДО удаления — после FK-каскада их уже не будет.
        List<UUID> placementIds = placementRepository.findIdsByCourseId(courseId);
        if (!placementIds.isEmpty()) {
            scheduleViewRepository.deleteByPlacementIdIn(placementIds);
        }

        disciplineCourseRepository.deleteById(courseId);

        log.info("Удалён курс id={}: снято размещений в read-модели={}", courseId, placementIds.size());
    }
}
