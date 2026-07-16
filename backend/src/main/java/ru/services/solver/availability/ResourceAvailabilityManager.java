package ru.services.solver.availability;

import ru.entity.Auditorium;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.constraints.ConstraintData;
import ru.inter.IMaterialEntity;
import ru.services.constraints.AllConstraints;
import ru.services.solver.model.AuditoriumResource;
import ru.services.solver.model.EducatorResource;
import ru.services.solver.model.SchedulableResource;

import java.util.Collection;
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
    /**
     * Тип уточнён до {@link AuditoriumResource}: комната знает свою вместимость, и подбору это
     * нужно на каждом шаге. Раньше здесь лежал голый ресурс, вместимость спрашивать было не у
     * кого — и три ветки подбора из четырёх её не проверяли.
     */
    private final Map<Integer, AuditoriumResource> auditoriums;

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
            applyConstraints(resource, constraintsMap.getOrDefault(educator.getId(), Collections.emptyList()));
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
            applyConstraints(resource, constraintsMap.getOrDefault(group.getId(), Collections.emptyList()));
            resourceMap.put(group.getId(), resource);
        }
        return resourceMap;
    }

    /**
     * Инициализирует ресурсы аудиторий.
     */
    private Map<Integer, AuditoriumResource> initializeAuditoriumResources(List<Auditorium> auditoriums, Map<Integer, List<ConstraintData>> constraintsMap) {
        Map<Integer, AuditoriumResource> resourceMap = new HashMap<>();
        for (Auditorium auditorium : auditoriums) {
            AuditoriumResource resource = new AuditoriumResource(auditorium);
            applyConstraints(resource, constraintsMap.getOrDefault(auditorium.getId(), Collections.emptyList()));
            resourceMap.put(auditorium.getId(), resource);
        }
        return resourceMap;
    }

    /**
     * Добавляет ограничения ресурсу как есть — по конкретным ячейкам.
     *
     * <p>{@link ConstraintData} уже развёрнуты в нужные ячейки в
     * {@code ConstraintServiceImpl.expandToCells}: пер-парное ограничение — одна ячейка
     * {@code (дата, пара)}, целодневное — все 4 пары дня. Поэтому здесь НЕ раскидываем по
     * всем парам (иначе ограничение на одну пару заблокировало бы весь день — так было).</p>
     */
    private static void applyConstraints(SchedulableResource resource, List<ConstraintData> constraints) {
        for (ConstraintData data : constraints) {
            resource.addHardConstraint(data.cell(), data.kind());
        }
    }

    /**
     * Возвращает "умную карточку" ресурса для любой сущности, реализующей IMaterialEntity.
     * Этот метод необходим для унифицированного доступа к ресурсам из сервиса экспорта.
     *
     * @param entity Сущность (Educator, Group или Auditorium).
     * @return Соответствующий SchedulableResource.
     * @throws IllegalArgumentException если передан неизвестный тип сущности.
     */
    public SchedulableResource getResource(IMaterialEntity entity) {
        if (entity instanceof Educator) {
            return educators.get(entity.getId());
        } else if (entity instanceof Group) {
            return groups.get(entity.getId());
        } else if (entity instanceof Auditorium) {
            return auditoriums.get(entity.getId());
        } else {
            // Безопасное поведение: если появится новый тип, мы сразу об этом узнаем.
            throw new IllegalArgumentException("Неизвестный тип сущности для поиска ресурса: " + entity.getClass().getName());
        }
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
    public AuditoriumResource getAuditoriumResource(Integer id) {
        return auditoriums.get(id);
    }

    /**
     * Все комнаты — область поиска, когда учебный план не сузил её ни жёстким требованием, ни
     * пулом. Раньше такой области не существовало: резервная ветка подбора не искала комнату
     * вовсе, а выдавала базовую аудиторию группы вслепую, из-за чего поток на 116 человек
     * оказывался в кабинете на 70, а свободная сотня рядом не рассматривалась.
     */
    public Collection<AuditoriumResource> allAuditoriumResources() {
        return Collections.unmodifiableCollection(auditoriums.values());
    }

    /**
     * Очищает динамическое расписание (занятость) у всех ресурсов.
     * Постоянные ограничения (Hard Constraints) сохраняются.
     */
    public void clearAllResources() {
        educators.values().forEach(SchedulableResource::clearSchedule);
        groups.values().forEach(SchedulableResource::clearSchedule);
        auditoriums.values().forEach(SchedulableResource::clearSchedule);
    }
}
