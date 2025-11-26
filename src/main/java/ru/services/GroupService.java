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
import ru.repository.AuditoriumRepository;
import ru.repository.GroupRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final GroupMapper groupMapper;

    // === ПУБЛИЧНЫЕ МЕТОДЫ (API) ===

    @Transactional
    public GroupDto createGroup(GroupCreateDto createDto) {
        if (groupRepository.existsByName(createDto.name())) {
            throw new IllegalStateException("Группа с названием '" + createDto.name() + "' уже существует.");
        }

        Group newGroup = new Group();
        newGroup.setName(createDto.name());
        newGroup.setSize(createDto.size());

        if (createDto.baseAuditoriumId() != null) {
            Auditorium baseAuditorium = auditoriumRepository.findById(createDto.baseAuditoriumId())
                    .orElseThrow(() -> new EntityNotFoundException("Аудитория с id=" + createDto.baseAuditoriumId() + " не найдена."));
            newGroup.setBaseAuditorium(baseAuditorium);
        }

        return groupMapper.toDto(groupRepository.save(newGroup));
    }

    @Transactional
    public GroupDto updateGroup(Integer groupId, GroupUpdateDto updateDto) {
        Group groupToUpdate = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Группа с id=" + groupId + " не найдена."));

        groupToUpdate.setName(updateDto.name());
        groupToUpdate.setSize(updateDto.size());

        if (updateDto.baseAuditoriumId() != null) {
            Auditorium baseAuditorium = auditoriumRepository.findById(updateDto.baseAuditoriumId())
                    .orElseThrow(() -> new EntityNotFoundException("Аудитория с id=" + updateDto.baseAuditoriumId() + " не найдена."));
            groupToUpdate.setBaseAuditorium(baseAuditorium);
        } else {
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

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    /**
     * Находит все сущности Group по списку их ID.
     * Предназначен для использования другими сервисами (например, StudyStreamService).
     *
     * @param groupIds Список ID групп.
     * @return Список найденных сущностей Group.
     * @throws EntityNotFoundException если хотя бы одна группа не найдена.
     */
    @Transactional(readOnly = true)
    public List<Group> findAllEntitiesByIds(List<Integer> groupIds) {
        List<Group> groups = groupRepository.findAllById(groupIds);
        if (groups.size() != groupIds.size()) {
            // Можно добавить более детальную логику поиска недостающих ID
            throw new EntityNotFoundException("Одна или несколько групп из списка ID не найдены.");
        }
        return groups;
    }
}
