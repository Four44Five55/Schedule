package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.dto.themeLesson.ThemeLessonCreateDto;
import ru.dto.themeLesson.ThemeLessonDto;
import ru.dto.themeLesson.ThemeLessonUpdateDto;
import ru.entity.Discipline;
import ru.entity.logicSchema.ThemeLesson;
import ru.mapper.ThemeLessonMapper;
import ru.repository.ThemeLessonRepository;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для управления темами занятий (ThemeLesson).
 */
@Service
@RequiredArgsConstructor
public class ThemeLessonService {

    private final ThemeLessonRepository themeLessonRepository;
    private final DisciplineService disciplineService;
    private final ThemeLessonMapper themeLessonMapper;
    private final ProjectionMaintenance projectionMaintenance;

    /**
     * Создает новую тему для дисциплины.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданной темы.
     */
    @Transactional
    public ThemeLessonDto createTheme(ThemeLessonCreateDto createDto) {
        if (themeLessonRepository.existsByDisciplineIdAndThemeNumber(createDto.disciplineId(), createDto.themeNumber())) {
            throw new DuplicateException("Тема с номером '" + createDto.themeNumber() + "' уже существует для данной дисциплины.");
        }

        Discipline discipline = disciplineService.getEntityById(createDto.disciplineId());

        ThemeLesson newTheme = new ThemeLesson();
        newTheme.setThemeNumber(createDto.themeNumber());
        newTheme.setTitle(createDto.title());
        newTheme.setDiscipline(discipline);

        return themeLessonMapper.toDto(themeLessonRepository.save(newTheme));
    }

    /**
     * Находит тему по ее ID.
     *
     * @param id ID темы.
     * @return Optional с DTO темы.
     */
    @Transactional(readOnly = true)
    public Optional<ThemeLessonDto> findById(Integer id) {
        return themeLessonRepository.findById(id).map(themeLessonMapper::toDto);
    }

    /**
     * Возвращает список всех тем для указанной дисциплины, отсортированный по номеру темы.
     *
     * @param disciplineId ID дисциплины.
     * @return Список DTO тем.
     */
    @Transactional(readOnly = true)
    public List<ThemeLessonDto> findAllByDisciplineId(Integer disciplineId) {
        return themeLessonRepository.findByDisciplineIdOrderByThemeNumber(disciplineId)
                .stream()
                .map(themeLessonMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Обновляет существующую тему.
     *
     * @param id        ID обновляемой темы.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленной темы.
     */
    @Transactional
    public ThemeLessonDto updateTheme(Integer id, ThemeLessonUpdateDto updateDto) {
        ThemeLesson themeToUpdate = getEntityById(id);

        // Проверяем, не создаем ли мы дубликат при смене номера или дисциплины
        if (!themeToUpdate.getDiscipline().getId().equals(updateDto.disciplineId()) ||
                !themeToUpdate.getThemeNumber().equals(updateDto.themeNumber())) {

            if (themeLessonRepository.existsByDisciplineIdAndThemeNumber(updateDto.disciplineId(), updateDto.themeNumber())) {
                throw new DuplicateException("Тема с номером '" + updateDto.themeNumber() + "' уже существует для целевой дисциплины.");
            }
        }

        // Если ID дисциплины изменился, получаем новую сущность
        if (!themeToUpdate.getDiscipline().getId().equals(updateDto.disciplineId())) {
            Discipline newDiscipline = disciplineService.getEntityById(updateDto.disciplineId());
            themeToUpdate.setDiscipline(newDiscipline);
        }

        themeToUpdate.setThemeNumber(updateDto.themeNumber());
        themeToUpdate.setTitle(updateDto.title());

        ThemeLessonDto updated = themeLessonMapper.toDto(themeLessonRepository.save(themeToUpdate));
        // Номер и название темы read-модель хранит снимком (подпись занятия в сетке и в Excel).
        projectionMaintenance.announce(ProjectionSource.THEME, id);
        return updated;
    }

    /**
     * Удаляет тему по ID.
     *
     * @param id ID удаляемой темы.
     */
    @Transactional
    public void deleteTheme(Integer id) {
        if (!themeLessonRepository.existsById(id)) {
            throw new NotFoundException("Тема с id=" + id + " не найдена.");
        }
        // TODO: Добавить проверку, не используется ли тема в CurriculumSlot, перед удалением.
        themeLessonRepository.deleteById(id);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    /**
     * Находит сущность ThemeLesson по ID. Для внутреннего использования другими сервисами.
     *
     * @param id ID темы.
     * @return Сущность ThemeLesson.
     * @throws NotFoundException если тема не найдена.
     */
    @Transactional(readOnly = true)
    public ThemeLesson getEntityById(Integer id) {
        return themeLessonRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Тема с id=" + id + " не найдена."));
    }
}
