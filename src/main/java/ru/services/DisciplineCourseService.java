package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.disciplineCourse.DisciplineCourseCreateDto;
import ru.dto.disciplineCourse.DisciplineCourseResponseDto;
import ru.dto.disciplineCourse.DisciplineCourseUpdateDto;
import ru.entity.Discipline;
import ru.entity.logicSchema.DisciplineCourse;
import ru.mapper.DisciplineCourseMapper;
import ru.repository.DisciplineCourseRepository;
import ru.repository.DisciplineRepository;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления учебными курсами (DisciplineCourse).
 * Инкапсулирует бизнес-логику, валидацию и работу с данными для курсов.
 */
@Service
@RequiredArgsConstructor
public class DisciplineCourseService {

    private final DisciplineCourseRepository disciplineCourseRepository;
    //TODO вынести репозиторий дисциплины и использовать сервис дисциплин
    private final DisciplineRepository disciplineRepository;
    private final DisciplineCourseMapper disciplineCourseMapper;

    /**
     * Создает новый учебный курс на основе DTO.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданного курса.
     * @throws EntityNotFoundException если дисциплина с указанным ID не найдена.
     * @throws IllegalStateException   если курс для данной дисциплины и семестра уже существует.
     */
    @Transactional
    public DisciplineCourseResponseDto createCourse(DisciplineCourseCreateDto createDto) {
        // 1. Находим родительскую сущность Discipline
        Discipline discipline = disciplineRepository.findById(createDto.getDisciplineId())
                .orElseThrow(() -> new EntityNotFoundException("Дисциплина с id=" + createDto.disciplineId() + " не найдена."));

        // 2. Проверяем бизнес-правило: не должно быть дубликатов
        if (disciplineCourseRepository.existsByDisciplineIdAndSemester(createDto.getDisciplineId(), createDto.semester())) {
            throw new IllegalStateException("Курс для дисциплины '" + discipline.getName() + "' и семестра " + createDto.semester() + " уже существует.");
        }

        // 3. Создаем и наполняем новую сущность
        DisciplineCourse newCourse = new DisciplineCourse();
        newCourse.setDiscipline(discipline);
        newCourse.setSemester(createDto.semester());

        // 4. Сохраняем в БД
        DisciplineCourse savedCourse = disciplineCourseRepository.save(newCourse);

        // 5. Преобразуем в DTO для ответа
        return disciplineCourseMapper.toDto(savedCourse);
    }

    /**
     * Находит курс по его уникальному идентификатору.
     *
     * @param id ID курса.
     * @return Optional с DTO курса, если найден, или пустой Optional.
     */
    @Transactional(readOnly = true)
    public Optional<DisciplineCourseResponseDto> findCourseById(Integer id) {
        return disciplineCourseRepository.findById(id)
                .map(disciplineCourseMapper::toDto);
    }

    /**
     * Находит все курсы, принадлежащие одной дисциплине, и сортирует их по семестру.
     *
     * @param disciplineId ID родительской дисциплины.
     * @return Список DTO курсов.
     */
    @Transactional(readOnly = true)
    public List<DisciplineCourseResponseDto> findAllCoursesByDiscipline(Integer disciplineId) {
        List<DisciplineCourse> courses = disciplineCourseRepository.findByDisciplineIdOrderBySemester(disciplineId);
        return disciplineCourseMapper.toDtoList(courses);
    }

    /**
     * Обновляет данные существующего учебного курса.
     *
     * @param id        ID обновляемого курса.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленного курса.
     * @throws EntityNotFoundException если курс с указанным ID не найден.
     * @throws IllegalStateException   если изменение семестра приведет к дубликату.
     */
    @Transactional
    public DisciplineCourseResponseDto updateCourse(Integer id, DisciplineCourseUpdateDto updateDto) {
        // 1. Находим сущность в БД
        DisciplineCourse courseToUpdate = disciplineCourseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Курс с id=" + id + " не найден."));

        // 2. Проверяем, не приведет ли изменение к конфликту
        Integer disciplineId = courseToUpdate.getDiscipline().getId();
        if (courseToUpdate.getSemester() != updateDto.semester() &&
                disciplineCourseRepository.existsByDisciplineIdAndSemester(disciplineId, updateDto.semester())) {
            throw new IllegalStateException("Курс для данной дисциплины и семестра " + updateDto.semester() + " уже существует.");
        }

        // 3. Обновляем поля сущности
        courseToUpdate.setSemester(updateDto.semester());

        // 4. Сохраняем. Hibernate поймет, что это UPDATE, т.к. сущность уже отслеживается.
        DisciplineCourse updatedCourse = disciplineCourseRepository.save(courseToUpdate);

        // 5. Возвращаем DTO с обновленными данными
        return disciplineCourseMapper.toDto(updatedCourse);
    }

    /**
     * Удаляет учебный курс по его ID.
     *
     * @param id ID удаляемого курса.
     * @throws EntityNotFoundException если курс с указанным ID не найден.
     */
    @Transactional
    public void deleteCourse(Integer id) {
        // Проверяем существование, чтобы выбросить осмысленную ошибку, если курса нет
        if (!disciplineCourseRepository.existsById(id)) {
            throw new EntityNotFoundException("Курс с id=" + id + " не найден, удаление невозможно.");
        }
        disciplineCourseRepository.deleteById(id);
    }
}
