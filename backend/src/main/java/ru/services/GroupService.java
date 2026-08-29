package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.dto.group.GroupCreateDto;
import ru.dto.group.GroupDto;
import ru.dto.group.GroupUpdateDto;
import ru.entity.Auditorium;
import ru.entity.Group;
import ru.entity.OrgUnit;
import ru.mapper.GroupMapper;
import ru.repository.GroupRepository;
import ru.services.orgunit.OrgUnitService;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

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
    private final ProjectionMaintenance projectionMaintenance;
    private final OrgUnitService orgUnitService;

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
            throw new DuplicateException("Группа с названием '" + createDto.name() + "' уже существует.");
        }

        Group newGroup = new Group();
        newGroup.setName(createDto.name());
        newGroup.setSize(createDto.size());

        // Если указана базовая аудитория, получаем ее через AuditoriumService
        if (createDto.baseAuditoriumId() != null) {
            Auditorium baseAuditorium = auditoriumService.getEntityById(createDto.baseAuditoriumId());
            newGroup.setBaseAuditorium(baseAuditorium);
        }
        newGroup.setEnrollmentYear(createDto.enrollmentYear());
        newGroup.setOrgUnit(resolveOrgUnit(createDto.orgUnitId()));

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
        Group groupToUpdate = getEntityById(groupId);

        // Проверяем уникальность имени, если оно было изменено
        if (!groupToUpdate.getName().equals(updateDto.name())) {
            groupRepository.findByName(updateDto.name()).ifPresent(existing -> {
                throw new DuplicateException("Группа с названием '" + updateDto.name() + "' уже существует.");
            });
        }

        groupToUpdate.setName(updateDto.name());
        groupToUpdate.setSize(updateDto.size());

        // Обновляем базовую аудиторию через сервис
        if (updateDto.baseAuditoriumId() != null) {
            Auditorium baseAuditorium = auditoriumService.getEntityById(updateDto.baseAuditoriumId());
            groupToUpdate.setBaseAuditorium(baseAuditorium);
        } else {
            // Если ID не передан, значит, связь нужно убрать
            groupToUpdate.setBaseAuditorium(null);
        }

        // null — снять значение: год набора в read-модели не хранится, перепроекция не нужна.
        groupToUpdate.setEnrollmentYear(updateDto.enrollmentYear());
        // null — открепить: подразделения нет в read-модели, перепроекция не нужна.
        groupToUpdate.setOrgUnit(resolveOrgUnit(updateDto.orgUnitId()));

        GroupDto updated = groupMapper.toDto(groupRepository.save(groupToUpdate));
        // Имя группы в read-модели — снимок: без перепроекции переименование не дошло бы
        // до сетки, отчётов и Excel.
        projectionMaintenance.announce(ProjectionSource.GROUP, groupId);
        return updated;
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
            throw new NotFoundException("Группа с id=" + groupId + " не найдена.");
        }
        // Цена удаления не называется заранее (общий долг CRUD-слоя, см. FOLLOWUPS: «Удаление
        // без предупреждения»). Образец решения — delete-impact у аудитории и назначения.

        // ОБЯЗАТЕЛЬНО до удаления: `stream_groups` уходит каскадом, и после коммита связь
        // «группа → размещения» уже не найти. Сами занятия остаются (они у потока), но строка
        // read-модели, выписанная на эту группу, стала бы вечным занятием-призраком.
        projectionMaintenance.announce(ProjectionSource.GROUP, groupId);
        groupRepository.deleteById(groupId);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (ДЛЯ ДРУГИХ СЕРВИСОВ) ===

    /**
     * Находит сущность Group по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public Group getEntityById(Integer id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Группа с id=" + id + " не найдена."));
    }

    /**
     * Находит все сущности Group.
     * Предназначен для использования внутри этого сервиса и другими сервисами.
     */
    @Transactional(readOnly = true)
    public List<Group> getAllEntities() {
        return groupRepository.findAll();
    }

    /**
     * Находит все сущности Group по списку их ID.
     * Предназначен для использования StudyStreamService.
     */
    @Transactional(readOnly = true)
    public List<Group> getAllEntitiesByIds(List<Integer> groupIds) {
        List<Group> groups = groupRepository.findAllById(groupIds);
        if (groups.size() != groupIds.size()) {
            throw new NotFoundException("Одна или несколько групп из списка ID не найдены.");
        }
        return groups;
    }

    /**
     * Подразделение по id; {@code null} — группа не привязана ни к одному.
     */
    private OrgUnit resolveOrgUnit(Integer orgUnitId) {
        return orgUnitId == null ? null : orgUnitService.getEntityById(orgUnitId);
    }
}
