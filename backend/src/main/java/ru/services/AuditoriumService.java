package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.exceptions.InUseException;
import ru.dto.auditorium.AuditoriumCreateDto;
import ru.dto.auditorium.AuditoriumDeletionImpactDto;
import ru.dto.auditorium.AuditoriumDto;
import ru.dto.auditorium.AuditoriumUpdateDto;
import ru.entity.Auditorium;
import ru.entity.AuditoriumPurpose;
import ru.entity.Building;
import ru.entity.Feature;
import ru.entity.OrgUnit;
import ru.mapper.AuditoriumMapper;
import ru.repository.AuditoriumRepository;
import ru.repository.CurriculumSlotRepository;
import ru.repository.GroupRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.orgunit.OrgUnitService;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

import java.util.HashSet;
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
    private final AuditoriumPurposeService purposeService;
    private final FeatureService featureService;
    private final AuditoriumMapper auditoriumMapper;
    private final ProjectionMaintenance projectionMaintenance;
    private final OrgUnitService orgUnitService;
    // Репозитории, а не сервисы: GroupService сам зависит от AuditoriumService — через сервисы
    // получился бы цикл бинов. Здесь нужны только счётчики.
    private final CurriculumSlotRepository curriculumSlotRepository;
    private final GroupRepository groupRepository;
    private final LessonPlacementRepository placementRepository;

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
            throw new DuplicateException("Аудитория с названием '" + createDto.name() + "' уже существует в этом корпусе.");
        }

        // Получаем сущность корпуса через BuildingService
        Building building = buildingService.getEntityById(createDto.buildingId());

        Auditorium newAuditorium = new Auditorium();
        newAuditorium.setName(createDto.name());
        newAuditorium.setCapacity(createDto.capacity());
        newAuditorium.setBuilding(building);

        if (createDto.purposeId() != null) {
            AuditoriumPurpose purpose = purposeService.getEntityById(createDto.purposeId());
            newAuditorium.setPurpose(purpose);
        }
        if (createDto.featureIds() != null && !createDto.featureIds().isEmpty()) {
            List<Feature> features = featureService.getAllEntitiesByIds(createDto.featureIds());
            newAuditorium.setFeatures(new HashSet<>(features));
        }
        newAuditorium.setOrgUnit(resolveOrgUnit(createDto.orgUnitId()));

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
        Auditorium auditoriumToUpdate = getEntityById(id);

        // Проверяем на уникальность, если имя или корпус изменились
        if (!auditoriumToUpdate.getName().equals(updateDto.name()) || !auditoriumToUpdate.getBuilding().getId().equals(updateDto.buildingId())) {
            if (auditoriumRepository.existsByNameAndBuildingId(updateDto.name(), updateDto.buildingId())) {
                throw new DuplicateException("Аудитория с названием '" + updateDto.name() + "' уже существует в целевом корпусе.");
            }
        }

        // Если ID корпуса изменился, получаем новую сущность корпуса
        if (!auditoriumToUpdate.getBuilding().getId().equals(updateDto.buildingId())) {
            Building newBuilding = buildingService.getEntityById(updateDto.buildingId());
            auditoriumToUpdate.setBuilding(newBuilding);
        }

        auditoriumToUpdate.setName(updateDto.name());
        auditoriumToUpdate.setCapacity(updateDto.capacity());

        if (updateDto.purposeId() != null) {
            AuditoriumPurpose purpose = purposeService.getEntityById(updateDto.purposeId());
            auditoriumToUpdate.setPurpose(purpose);
        } else {
            auditoriumToUpdate.setPurpose(null);
        }

        auditoriumToUpdate.getFeatures().clear();
        if (updateDto.featureIds() != null && !updateDto.featureIds().isEmpty()) {
            List<Feature> newFeatures = featureService.getAllEntitiesByIds(updateDto.featureIds());
            auditoriumToUpdate.getFeatures().addAll(newFeatures);
        }
        // null — открепить. Перепроекции не требует: подразделения в schedule_view нет вовсе
        // (CQRS_ARCHITECTURE, «Что в проекцию НЕ кладут»), денормализовано только имя комнаты.
        auditoriumToUpdate.setOrgUnit(resolveOrgUnit(updateDto.orgUnitId()));

        AuditoriumDto updated = auditoriumMapper.toDto(auditoriumRepository.save(auditoriumToUpdate));
        // Название аудитории в read-модели — снимок (сетка, тултипы, Excel).
        projectionMaintenance.announce(ProjectionSource.AUDITORIUM, id);
        return updated;
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
     * Предпросмотр последствий удаления: сколько занятий останется без комнаты (и сколько из них
     * закреплено вручную), не запрещает ли удаление учебный план, у скольких групп аудитория
     * числится домашней. Состояние не меняет.
     *
     * @see AuditoriumDeletionImpactDto
     */
    @Transactional(readOnly = true)
    public AuditoriumDeletionImpactDto deleteImpact(Integer id) {
        Auditorium auditorium = getEntityById(id);
        long referencedBySlots = curriculumSlotRepository.countReferencingAuditorium(id);
        return new AuditoriumDeletionImpactDto(
                auditorium.getId(),
                auditorium.getName(),
                referencedBySlots == 0, // deletable: правило считается ЗДЕСЬ, фронт его не выводит
                placementRepository.countByAuditoriumId(id),
                placementRepository.countLockedByAuditoriumId(id),
                referencedBySlots,
                groupRepository.countByBaseAuditoriumId(id));
    }

    /**
     * Удаляет аудиторию по ID.
     *
     * <p>Отказывает, если на аудиторию ссылается учебный план (требуемая/приоритетная в слоте):
     * эти FK идут без каскада, БД удалить не даст, и раньше наружу летел сырой 500. Занятость в
     * расписании удалению НЕ мешает — но занятия останутся без комнаты, поэтому цена названа в
     * {@link #deleteImpact} и подтверждается в UI.</p>
     */
    @Transactional
    public void deleteAuditorium(Integer id) {
        // Одно правило — один источник: и предпросмотр, и отказ смотрят на тот же deletable
        // (иначе UI и бэк со временем разошлись бы в том, что считать «нельзя»).
        AuditoriumDeletionImpactDto impact = deleteImpact(id); // бросит 404, если аудитории нет
        if (!impact.deletable()) {
            throw new InUseException(
                    "Аудиторию нельзя удалить: она указана требуемой или приоритетной в "
                            + impact.slotsRequiringIt() + " занятиях учебного плана. "
                            + "Сначала уберите её из плана.");
        }

        // ОБЯЗАТЕЛЬНО до удаления: `placement_auditoriums` уходит каскадом, и после коммита
        // связь «аудитория → размещения» пропадёт. Занятия остаются (уже без комнаты) — и
        // read-модель должна честно это показать, а не старое название удалённой аудитории.
        projectionMaintenance.announce(ProjectionSource.AUDITORIUM, id);
        auditoriumRepository.deleteById(id);
    }

    /**
     * Подразделение по id; {@code null} — комната не закреплена ни за какой кафедрой.
     *
     * <p>Через сервис, а не репозиторий: 404 на несуществующем id — его правило, и второй вход,
     * который об этом не знает, отдал бы вместо него нарушение внешнего ключа.</p>
     */
    private OrgUnit resolveOrgUnit(Integer orgUnitId) {
        return orgUnitId == null ? null : orgUnitService.getEntityById(orgUnitId);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    /**
     * Находит сущность Auditorium по ID. Для внутреннего использования другими сервисами (например, GroupService).
     *
     * @param id ID аудитории.
     * @return Сущность Auditorium.
     * @throws NotFoundException если аудитория не найдена.
     */
    @Transactional(readOnly = true)
    public Auditorium getEntityById(Integer id) {
        return auditoriumRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Аудитория с id=" + id + " не найдена."));
    }

    /**
     * Находит все сущности Auditorium.
     * Предназначен для использования внутри этого сервиса и другими сервисами.
     */
    @Transactional(readOnly = true)
    public List<Auditorium> getAllEntities() {
        return auditoriumRepository.findAll();
    }

    /**
     * Находит все сущности Auditorium по списку их ID.
     * Предназначен для использования другими сервисами (AuditoriumPoolService).
     *
     * @param ids Список ID аудиторий.
     * @return Список найденных сущностей Auditorium.
     * @throws NotFoundException если хотя бы одна аудитория не найдена.
     */
    @Transactional(readOnly = true)
    public List<Auditorium> getAllEntitiesByIds(List<Integer> ids) {
        List<Auditorium> auditoriums = auditoriumRepository.findAllById(ids);
        // Проверяем, что количество найденных сущностей совпадает с количеством запрошенных ID
        if (auditoriums.size() != ids.size()) {
            // Эта проверка важна, чтобы убедиться в целостности данных
            throw new NotFoundException("Одна или несколько аудиторий из списка ID не найдены.");
        }
        return auditoriums;
    }
}