package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.discipline.DisciplineCreateDto;
import ru.dto.discipline.DisciplineDto;
import ru.dto.discipline.DisciplineUpdateDto;
import ru.entity.Discipline;
import ru.mapper.DisciplineMapper;
import ru.repository.DisciplineRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisciplineService {
    private final DisciplineRepository disciplineRepository;
    private final DisciplineMapper disciplineMapper;

    /**
     * Создает новую дисциплину.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданной дисциплины.
     * @throws IllegalStateException если дисциплина с таким названием уже существует.
     */
    @Transactional
    public DisciplineDto createDiscipline(DisciplineCreateDto createDto) {
        if (disciplineRepository.existsByName(createDto.name())) {
            throw new IllegalStateException("Дисциплина с названием '" + createDto.name() + "' уже существует.");
        }

        Discipline discipline = disciplineMapper.toEntity(createDto);
        Discipline savedDiscipline = disciplineRepository.save(discipline);

        // Конвертируем в DTO для ответа. Список курсов будет пустым, что корректно.
        return disciplineMapper.toDto(savedDiscipline);
    }

    /**
     * Возвращает список всех дисциплин.
     *
     * @return Список DTO всех дисциплин.
     */
    @Transactional(readOnly = true)
    public List<DisciplineDto> findAllDisciplines() {
        return disciplineMapper.toDtoList(disciplineRepository.findAll());
    }

    /**
     * Находит дисциплину по ID вместе со всеми ее курсами.
     *
     * @param id ID дисциплины.
     * @return Optional с полным DTO дисциплины.
     */
    @Transactional(readOnly = true)
    public Optional<DisciplineDto> findDisciplineById(Integer id) {
        return disciplineRepository.findByIdWithCourses(id)
                .map(disciplineMapper::toDto);
    }

    /**
     * Обновляет существующую дисциплину.
     *
     * @param id        ID обновляемой дисциплины.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленной дисциплины.
     * @throws EntityNotFoundException если дисциплина не найдена.
     * @throws IllegalStateException   если новое имя уже занято другой дисциплиной.
     */
    @Transactional
    public DisciplineDto updateDiscipline(Integer id, DisciplineUpdateDto updateDto) {
        Discipline disciplineToUpdate = disciplineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Дисциплина с id=" + id + " не найдена."));

        // Проверяем, не занято ли новое имя другой дисциплиной
        disciplineRepository.findByName(updateDto.name()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalStateException("Дисциплина с названием '" + updateDto.name() + "' уже существует.");
            }
        });

        disciplineToUpdate.setName(updateDto.name());
        disciplineToUpdate.setAbbreviation(updateDto.abbreviation());

        Discipline updatedDiscipline = disciplineRepository.save(disciplineToUpdate);

        // Загружаем сущность заново вместе с курсами для полного ответа
        return findDisciplineById(updatedDiscipline.getId()).orElseThrow();
    }

    /**
     * Удаляет дисциплину.
     * За счет ON DELETE CASCADE в БД, будут также удалены все связанные DisciplineCourse,
     * а за ними CurriculumSlot и т.д.
     *
     * @param id ID удаляемой дисциплины.
     */
    @Transactional
    public void deleteDiscipline(Integer id) {
        if (!disciplineRepository.existsById(id)) {
            // Можно просто ничего не делать, а можно бросить исключение для явной обратной связи
            throw new EntityNotFoundException("Дисциплина с id=" + id + " не найдена.");
        }
        disciplineRepository.deleteById(id);
    }
}
