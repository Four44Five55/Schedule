package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.curriculumSlot.CurriculumSlotCreateDto;
import ru.dto.curriculumSlot.CurriculumSlotDto;
import ru.dto.curriculumSlot.CurriculumSlotUpdateDto;
import ru.dto.curriculumSlot.SlotDeletionImpactDto;
import ru.entity.Auditorium;
import ru.entity.Lesson;
import ru.entity.logicSchema.AuditoriumPool;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.ThemeLesson;
import ru.enums.AssessmentWindow;
import ru.mapper.CurriculumSlotMapper;
import ru.repository.AssignmentRepository;
import ru.repository.CurriculumSlotRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import ru.exceptions.NotFoundException;

@Service
@RequiredArgsConstructor
public class CurriculumSlotService {

    private final CurriculumSlotRepository curriculumSlotRepository;
    private final DisciplineCourseService disciplineCourseService;
    private final ThemeLessonService themeLessonService;
    private final AuditoriumService auditoriumService;
    private final AuditoriumPoolService auditoriumPoolService;
    private final CurriculumSlotMapper curriculumSlotMapper;
    private final ProjectionMaintenance projectionMaintenance;
    // Только счётчики для предпросмотра последствий удаления (репозитории, а не сервисы:
    // AssignmentService сам зависит от CurriculumSlotService — иначе цикл бинов).
    private final AssignmentRepository assignmentRepository;
    private final LessonPlacementRepository placementRepository;

    @Transactional(readOnly = true)
    public List<CurriculumSlotDto> findAll() {
        return curriculumSlotRepository.findAll().stream()
                .map(curriculumSlotMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CurriculumSlotDto> findByCourseId(Integer courseId) {
        return curriculumSlotRepository.findByDisciplineCourseIdOrderByPosition(courseId)
                .stream()
                .map(curriculumSlotMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CurriculumSlotDto getDtoById(Integer id) {
        return curriculumSlotMapper.toDto(getEntityById(id));
    }

    @Transactional
    public CurriculumSlotDto createSlot(CurriculumSlotCreateDto createDto) {
        Integer courseId = createDto.disciplineCourseId();

        // 1. Проверяем существование курса через сервис
        DisciplineCourse course = disciplineCourseService.getEntityById(courseId);

        // 2. "Раздвигаем" слоты
        curriculumSlotRepository.incrementPositionsFrom(courseId, createDto.position());

        // 3. Создаем и наполняем сущность
        CurriculumSlot newSlot = new CurriculumSlot();
        newSlot.setPosition(createDto.position());
        newSlot.setDisciplineCourse(course);
        newSlot.setKindOfStudy(createDto.kindOfStudy());
        newSlot.setAssessmentWindow(resolveWindow(createDto.assessmentWindow()));

        // 4. Устанавливаем связи через сервисы
        if (createDto.themeLessonId() != null) {
            ThemeLesson theme = themeLessonService.getEntityById(createDto.themeLessonId());
            newSlot.setThemeLesson(theme);
        }
        if (createDto.requiredAuditoriumId() != null) {
            Auditorium aud = auditoriumService.getEntityById(createDto.requiredAuditoriumId());
            newSlot.setRequiredAuditorium(aud);
        }
        if (createDto.priorityAuditoriumId() != null) {
            Auditorium priorityAud = auditoriumService.getEntityById(createDto.priorityAuditoriumId());
            newSlot.setPriorityAuditorium(priorityAud);
        }
        if (createDto.allowedAuditoriumPoolId() != null) {
            AuditoriumPool pool = auditoriumPoolService.getEntityById(createDto.allowedAuditoriumPoolId());
            newSlot.setAllowedAuditoriumPool(pool);
        }

        return curriculumSlotMapper.toDto(curriculumSlotRepository.save(newSlot));
    }

    /**
     * Где сдаётся аттестация; не задано — «в учебное время».
     *
     * <p>Умолчание строгое: {@code SESSION} означает «генерация это не размещает», и получить такое
     * поведение по забывчивости клиента нельзя — занятие молча исчезло бы из расписания.</p>
     */
    private static AssessmentWindow resolveWindow(AssessmentWindow window) {
        return window == null ? AssessmentWindow.STUDY_TIME : window;
    }

    @Transactional
    public CurriculumSlotDto updateSlot(Integer slotId, CurriculumSlotUpdateDto updateDto) {
        CurriculumSlot slotToUpdate = getEntityById(slotId);

        slotToUpdate.setKindOfStudy(updateDto.kindOfStudy());
        slotToUpdate.setAssessmentWindow(resolveWindow(updateDto.assessmentWindow()));

        // Обновляем связи через сервисы, обрабатывая null
        slotToUpdate.setThemeLesson(
                updateDto.themeLessonId() != null
                        ? themeLessonService.getEntityById(updateDto.themeLessonId())
                        : null
        );
        slotToUpdate.setRequiredAuditorium(
                updateDto.requiredAuditoriumId() != null
                        ? auditoriumService.getEntityById(updateDto.requiredAuditoriumId())
                        : null
        );
        slotToUpdate.setPriorityAuditorium(
                updateDto.priorityAuditoriumId() != null
                        ? auditoriumService.getEntityById(updateDto.priorityAuditoriumId())
                        : null
        );
        slotToUpdate.setAllowedAuditoriumPool(
                updateDto.allowedAuditoriumPoolId() != null
                        ? auditoriumPoolService.getEntityById(updateDto.allowedAuditoriumPoolId())
                        : null
        );

        CurriculumSlotDto updated = curriculumSlotMapper.toDto(curriculumSlotRepository.save(slotToUpdate));
        // Вид занятия и тема слота лежат в read-модели снимком: без перепроекции уже размещённое
        // занятие продолжало бы показывать прежний вид/тему.
        projectionMaintenance.announce(ProjectionSource.CURRICULUM_SLOT, slotId);
        return updated;
    }

    /**
     * Находит DTO слота по ID (для контроллера).
     *
     * @param id ID слота.
     * @return Optional с DTO слота.
     */
    @Transactional(readOnly = true)
    public Optional<CurriculumSlotDto> findById(Integer id) {
        return curriculumSlotRepository.findById(id)
                .map(curriculumSlotMapper::toDto);
    }

    /**
     * Предпросмотр последствий удаления занятия плана: сколько назначений и уже размещённых
     * занятий уйдёт каскадом и сколько из них закреплено вручную. Состояние не меняет.
     *
     * <p>Прецедент — {@code /assignments/{id}/delete-impact} и предпросмотр удаления курса.
     * У слота такого предупреждения не было, хотя теряется ровно то же самое: каскад
     * {@code curriculum_slot → assignment → lesson_placement} уносит и ручную раскладку.</p>
     */
    @Transactional(readOnly = true)
    public SlotDeletionImpactDto deleteImpact(Integer id) {
        CurriculumSlot slot = getEntityById(id);
        return new SlotDeletionImpactDto(
                slot.getId(),
                slot.getPosition(),
                slot.getKindOfStudy() != null ? slot.getKindOfStudy().name() : null,
                assignmentRepository.countByCurriculumSlotId(id),
                placementRepository.countBySlotId(id),
                placementRepository.countLockedBySlotId(id));
    }

    /**
     * Удаляет слот по ID с корректировкой позиций остальных слотов.
     *
     * <p>Каскад БД уносит назначения слота и их размещения (включая закреплённые), а следом —
     * строки read-модели (FK {@code schedule_view → lesson_placement}, миграция 017). Цена
     * называется заранее в {@link #deleteImpact} и подтверждается в UI.</p>
     *
     * @param id ID удаляемого слота.
     * @throws NotFoundException если слот не найден.
     */
    @Transactional
    public void deleteSlot(Integer id) {
        CurriculumSlot slot = getEntityById(id);
        Integer courseId = slot.getDisciplineCourse().getId();
        Integer deletedPosition = slot.getPosition();

        curriculumSlotRepository.delete(slot);

        // Сдвигаем позиции последующих слотов
        curriculumSlotRepository.decrementPositionsAfter(courseId, deletedPosition);
    }

    /**
     * Проверяет существование слота по его ID.
     *
     * @param id ID слота для проверки.
     * @return true, если слот существует, иначе false.
     */
    @Transactional(readOnly = true)
    public boolean existsById(Integer id) {
        return curriculumSlotRepository.existsById(id);
    }
    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    /**
     * [СЛУЖЕБНЫЙ МЕТОД] Находит сущность CurriculumSlot по ID.
     */
    @Transactional(readOnly = true)
    public CurriculumSlot getEntityById(Integer id) {
        return curriculumSlotRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CurriculumSlot с id=" + id + " не найден."));
    }

    /** [СЛУЖЕБНЫЙ] Слоты курса (сущности), упорядоченные по позиции. */
    @Transactional(readOnly = true)
    public List<CurriculumSlot> getEntitiesByCourseId(Integer courseId) {
        return curriculumSlotRepository.findByDisciplineCourseIdOrderByPosition(courseId);
    }

    /**
     * [СЛУЖЕБНЫЙ МЕТОД] Находит предыдущую лекцию.
     */
    @Transactional(readOnly = true)
    public Optional<CurriculumSlot> getPreviousLectureInCourse(Lesson lesson) {
        return curriculumSlotRepository.findPreviousLectureInCourse(lesson.getDisciplineCourse().getId(), lesson.getCurriculumSlot().getPosition());

    }
}
