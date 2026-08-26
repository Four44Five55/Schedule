package ru.services.orgunit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.exceptions.InUseException;
import ru.exceptions.RuleViolationException;
import ru.dto.orgUnit.OrgUnitCreateDto;
import ru.dto.orgUnit.OrgUnitDeletionImpactDto;
import ru.dto.orgUnit.OrgUnitDto;
import ru.dto.orgUnit.OrgUnitUpdateDto;
import ru.entity.OrgUnit;
import ru.enums.OrgUnitType;
import ru.mapper.OrgUnitMapper;
import ru.repository.AuditoriumRepository;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.OrgUnitRepository;
import ru.services.orgunit.OrgUnitHierarchyRule.UnitNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CRUD подразделений и цена их удаления.
 *
 * <p>Допустимость родителя сервис не решает сам — спрашивает у чистого
 * {@link OrgUnitHierarchyRule} (правило тестируется юнитами без Spring и БД), а сам лишь
 * загружает дерево и превращает семантику отказа в текст для пользователя.</p>
 *
 * <p><b>Проекция здесь ни при чём.</b> Подразделение не денормализовано в {@code schedule_view},
 * поэтому переименование не требует {@code ProjectionMaintenance.announce} — в отличие от
 * преподавателя, группы или аудитории. Если подразделение когда-нибудь попадёт в снимок
 * расписания, эта связь станет обязательной: понадобится своя константа
 * {@code ProjectionSource} и объявление устаревания здесь.</p>
 */
@Service
@RequiredArgsConstructor
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;
    private final OrgUnitMapper orgUnitMapper;
    // Репозитории, а не сервисы: нужны только счётчики цены удаления (по образцу BuildingService).
    private final EducatorRepository educatorRepository;
    private final GroupRepository groupRepository;
    // Репозиторий, а не AuditoriumService: тот сам зависит от OrgUnitService (кафедра-владелец
    // комнаты) — через сервисы получился бы цикл бинов.
    private final AuditoriumRepository auditoriumRepository;

    private final OrgUnitHierarchyRule hierarchyRule = new OrgUnitHierarchyRule();

    /**
     * Все подразделения плоским списком (дерево собирает фронт).
     */
    @Transactional(readOnly = true)
    public List<OrgUnitDto> findAll() {
        return orgUnitRepository.findAllByOrderByNameAsc().stream()
                .map(orgUnitMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<OrgUnitDto> findById(Integer id) {
        return orgUnitRepository.findWithDetailsById(id).map(orgUnitMapper::toDto);
    }

    @Transactional
    public OrgUnitDto create(OrgUnitCreateDto createDto) {
        List<OrgUnit> existing = orgUnitRepository.findAllByOrderByNameAsc();
        requireValidHierarchy(null, createDto.type(), createDto.parentId(), existing);
        requireUniqueNameAmongSiblings(null, createDto.name(), createDto.parentId(), existing);

        OrgUnit unit = new OrgUnit();
        unit.setName(createDto.name());
        unit.setShortName(createDto.shortName());
        unit.setType(createDto.type());
        unit.setParent(resolveParent(createDto.parentId()));
        unit.setActive(true);

        return orgUnitMapper.toDto(orgUnitRepository.save(unit));
    }

    /**
     * Правка подразделения, включая перенос в другого родителя ({@code parentId = null} поднимает
     * на верхний уровень). Перенос проверяется тем же правилом, что и создание.
     */
    @Transactional
    public OrgUnitDto update(Integer id, OrgUnitUpdateDto updateDto) {
        OrgUnit unit = getEntityById(id);
        List<OrgUnit> existing = orgUnitRepository.findAllByOrderByNameAsc();
        requireValidHierarchy(id, updateDto.type(), updateDto.parentId(), existing);
        requireUniqueNameAmongSiblings(id, updateDto.name(), updateDto.parentId(), existing);

        unit.setName(updateDto.name());
        unit.setShortName(updateDto.shortName());
        unit.setType(updateDto.type());
        unit.setParent(resolveParent(updateDto.parentId()));
        unit.setActive(Boolean.TRUE.equals(updateDto.active()));

        return orgUnitMapper.toDto(orgUnitRepository.save(unit));
    }

    /**
     * Цена удаления подразделения: всё, что на него ссылается, удалению мешает
     * ({@code ON DELETE RESTRICT} на всех трёх ссылках — каскада нет намеренно, см.
     * {@link OrgUnitDeletionImpactDto}). Состояние не меняет.
     */
    @Transactional(readOnly = true)
    public OrgUnitDeletionImpactDto deleteImpact(Integer id) {
        OrgUnit unit = getEntityById(id);

        long childUnits = orgUnitRepository.countByParentId(id);
        long educators = educatorRepository.countByOrgUnitId(id);
        long groups = groupRepository.countByOrgUnitId(id);
        long auditoriums = auditoriumRepository.countByOrgUnitId(id);

        return new OrgUnitDeletionImpactDto(
                unit.getId(),
                unit.getName(),
                // deletable считается ЗДЕСЬ. Четвёртый счётчик появился вместе с миграцией 025:
                // забыть его значило бы вернуть сырой 500 из БД на кафедре с комнатами.
                childUnits == 0 && educators == 0 && groups == 0 && auditoriums == 0,
                childUnits,
                educators,
                groups,
                auditoriums);
    }

    /**
     * Удаляет подразделение. Отказывает, если на него что-то ссылается: БД всё равно не даст
     * (RESTRICT), а сырой 500 ничего не объясняет.
     */
    @Transactional
    public void delete(Integer id) {
        // Одно правило — один источник: и предпросмотр, и отказ смотрят на тот же deletable.
        OrgUnitDeletionImpactDto impact = deleteImpact(id); // бросит 404, если подразделения нет
        if (!impact.deletable()) {
            throw new InUseException(
                    "Подразделение «" + impact.name() + "» нельзя удалить: на него ссылаются "
                            + "вложенные подразделения (" + impact.childUnits() + "), "
                            + "преподаватели (" + impact.educators() + "), "
                            + "группы (" + impact.groups() + "), "
                            + "аудитории (" + impact.auditoriums() + "). "
                            + "Сначала перепривяжите их или снимите флаг «действующее».");
        }
        orgUnitRepository.deleteById(id);
    }

    /**
     * Находит сущность по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public OrgUnit getEntityById(Integer id) {
        return orgUnitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Подразделение с id=" + id + " не найдено."));
    }

    // === СЛУЖЕБНОЕ ===

    private OrgUnit resolveParent(Integer parentId) {
        return parentId == null ? null : getEntityById(parentId);
    }

    /**
     * Спрашивает правило (о новом родителе — вверх, о смене вида — вниз, на уже вложенные
     * подразделения) и переводит семантику отказа в сообщение пользователю.
     *
     * @param selfId {@code null} при создании нового подразделения
     */
    private void requireValidHierarchy(Integer selfId, OrgUnitType type, Integer parentId,
                                       List<OrgUnit> existing) {
        // Сборка входа — общая с OrgUnitSubtree: своя копия toNode развела бы два источника формы.
        Map<Integer, UnitNode> nodes = OrgUnitNodes.byId(existing);

        hierarchyRule.validateParent(selfId, type, parentId, nodes)
                .or(() -> hierarchyRule.validateTypeChange(selfId, type, nodes))
                .ifPresent(rejection -> {
                    OrgUnitType parentType = Optional.ofNullable(nodes.get(parentId))
                            .map(UnitNode::type)
                            .orElse(null);
                    throw new RuleViolationException(switch (rejection) {
                        case PARENT_NOT_FOUND ->
                                "Вышестоящее подразделение с id=" + parentId + " не найдено.";
                        case SELF_PARENT ->
                                "Подразделение не может быть вложено само в себя.";
                        case CYCLE ->
                                "Нельзя перенести подразделение внутрь его же подчинённого — "
                                        + "получилось бы кольцо.";
                        case TYPE_NOT_ALLOWED ->
                                "«" + (parentType == null ? "?" : parentType.getFullName())
                                        + "» не может содержать «" + type.getFullName() + "».";
                        case TYPE_BREAKS_CHILDREN ->
                                "Вид «" + type.getFullName() + "» не может содержать подразделения, "
                                        + "уже вложенные в это. Сначала перенесите их.";
                    });
                });
    }

    /**
     * Тёзки под одним родителем запрещены (то же самое стережёт UNIQUE-индекс в схеме — здесь
     * проверяем заранее, чтобы вместо сырой ошибки БД вернуть внятный текст).
     */
    private void requireUniqueNameAmongSiblings(Integer selfId, String name, Integer parentId,
                                                List<OrgUnit> existing) {
        boolean taken = existing.stream()
                .filter(u -> !u.getId().equals(selfId))
                .filter(u -> Objects.equals(OrgUnitNodes.parentIdOf(u), parentId))
                .anyMatch(u -> u.getName().trim().equalsIgnoreCase(name.trim()));

        if (taken) {
            throw new DuplicateException("Подразделение с названием «" + name.trim()
                    + "» уже есть на этом уровне.");
        }
    }
}
