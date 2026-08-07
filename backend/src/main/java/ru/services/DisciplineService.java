package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.discipline.DisciplineCreateDto;
import ru.dto.discipline.DisciplineDto;
import ru.dto.discipline.DisciplineUpdateDto;
import ru.entity.Discipline;
import ru.mapper.DisciplineMapper;
import ru.repository.DisciplineCourseRepository;
import ru.repository.DisciplineRepository;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisciplineService {
    private final DisciplineRepository disciplineRepository;
    private final DisciplineCourseRepository disciplineCourseRepository;
    private final DisciplineMapper disciplineMapper;
    private final ProjectionMaintenance projectionMaintenance;

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
     * Возвращает список всех дисциплин, упорядоченный по названию.
     *
     * <p>Порядок задаётся <b>здесь</b>, а не на фронте: {@code findAll()} отдавал строки в порядке
     * СУБД, из-за чего список переставлялся после каждой правки. Сортировка на бэке делает порядок
     * одинаковым у всех потребителей — а их несколько (справочник, планировщик, выбор в формах).</p>
     *
     * @return Список DTO всех дисциплин.
     */
    @Transactional(readOnly = true)
    public List<DisciplineDto> findAllDisciplines() {
        return disciplineMapper.toDtoList(
                disciplineRepository.findAll(Sort.by(Sort.Order.asc("name").ignoreCase())));
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

        // Название и аббревиатура дисциплины лежат в read-модели снимком (подпись занятия).
        projectionMaintenance.announce(ProjectionSource.DISCIPLINE, id);

        // Загружаем сущность заново вместе с курсами для полного ответа
        return findDisciplineById(updatedDiscipline.getId()).orElseThrow();
    }

    /**
     * Удаляет дисциплину — <b>только если у неё нет курсов</b>.
     *
     * <p>В БД стоит {@code ON DELETE CASCADE}, и удаление дисциплины с курсами уносит цепочку
     * {@code discipline_course → curriculum_slot → assignment → lesson_placement}, то есть
     * <b>стирает расписание дисциплины вместе с ручной раскладкой</b>. Это самый крупный каскад в
     * приложении, и до сих пор он запускался нажатием кнопки в справочнике.</p>
     *
     * <p>Поэтому операция запрещена, а не описана предпросмотром: цену такого удаления невозможно
     * «принять осознанно» в одном диалоге. Тот же выбор сделан для подразделений и корпусов — сначала
     * убрать содержимое, потом удалять контейнер. Курсы удаляются по одному, и там цена называется
     * ({@code /discipline-courses/{id}/deletion-impact}).</p>
     *
     * @param id ID удаляемой дисциплины.
     * @throws IllegalStateException если у дисциплины есть курсы (контроллер отдаёт 409).
     */
    @Transactional
    public void deleteDiscipline(Integer id) {
        if (!disciplineRepository.existsById(id)) {
            // Можно просто ничего не делать, а можно бросить исключение для явной обратной связи
            throw new EntityNotFoundException("Дисциплина с id=" + id + " не найдена.");
        }
        long courses = disciplineCourseRepository.countByDisciplineId(id);
        if (courses > 0) {
            throw new IllegalStateException(
                    "У дисциплины есть учебные курсы (" + courses + "). Удаление снесло бы их планы, "
                            + "назначения и уже размещённые занятия. Сначала удалите курсы.");
        }
        disciplineRepository.deleteById(id);
    }
    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===
    /**
     * Находит сущность Discipline по ID.
     * Этот метод предназначен для использования ТОЛЬКО другими сервисами.
     * Он не возвращает DTO.
     *
     * @return Сущность Discipline.
     * @throws EntityNotFoundException если не найдена.
     */
    @Transactional(readOnly = true)
    Discipline getEntityById(Integer id) { // <-- package-private доступ
        return disciplineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Дисциплина с id=" + id + " не найдена."));
    }
}
