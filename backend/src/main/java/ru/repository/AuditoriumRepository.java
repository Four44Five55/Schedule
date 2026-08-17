package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Auditorium;

import java.util.Optional;

@Repository
public interface AuditoriumRepository extends JpaRepository<Auditorium, Integer> {

    // orgUnit — в графе вместе с корпусом: DTO отдаёт его имя, и без графа карточка комнаты
    // догружала бы кафедру отдельным запросом (миграция 025).
    @EntityGraph(attributePaths = {"building", "building.location", "orgUnit"})
    Optional<Auditorium> findWithDetailsById(Integer id);

    boolean existsByNameAndBuildingId(String name, Integer buildingId);

    /**
     * Сколько аудиторий закреплено за подразделением. Для цены удаления: FK
     * {@code auditorium.org_unit_id → org_unit ON DELETE RESTRICT} (миграция 025) — кафедру с
     * комнатами удалить нельзя, и сказать об этом надо до попытки, а не сырым 500 после.
     */
    long countByOrgUnitId(Integer orgUnitId);
}
