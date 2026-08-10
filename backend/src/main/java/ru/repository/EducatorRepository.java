package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.Educator;

import java.util.Collection;
import java.util.List;

@Repository
public interface EducatorRepository extends JpaRepository<Educator, Integer> {

    /**
     * Сколько преподавателей числится в подразделении. Для цены удаления: FK
     * {@code educator.org_unit_id → org_unit ON DELETE RESTRICT} — подразделение с людьми
     * удалить нельзя, и сказать об этом надо до попытки, а не сырым 500 после.
     */
    long countByOrgUnitId(Integer orgUnitId);

    /**
     * Сколько преподавателей ссылается на строку справочника регалий. Нужно для той же цели, что
     * и {@link #countByOrgUnitId}: FK стоят с {@code ON DELETE RESTRICT}, поэтому цену удаления
     * называем заранее — числом в списке и флагом {@code deletable}, а не сырым 500 после попытки.
     */
    long countBySpecialRankId(Integer specialRankId);

    long countByRankServiceId(Integer rankServiceId);

    long countByScienceBranchId(Integer scienceBranchId);

    /**
     * Id преподавателей любого из подразделений набора — вход для фильтра «расписание кафедры».
     *
     * <p>Набор подразделений разворачивает {@code OrgUnitScopeResolver} (единственный владелец
     * рекурсии по дереву); репозиторий про вложенность не знает и знать не должен — иначе
     * рекурсия появилась бы во втором месте.</p>
     *
     * <p>Отдаются id, а не сущности: результат идёт в {@code IN (...)} следующего запроса,
     * поднимать ради этого сущности целиком незачем.</p>
     */
    @Query("select e.id from Educator e where e.orgUnit.id in :orgUnitIds")
    List<Integer> findIdsByOrgUnitIdIn(@Param("orgUnitIds") Collection<Integer> orgUnitIds);

    /**
     * Преподаватели набора вместе с подразделением и регалиями — вход для таблицы «Обозначения»
     * в выгрузке расписания: колонка «Каф» и подпись «п-к юст Иванов И.И., к.т.н., доц».
     *
     * <p>{@code left join fetch} на каждой связи: все они nullable («не указано» — легитимное
     * состояние), и внутреннее соединение молча выбросило бы преподавателей без кафедры или без
     * звания. Fetch — чтобы не ловить N+1 на ленивых связях уже за пределами метода.</p>
     *
     * <p>Все четыре справочника — {@code ToOne}, поэтому несколько fetch-соединений в одном
     * запросе допустимы (декартова взрыва, как на коллекциях, здесь нет).</p>
     *
     * <p>Читается из master-данных, а не из {@code schedule_view}: ни подразделение, ни регалии в
     * проекцию сознательно не денормализованы — иначе переименование кафедры и присвоение звания
     * обязаны были бы порождать перепроекцию. См. CQRS_ARCHITECTURE, «Что в проекцию НЕ кладут».</p>
     */
    @Query("""
            select e from Educator e
            left join fetch e.orgUnit
            left join fetch e.specialRank
            left join fetch e.rankService
            left join fetch e.scienceBranch
            where e.id in :ids
            """)
    List<Educator> findAllWithDetailsByIdIn(@Param("ids") Collection<Integer> ids);
}
