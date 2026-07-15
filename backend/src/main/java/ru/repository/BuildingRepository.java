package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Building;

import java.util.Optional;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Integer> {
    @EntityGraph(attributePaths = {"location", "auditoriums"})
    Optional<Building> findWithDetailsById(Integer id);

    /**
     * Сколько корпусов привязано к локации. Для удаления локации: FK
     * {@code building.location_id → location ON DELETE RESTRICT}, поэтому БД не даст удалить
     * локацию с корпусами — считаем заранее, чтобы вернуть осмысленный 409, а не сырой 500.
     */
    long countByLocationId(Integer locationId);
}
