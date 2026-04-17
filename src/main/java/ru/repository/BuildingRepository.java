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
}
