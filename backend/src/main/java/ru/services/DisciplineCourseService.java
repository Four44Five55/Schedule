package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.disciplineCourse.DisciplineCourseCreateDto;
import ru.dto.disciplineCourse.DisciplineCourseDto;
import ru.dto.disciplineCourse.DisciplineCourseUpdateDto;
import ru.entity.Discipline;
import ru.entity.StudyPeriod;
import ru.entity.logicSchema.DisciplineCourse;
import ru.mapper.DisciplineCourseMapper;
import ru.repository.DisciplineCourseRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class DisciplineCourseService {

    private final DisciplineCourseRepository disciplineCourseRepository;
    private final DisciplineService disciplineService;
    private final StudyPeriodService studyPeriodService;
    private final DisciplineCourseMapper disciplineCourseMapper;

    @Transactional
    public DisciplineCourseDto createCourse(DisciplineCourseCreateDto createDto) {
        // 1. Получаем связанные сущности через их сервисы
        Discipline discipline = disciplineService.getEntityById(createDto.disciplineId());
        StudyPeriod studyPeriod = studyPeriodService.getEntityById(createDto.studyPeriodId());

        // 2. Проверка на дубликаты по новой логике
        if (disciplineCourseRepository.existsByDisciplineIdAndStudyPeriodId(discipline.getId(), studyPeriod.getId())) {
            throw new IllegalStateException("Курс для дисциплины '" + discipline.getName() + "' и периода '" + studyPeriod.getName() + "' уже существует.");
        }

        // 3. Создаем и сохраняем новый курс
        DisciplineCourse newCourse = new DisciplineCourse();
        newCourse.setDiscipline(discipline);
        newCourse.setStudyPeriod(studyPeriod);
        DisciplineCourse savedCourse = disciplineCourseRepository.save(newCourse);

        return disciplineCourseMapper.toDto(savedCourse);
    }

    @Transactional(readOnly = true)
    public List<DisciplineCourseDto> findAllCoursesByDiscipline(Integer disciplineId) {
        // Сортируем по дате начала периода
        List<DisciplineCourse> courses = disciplineCourseRepository.findByDisciplineIdOrderByStudyPeriod_StartDate(disciplineId);
        return courses.stream()
                .map(disciplineCourseMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public DisciplineCourseDto updateCourse(Integer id, DisciplineCourseUpdateDto updateDto) {
        DisciplineCourse courseToUpdate = getEntityById(id);

        StudyPeriod newStudyPeriod = studyPeriodService.getEntityById(updateDto.studyPeriodId());

        // Проверяем, что мы не создаем дубликат, если период изменился
        if (!courseToUpdate.getStudyPeriod().getId().equals(newStudyPeriod.getId())) {
            if (disciplineCourseRepository.existsByDisciplineIdAndStudyPeriodId(courseToUpdate.getDiscipline().getId(), newStudyPeriod.getId())) {
                throw new IllegalStateException("Курс для данной дисциплины и периода '" + newStudyPeriod.getName() + "' уже существует.");
            }
        }

        courseToUpdate.setStudyPeriod(newStudyPeriod);

        return disciplineCourseMapper.toDto(disciplineCourseRepository.save(courseToUpdate));
    }

    @Transactional
    public void deleteCourse(Integer id) {
        if (!disciplineCourseRepository.existsById(id)) {
            throw new EntityNotFoundException("Курс с id=" + id + " не найден.");
        }
        // TODO: Добавить проверку, не используется ли курс в CurriculumSlot
        disciplineCourseRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<DisciplineCourseDto> findById(Integer id) {
        // TODO: Использовать метод с @EntityGraph для жадной загрузки
        return disciplineCourseRepository.findById(id).map(disciplineCourseMapper::toDto);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    @Transactional(readOnly = true)
    public DisciplineCourse getEntityById(Integer id) {
        return disciplineCourseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Курс с id=" + id + " не найден."));
    }
}