package ru.services.solver.availability;

import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.constraints.ConstraintData;
import ru.services.constraints.AllConstraints;
import ru.services.solver.model.EducatorResource;
import ru.services.solver.model.SchedulableResource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Управляет всеми планируемыми ресурсами (преподавателями, группами, аудиториями)
 * и их состоянием занятости на время работы алгоритма.
 *
 * <p>При инициализации создает "умные карточки" ({@link SchedulableResource}) для каждой
 * сущности из БД и наполняет их постоянными ограничениями.</p>
 */
public final class ResourceAvailabilityManager {

    private final Map<Integer, SchedulableResource> educators;
    private final Map<Integer, SchedulableResource> groups;
    private final Map<Integer, SchedulableResource> auditoriums;

    /**
     * Создает и инициализирует менеджер ресурсов.
     *
     * @param allEducators   полный список преподавателей из БД.
     * @param allGroups      полный список групп из БД.
     * @param allAuditoriums полный список аудиторий из БД.
     * @param allConstraints DTO со всеми постоянными ограничениями, загруженными из БД.
     */
    public ResourceAvailabilityManager(
            List<Educator> allEducators,
            List<Group> allGroups,
            List<Auditorium> allAuditoriums,
            AllConstraints allConstraints
    ) {
        this.educators = initializeEducatorResources(allEducators, allConstraints.educatorConstraints());
        this.groups = initializeGroupResources(allGroups, allConstraints.groupConstraints());
        this.auditoriums = initializeAuditoriumResources(allAuditoriums, allConstraints.auditoriumConstraints());
    }

    /**
     * Инициализирует ресурсы преподавателей.
     */
    private Map<Integer, SchedulableResource> initializeEducatorResources(List<Educator> educators, Map<Integer, List<ConstraintData>> constraintsMap) {
        Map<Integer, SchedulableResource> resourceMap = new HashMap<>();
        for (Educator educator : educators) {
            EducatorResource resource = new EducatorResource(educator);
            List<ConstraintData> constraints = constraintsMap.getOrDefault(educator.getId(), Collections.emptyList());
            for (ConstraintData data : constraints) {
                // Предполагаем, что ограничение на день - это ограничение на все пары в этот день
                for (var pair : ru.enums.TimeSlotPair.values()) {
                    resource.addHardConstraint(new CellForLesson(data.cell().getDate(), pair), data.kind());
                }
            }
            resourceMap.put(educator.getId(), resource);
        }
        return resourceMap;
    }

    /**
     * Инициализирует ресурсы групп.
     */
    private Map<Integer, SchedulableResource> initializeGroupResources(List<Group> groups, Map<Integer, List<ConstraintData>> constraintsMap) {
        Map<Integer, SchedulableResource> resourceMap = new HashMap<>();
        for (Group group : groups) {
            SchedulableResource resource = new SchedulableResource(group.getId(), group.getName());
            List<ConstraintData> constraints = constraintsMap.getOrDefault(group.getId(), Collections.emptyList());
            for (ConstraintData data : constraints) {
                for (var pair : ru.enums.TimeSlotPair.values()) {
                    resource.addHardConstraint(new CellForLesson(data.cell().getDate(), pair), data.kind());
                }
            }
            resourceMap.put(group.getId(), resource);
        }
        return resourceMap;
    }

    /**
     * Инициализирует ресурсы аудиторий.
     */
    private Map<Integer, SchedulableResource> initializeAuditoriumResources(List<Auditorium> auditoriums, Map<Integer, List<ConstraintData>> constraintsMap) {
        Map<Integer, SchedulableResource> resourceMap = new HashMap<>();
        for (Auditorium auditorium : auditoriums) {
            SchedulableResource resource = new SchedulableResource(auditorium.getId(), auditorium.getName());
            List<ConstraintData> constraints = constraintsMap.getOrDefault(auditorium.getId(), Collections.emptyList());
            for (ConstraintData data : constraints) {
                for (var pair : ru.enums.TimeSlotPair.values()) {
                    resource.addHardConstraint(new CellForLesson(data.cell().getDate(), pair), data.kind());
                }
            }
            resourceMap.put(auditorium.getId(), resource);
        }
        return resourceMap;
    }

    /**
     * Возвращает "умную карточку" для преподавателя по его ID.
     */
    public SchedulableResource getEducatorResource(Integer id) {
        return educators.get(id);
    }

    /**
     * Возвращает "умную карточку" для группы по ее ID.
     */
    public SchedulableResource getGroupResource(Integer id) {
        return groups.get(id);
    }

    /**
     * Возвращает "умную карточку" для аудитории по ее ID.
     */
    public SchedulableResource getAuditoriumResource(Integer id) {
        return auditoriums.get(id);
    }
}
