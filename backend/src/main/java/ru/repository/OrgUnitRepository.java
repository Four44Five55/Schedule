package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.OrgUnit;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrgUnitRepository extends JpaRepository<OrgUnit, Integer> {

    /**
     * Все подразделения плоским списком, с подгруженным родителем.
     *
     * <p>Дерево из этого списка собирает фронт: подразделений десятки, отдавать их одним
     * запросом дешевле, чем ходить по уровням, а форма дерева — презентация.
     * {@code EntityGraph} убирает N+1 при маппинге {@code parent.id/name} в DTO.</p>
     */
    @EntityGraph(attributePaths = {"parent"})
    List<OrgUnit> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"parent", "children"})
    Optional<OrgUnit> findWithDetailsById(Integer id);

    /**
     * Сколько подразделений непосредственно вложено. Для цены удаления: FK
     * {@code org_unit.parent_id → org_unit ON DELETE RESTRICT} — БД не даст удалить узел с
     * детьми, считаем заранее ради осмысленного 409 вместо сырого 500.
     */
    long countByParentId(Integer parentId);
}
