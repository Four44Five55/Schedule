package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.curriculumSlot.CurriculumSlotCreateDto;
import ru.dto.curriculumSlot.CurriculumSlotDto;
import ru.dto.curriculumSlot.CurriculumSlotUpdateDto;
import ru.entity.Auditorium;
import ru.entity.Lesson;
import ru.entity.logicSchema.AuditoriumPool;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.ThemeLesson;
import ru.mapper.CurriculumSlotMapper;
import ru.repository.CurriculumSlotRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CurriculumSlotService {

    private final CurriculumSlotRepository curriculumSlotRepository;
    private final DisciplineCourseService disciplineCourseService;
    private final ThemeLessonService themeLessonService;
    private final AuditoriumService auditoriumService;
    private final AuditoriumPoolService auditoriumPoolService;
    private final CurriculumSlotMapper curriculumSlotMapper;

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

    @Transactional
    public CurriculumSlotDto updateSlot(Integer slotId, CurriculumSlotUpdateDto updateDto) {
        CurriculumSlot slotToUpdate = getEntityById(slotId);

        slotToUpdate.setKindOfStudy(updateDto.kindOfStudy());

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

        return curriculumSlotMapper.toDto(curriculumSlotRepository.save(slotToUpdate));
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
     * Удаляет слот по ID с корректировкой позиций остальных слотов.
     *
     * @param id ID удаляемого слота.
     * @throws EntityNotFoundException если слот не найден.
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
                .orElseThrow(() -> new EntityNotFoundException("CurriculumSlot с id=" + id + " не найден."));
    }

    /**
     * [СЛУЖЕБНЫЙ МЕТОД] Находит предыдущую лекцию.
     */
    @Transactional(readOnly = true)
    public Optional<CurriculumSlot> getPreviousLectureInCourse(Lesson lesson) {
        return curriculumSlotRepository.findPreviousLectureInCourse(lesson.getDisciplineCourse().getId(), lesson.getCurriculumSlot().getPosition());

    }
}
