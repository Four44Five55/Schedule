package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Educator;

@Repository
public interface EducatorRepository extends JpaRepository<Educator, Integer> {

    /**
     * Сколько преподавателей числится в подразделении. Для цены удаления: FK
     * {@code educator.org_unit_id → org_unit ON DELETE RESTRICT} — подразделение с людьми
     * удалить нельзя, и сказать об этом надо до попытки, а не сырым 500 после.
     */
    long countByOrgUnitId(Integer orgUnitId);
}
