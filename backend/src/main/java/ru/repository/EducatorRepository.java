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
}
