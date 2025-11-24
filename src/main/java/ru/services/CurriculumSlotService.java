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
import ru.repository.*;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurriculumSlotService {

    // --- Зависимости ---
    private final CurriculumSlotRepository curriculumSlotRepository;
    private final DisciplineCourseRepository disciplineCourseRepository;
    private final ThemeLessonRepository themeLessonRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final AuditoriumPoolRepository auditoriumPoolRepository;
    private final CurriculumSlotMapper curriculumSlotMapper;

    /**
     * Создает новый слот в учебном плане и сдвигает существующие слоты, чтобы освободить место.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданного слота.
     */
    @Transactional
    public CurriculumSlotDto createSlot(CurriculumSlotCreateDto createDto) {
        Integer courseId = createDto.disciplineCourseId();
        Integer position = createDto.position();

        // 1. Проверяем, существует ли курс, в который мы добавляем слот
        if (!disciplineCourseRepository.existsById(courseId)) {
            throw new EntityNotFoundException("DisciplineCourse с id=" + courseId + " не найден.");
        }

        // 2. "Раздвигаем" существующие слоты: все, что на этой позиции и дальше, сдвигается на +1
        curriculumSlotRepository.incrementPositionsFrom(courseId, position);

        // 3. Создаем и наполняем новую сущность
        CurriculumSlot newSlot = new CurriculumSlot();
        newSlot.setPosition(position);

        // Подгружаем связанные сущности по ID из DTO
        DisciplineCourse course = disciplineCourseRepository.getReferenceById(courseId);
        newSlot.setDisciplineCourse(course);

        if (createDto.themeLessonId() != null) {
            ThemeLesson theme = themeLessonRepository.getReferenceById(createDto.themeLessonId());
            newSlot.setThemeLesson(theme);
        }
        if (createDto.requiredAuditoriumId() != null) {
            Auditorium aud = auditoriumRepository.getReferenceById(createDto.requiredAuditoriumId());
            newSlot.setRequiredAuditorium(aud);
        }
        if (createDto.priorityAuditoriumId() != null) {
            Auditorium priorityAud = auditoriumRepository.getReferenceById(createDto.priorityAuditoriumId());
            newSlot.setPriorityAuditorium(priorityAud);
        }

        if (createDto.allowedAuditoriumPoolId() != null) {
            AuditoriumPool pool = auditoriumPoolRepository.getReferenceById(createDto.allowedAuditoriumPoolId());
            newSlot.setAllowedAuditoriumPool(pool);
        }

        newSlot.setKindOfStudy(createDto.kindOfStudy());

        // 4. Сохраняем новый слот
        CurriculumSlot savedSlot = curriculumSlotRepository.save(newSlot);
        return curriculumSlotMapper.toDto(savedSlot);
    }

    /**
     * Удаляет слот из учебного плана и "сдвигает" последующие слоты, чтобы закрыть "дыру".
     *
     * @param slotId ID удаляемого слота.
     */
    @Transactional
    public void deleteSlot(Integer slotId) {
        // 1. Находим слот, чтобы получить его позицию и ID курса
        CurriculumSlot slotToDelete = curriculumSlotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("CurriculumSlot с id=" + slotId + " не найден."));

        Integer courseId = slotToDelete.getDisciplineCourse().getId();
        Integer position = slotToDelete.getPosition();

        // 2. Удаляем сам слот
        curriculumSlotRepository.delete(slotToDelete);
        curriculumSlotRepository.flush(); // Принудительно отправляем запрос на удаление в БД

        // 3. "Сдвигаем" все последующие слоты на -1
        curriculumSlotRepository.decrementPositionsAfter(courseId, position);
    }

    /**
     * Обновляет информационные поля существующего слота.
     * Не меняет позицию или принадлежность к курсу.
     *
     * @param slotId    ID обновляемого слота.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленного слота.
     */
    @Transactional
    public CurriculumSlotDto updateSlot(Integer slotId, CurriculumSlotUpdateDto updateDto) {
        // 1. Находим сущность, которую будем обновлять.
        CurriculumSlot slotToUpdate = curriculumSlotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("CurriculumSlot с id=" + slotId + " не найден."));

        // 2. Обновляем вид занятия (обязательное поле в DTO)
        slotToUpdate.setKindOfStudy(updateDto.kindOfStudy());

        // 3. Обновляем связанные сущности.
        // Используем тернарный оператор для обработки null'ов: если ID пришел,
        // получаем прокси-объект, если нет - устанавливаем null.

        // Обновляем тему
        slotToUpdate.setThemeLesson(
                updateDto.themeLessonId() != null
                        ? themeLessonRepository.getReferenceById(updateDto.themeLessonId())
                        : null
        );

        // Обновляем жестко требуемую аудиторию
        slotToUpdate.setRequiredAuditorium(
                updateDto.requiredAuditoriumId() != null
                        ? auditoriumRepository.getReferenceById(updateDto.requiredAuditoriumId())
                        : null
        );

        // Обновляем приоритетную аудиторию
        slotToUpdate.setPriorityAuditorium(
                updateDto.priorityAuditoriumId() != null
                        ? auditoriumRepository.getReferenceById(updateDto.priorityAuditoriumId())
                        : null
        );

        // Обновляем пул аудиторий
        slotToUpdate.setAllowedAuditoriumPool(
                updateDto.allowedAuditoriumPoolId() != null
                        ? auditoriumPoolRepository.getReferenceById(updateDto.allowedAuditoriumPoolId())
                        : null
        );

        // 4. Сохраняем изменения. JPA поймет, что это UPDATE.
        CurriculumSlot updatedSlot = curriculumSlotRepository.save(slotToUpdate);

        // 5. Возвращаем DTO с обновленными данными.
        return curriculumSlotMapper.toDto(updatedSlot);
    }

    /**
     * Находит слот по его ID.
     */
    @Transactional(readOnly = true)
    public Optional<CurriculumSlotDto> findSlotById(Integer id) {
        return curriculumSlotRepository.findById(id).map(curriculumSlotMapper::toDto);
    }

    /**
     * Возвращает полный, отсортированный по позиции учебный план для указанного курса.
     */
    @Transactional(readOnly = true)
    public List<CurriculumSlotDto> findAllSlotsForCourse(Integer courseId) {
        return curriculumSlotRepository.findByDisciplineCourseIdOrderByPosition(courseId)
                .stream()
                .map(curriculumSlotMapper::toDto)
                .toList();
    }

    /**
     * Находит предыдущую лекцию в рамках того же учебного курса.
     *
     * @param currentSlotId ID текущего слота, от которого ведется поиск.
     * @return Optional с сущностью предыдущего лекционного слота, если он найден.
     * @throws EntityNotFoundException если currentSlotId не существует.
     */
    @Transactional(readOnly = true)
    public Optional<CurriculumSlot> getPreviousLecture(Integer currentSlotId) {
        // 1. Сначала нам нужно получить текущий слот, чтобы узнать его позицию и ID курса
        CurriculumSlot currentSlot = curriculumSlotRepository.findById(currentSlotId)
                .orElseThrow(() -> new EntityNotFoundException("CurriculumSlot с id=" + currentSlotId + " не найден."));

        Integer courseId = currentSlot.getDisciplineCourse().getId();
        Integer currentPosition = currentSlot.getPosition();

        // 2. Вызываем новый, адаптированный метод репозитория
        return curriculumSlotRepository.findPreviousLectureInCourse(courseId, currentPosition);
    }
}
