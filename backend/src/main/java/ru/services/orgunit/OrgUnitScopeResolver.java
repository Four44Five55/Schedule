package ru.services.orgunit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.orgUnit.OrgUnitScopeDto;
import ru.entity.OrgUnit;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.OrgUnitRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import ru.exceptions.NotFoundException;

/**
 * Разрешение охвата подразделения: «кафедра» → множества id преподавателей и групп, с учётом
 * вложенности.
 *
 * <p><b>Единственный владелец рекурсии по дереву подразделений.</b> Это и есть причина, по
 * которой класс появился раньше своих потребителей: как только фильтр «расписание кафедры»
 * понадобится второму месту, обход дерева будет скопирован — и разойдётся, как разошлись два
 * описателя занятия и три реализации «равномерного распределения». Сам обход — чистая функция
 * {@link OrgUnitSubtree}; здесь к нему добавлено то, что требует БД и прикладного суждения.</p>
 *
 * <p><b>Отдаёт id, а не сущности.</b> Охват — это <i>вход для выборок</i>: множество id ложится
 * в {@code IN (...)} по уже существующим индексам ({@code idx_view_educator},
 * {@code idx_view_group}). Именно поэтому подразделение не денормализовано в
 * {@code schedule_view}: иначе переименование кафедры обязано было бы порождать перепроекцию
 * (см. {@code CQRS_ARCHITECTURE.md}, «Инварианты read-модели»).</p>
 *
 * <p><b>Подразделение — фильтр, а не ось.</b> Разрезы выгрузки остаются
 * {@code GROUP|EDUCATOR|AUDITORIUM}: у подразделения нет занятости, оно не планируемый ресурс.
 * Его роль — сузить набор сущностей, по которым строится существующий разрез.</p>
 */
@Service
@RequiredArgsConstructor
public class OrgUnitScopeResolver {

    private final OrgUnitRepository orgUnitRepository;
    // Репозитории, а не сервисы: нужны только множества id (по образцу OrgUnitService).
    private final EducatorRepository educatorRepository;
    private final GroupRepository groupRepository;

    private final OrgUnitSubtree subtree = new OrgUnitSubtree();

    /**
     * Идентификаторы подразделения и всех вложенных в него, на любую глубину.
     *
     * @throws NotFoundException если подразделения с таким id нет
     */
    @Transactional(readOnly = true)
    public Set<Integer> unitIds(Integer orgUnitId) {
        return resolveUnits(orgUnitId);
    }

    /**
     * Все преподаватели подразделения и вложенных в него.
     *
     * <p>Расформированные подразделения из охвата не выпадают — см. {@link OrgUnitSubtree}.
     * Преподаватели без подразделения ({@code org_unit_id IS NULL}) не попадают ни в один
     * охват: «не распределён» — это отсутствие принадлежности, а не принадлежность корню.</p>
     */
    @Transactional(readOnly = true)
    public Set<Integer> educatorIds(Integer orgUnitId) {
        // Через приватный helper, а не через публичный unitIds(): вызов самого себя минует прокси,
        // и @Transactional внутреннего метода молча не применяется (та же ловушка, что чинили в
        // setLock). Здесь это было бы безвредно — но приём опасен сам по себе.
        return educatorIdsIn(resolveUnits(orgUnitId));
    }

    /**
     * Все группы подразделения и вложенных в него. Условия те же, что у преподавателей.
     */
    @Transactional(readOnly = true)
    public Set<Integer> groupIds(Integer orgUnitId) {
        return groupIdsIn(resolveUnits(orgUnitId));
    }

    /**
     * Весь охват разом — для потребителей, которым нужны обе стороны (счётчики по ветке,
     * фильтр сразу по преподавателям и группам). Дерево при этом грузится и разворачивается
     * <b>один</b> раз, а не по разу на вопрос.
     */
    @Transactional(readOnly = true)
    public OrgUnitScopeDto scopeOf(Integer orgUnitId) {
        List<OrgUnit> allUnits = orgUnitRepository.findAllByOrderByNameAsc();
        Set<Integer> units = requireSubtree(orgUnitId, allUnits);

        String name = allUnits.stream()
                .filter(unit -> unit.getId().equals(orgUnitId))
                .findFirst()
                .map(OrgUnit::getName)
                // Недостижимо: пустое поддерево уже отвергнуто выше, а корень входит в своё поддерево.
                .orElseThrow(() -> new IllegalStateException(
                        "Подразделение id=" + orgUnitId + " есть в поддереве, но не в списке."));

        return new OrgUnitScopeDto(
                orgUnitId, name, units, educatorIdsIn(units), groupIdsIn(units));
    }

    // === СЛУЖЕБНОЕ ===

    private Set<Integer> resolveUnits(Integer orgUnitId) {
        return requireSubtree(orgUnitId, orgUnitRepository.findAllByOrderByNameAsc());
    }

    /**
     * Разворачивает поддерево и превращает «корня нет» из пустого множества в отказ.
     *
     * <p>Разделение намеренное: {@link OrgUnitSubtree} тотальна и на неизвестном корне молчит —
     * так её можно тестировать без БД. Но фильтр по несуществующему подразделению, вернувший
     * «ничего», выглядел бы как «в кафедре никого» — то есть спрятал бы ошибку вызывающего.</p>
     */
    private Set<Integer> requireSubtree(Integer orgUnitId, List<OrgUnit> allUnits) {
        Set<Integer> ids = subtree.idsOf(orgUnitId, OrgUnitNodes.byId(allUnits));
        if (ids.isEmpty()) {
            throw new NotFoundException("Подразделение с id=" + orgUnitId + " не найдено.");
        }
        return ids;
    }

    private Set<Integer> educatorIdsIn(Set<Integer> unitIds) {
        return new LinkedHashSet<>(educatorRepository.findIdsByOrgUnitIdIn(unitIds));
    }

    private Set<Integer> groupIdsIn(Set<Integer> unitIds) {
        return new LinkedHashSet<>(groupRepository.findIdsByOrgUnitIdIn(unitIds));
    }
}
