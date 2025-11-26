package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.group.GroupCreateDto;
import ru.dto.group.GroupDto;
import ru.dto.group.GroupUpdateDto;
import ru.entity.Auditorium;
import ru.entity.Group;
import ru.mapper.GroupMapper;
import ru.repository.GroupRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для управления Учебными группами.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final AuditoriumService auditoriumService;
    private final GroupMapper groupMapper;

    // === ПУБЛИЧНЫЕ МЕТОДЫ (ДЛЯ API) ===

    /**
     * Создает новую учебную группу.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданной группы.
     */
    @Transactional
    public GroupDto createGroup(GroupCreateDto createDto) {
        if (groupRepository.existsByName(createDto.name())) {
            throw new IllegalStateException("Группа с названием '" + createDto.name() + "' уже существует.");
        }

        Group newGroup = new Group();
        newGroup.setName(createDto.name());
        newGroup.setSize(createDto.size());

        // Если указана базовая аудитория, получаем ее через AuditoriumService
        if (createDto.baseAuditoriumId() != null) {
            Auditorium baseAuditorium = auditoriumService.findEntityById(createDto.baseAuditoriumId());
            newGroup.setBaseAuditorium(baseAuditorium);
        }

        Group savedGroup = groupRepository.save(newGroup);
        return groupMapper.toDto(savedGroup);
    }

    /**
     * Обновляет существующую учебную группу.
     *
     * @param groupId   ID обновляемой группы.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленной группы.
     */
    @Transactional
    public GroupDto updateGroup(Integer groupId, GroupUpdateDto updateDto) {
        Group groupToUpdate = findEntityById(groupId);

        // Проверяем уникальность имени, если оно было изменено
        if (!groupToUpdate.getName().equals(updateDto.name())) {
            groupRepository.findByName(updateDto.name()).ifPresent(existing -> {
                throw new IllegalStateException("Группа с названием '" + updateDto.name() + "' уже существует.");
            });
        }

        groupToUpdate.setName(updateDto.name());
        groupToUpdate.setSize(updateDto.size());

        // Обновляем базовую аудиторию через сервис
        if (updateDto.baseAuditoriumId() != null) {
            Auditorium baseAuditorium = auditoriumService.findEntityById(updateDto.baseAuditoriumId());
            groupToUpdate.setBaseAuditorium(baseAuditorium);
        } else {
            // Если ID не передан, значит, связь нужно убрать
            groupToUpdate.setBaseAuditorium(null);
        }

        return groupMapper.toDto(groupRepository.save(groupToUpdate));
    }

    @Transactional(readOnly = true)
    public List<GroupDto> findAll() {
        return groupRepository.findAll().stream()
                .map(groupMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<GroupDto> findById(Integer groupId) {
        return groupRepository.findById(groupId).map(groupMapper::toDto);
    }

    @Transactional
    public void deleteGroup(Integer groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new EntityNotFoundException("Группа с id=" + groupId + " не найдена.");
        }
        // TODO: Добавить проверку, не используется ли группа в StudyStream, перед удалением.
        groupRepository.deleteById(groupId);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (ДЛЯ ДРУГИХ СЕРВИСОВ) ===

    /**
     * Находит сущность Group по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public Group findEntityById(Integer id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Группа с id=" + id + " не найдена."));
    }

    /**
     * Находит все сущности Group по списку их ID.
     * Предназначен для использования StudyStreamService.
     */
    @Transactional(readOnly = true)
    public List<Group> findAllEntitiesByIds(List<Integer> groupIds) {
        List<Group> groups = groupRepository.findAllById(groupIds);
        if (groups.size() != groupIds.size()) {
            throw new EntityNotFoundException("Одна или несколько групп из списка ID не найдены.");
        }
        return groups;
    }
}
