package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.curriculumSlot.CurriculumSlotCreateDto;
import ru.dto.curriculumSlot.CurriculumSlotDto;
import ru.dto.curriculumSlot.CurriculumSlotUpdateDto;
import ru.entity.Auditorium;
import ru.entity.logicSchema.AuditoriumPool;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.ThemeLesson;
import ru.mapper.CurriculumSlotMapper;
import ru.repository.CurriculumSlotRepository;

@Service
@RequiredArgsConstructor
public class CurriculumSlotService {

    private final CurriculumSlotRepository curriculumSlotRepository;
    private final DisciplineCourseService disciplineCourseService;
    private final ThemeLessonService themeLessonService;
    private final AuditoriumService auditoriumService;
    private final AuditoriumPoolService auditoriumPoolService;
    private final CurriculumSlotMapper curriculumSlotMapper;

    @Transactional
    public CurriculumSlotDto createSlot(CurriculumSlotCreateDto createDto) {
        Integer courseId = createDto.disciplineCourseId();

        // 1. Проверяем существование курса через сервис
        DisciplineCourse course = disciplineCourseService.findEntityById(courseId);

        // 2. "Раздвигаем" слоты
        curriculumSlotRepository.incrementPositionsFrom(courseId, createDto.position());

        // 3. Создаем и наполняем сущность
        CurriculumSlot newSlot = new CurriculumSlot();
        newSlot.setPosition(createDto.position());
        newSlot.setDisciplineCourse(course);
        newSlot.setKindOfStudy(createDto.kindOfStudy());

        // 4. Устанавливаем связи через сервисы
        if (createDto.themeLessonId() != null) {
            ThemeLesson theme = themeLessonService.findEntityById(createDto.themeLessonId());
            newSlot.setThemeLesson(theme);
        }
        if (createDto.requiredAuditoriumId() != null) {
            Auditorium aud = auditoriumService.findEntityById(createDto.requiredAuditoriumId());
            newSlot.setRequiredAuditorium(aud);
        }
        if (createDto.priorityAuditoriumId() != null) {
            Auditorium priorityAud = auditoriumService.findEntityById(createDto.priorityAuditoriumId());
            newSlot.setPriorityAuditorium(priorityAud);
        }
        if (createDto.allowedAuditoriumPoolId() != null) {
            AuditoriumPool pool = auditoriumPoolService.findEntityById(createDto.allowedAuditoriumPoolId());
            newSlot.setAllowedAuditoriumPool(pool);
        }

        return curriculumSlotMapper.toDto(curriculumSlotRepository.save(newSlot));
    }

    @Transactional
    public CurriculumSlotDto updateSlot(Integer slotId, CurriculumSlotUpdateDto updateDto) {
        CurriculumSlot slotToUpdate = findEntityById(slotId);

        slotToUpdate.setKindOfStudy(updateDto.kindOfStudy());

        // Обновляем связи через сервисы, обрабатывая null
        slotToUpdate.setThemeLesson(
                updateDto.themeLessonId() != null
                        ? themeLessonService.findEntityById(updateDto.themeLessonId())
                        : null
        );
        slotToUpdate.setRequiredAuditorium(
                updateDto.requiredAuditoriumId() != null
                        ? auditoriumService.findEntityById(updateDto.requiredAuditoriumId())
                        : null
        );
        slotToUpdate.setPriorityAuditorium(
                updateDto.priorityAuditoriumId() != null
                        ? auditoriumService.findEntityById(updateDto.priorityAuditoriumId())
                        : null
        );
        slotToUpdate.setAllowedAuditoriumPool(
                updateDto.allowedAuditoriumPoolId() != null
                        ? auditoriumPoolService.findEntityById(updateDto.allowedAuditoriumPoolId())
                        : null
        );

        return curriculumSlotMapper.toDto(curriculumSlotRepository.save(slotToUpdate));
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

    /**
     * [СЛУЖЕБНЫЙ МЕТОД] Находит сущность CurriculumSlot по ID.
     */
    @Transactional(readOnly = true)
    public CurriculumSlot findEntityById(Integer id) {
        return curriculumSlotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CurriculumSlot с id=" + id + " не найден."));
    }
}
