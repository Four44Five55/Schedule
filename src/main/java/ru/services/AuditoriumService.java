package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.auditorium.AuditoriumCreateDto;
import ru.dto.auditorium.AuditoriumDto;
import ru.dto.auditorium.AuditoriumUpdateDto;
import ru.entity.Auditorium;
import ru.entity.Building;
import ru.mapper.AuditoriumMapper;
import ru.repository.AuditoriumRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для управления Аудиториями.
 */
@Service
@RequiredArgsConstructor
public class AuditoriumService {

    private final AuditoriumRepository auditoriumRepository;
    private final BuildingService buildingService;
    private final AuditoriumMapper auditoriumMapper;

    // === ПУБЛИЧНЫЕ МЕТОДЫ (ДЛЯ API) ===

    /**
     * Создает новую аудиторию.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданной аудитории.
     */
    @Transactional
    public AuditoriumDto createAuditorium(AuditoriumCreateDto createDto) {
        // Проверяем, не существует ли уже аудитория с таким именем в данном корпусе
        if (auditoriumRepository.existsByNameAndBuildingId(createDto.name(), createDto.buildingId())) {
            throw new IllegalStateException("Аудитория с названием '" + createDto.name() + "' уже существует в этом корпусе.");
        }

        // Получаем сущность корпуса через BuildingService
        Building building = buildingService.findEntityById(createDto.buildingId());

        Auditorium newAuditorium = new Auditorium();
        newAuditorium.setName(createDto.name());
        newAuditorium.setCapacity(createDto.capacity());
        newAuditorium.setBuilding(building);

        return auditoriumMapper.toDto(auditoriumRepository.save(newAuditorium));
    }

    /**
     * Обновляет существующую аудиторию.
     *
     * @param id        ID обновляемой аудитории.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленной аудитории.
     */
    @Transactional
    public AuditoriumDto updateAuditorium(Integer id, AuditoriumUpdateDto updateDto) {
        Auditorium auditoriumToUpdate = findEntityById(id);

        // Проверяем на уникальность, если имя или корпус изменились
        if (!auditoriumToUpdate.getName().equals(updateDto.name()) || !auditoriumToUpdate.getBuilding().getId().equals(updateDto.buildingId())) {
            if (auditoriumRepository.existsByNameAndBuildingId(updateDto.name(), updateDto.buildingId())) {
                throw new IllegalStateException("Аудитория с названием '" + updateDto.name() + "' уже существует в целевом корпусе.");
            }
        }

        // Если ID корпуса изменился, получаем новую сущность корпуса
        if (!auditoriumToUpdate.getBuilding().getId().equals(updateDto.buildingId())) {
            Building newBuilding = buildingService.findEntityById(updateDto.buildingId());
            auditoriumToUpdate.setBuilding(newBuilding);
        }

        auditoriumToUpdate.setName(updateDto.name());
        auditoriumToUpdate.setCapacity(updateDto.capacity());

        return auditoriumMapper.toDto(auditoriumRepository.save(auditoriumToUpdate));
    }

    /**
     * Находит аудиторию по ID с полной информацией о ее местоположении.
     */
    @Transactional(readOnly = true)
    public Optional<AuditoriumDto> findById(Integer id) {
        return auditoriumRepository.findWithDetailsById(id).map(auditoriumMapper::toDto);
    }

    /**
     * Возвращает список всех аудиторий.
     */
    @Transactional(readOnly = true)
    public List<AuditoriumDto> findAll() {
        return auditoriumRepository.findAll().stream()
                .map(auditoriumMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Удаляет аудиторию по ID.
     */
    @Transactional
    public void deleteAuditorium(Integer id) {
        if (!auditoriumRepository.existsById(id)) {
            throw new EntityNotFoundException("Аудитория с id=" + id + " не найдена.");
        }
        // TODO: Добавить проверку, не используется ли аудитория в каких-либо
        // постоянных требованиях (curriculum_slot) или пулах (auditorium_pool), перед удалением.
        auditoriumRepository.deleteById(id);
    }

    // === СЛУЖЕБНЫЙ МЕТОД (для других сервисов) ===

    /**
     * Находит сущность Auditorium по ID. Для внутреннего использования другими сервисами (например, GroupService).
     *
     * @param id ID аудитории.
     * @return Сущность Auditorium.
     * @throws EntityNotFoundException если аудитория не найдена.
     */
    @Transactional(readOnly = true)
    public Auditorium findEntityById(Integer id) {
        return auditoriumRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Аудитория с id=" + id + " не найдена."));
    }

    /**
     * Находит все сущности Auditorium по списку их ID.
     * Предназначен для использования другими сервисами (AuditoriumPoolService).
     *
     * @param ids Список ID аудиторий.
     * @return Список найденных сущностей Auditorium.
     * @throws EntityNotFoundException если хотя бы одна аудитория не найдена.
     */
    @Transactional(readOnly = true)
    public List<Auditorium> findAllEntitiesByIds(List<Integer> ids) {
        List<Auditorium> auditoriums = auditoriumRepository.findAllById(ids);
        // Проверяем, что количество найденных сущностей совпадает с количеством запрошенных ID
        if (auditoriums.size() != ids.size()) {
            // Эта проверка важна, чтобы убедиться в целостности данных
            throw new EntityNotFoundException("Одна или несколько аудиторий из списка ID не найдены.");
        }
        return auditoriums;
    }
}