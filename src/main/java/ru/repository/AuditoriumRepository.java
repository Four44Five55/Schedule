package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Auditorium;

import java.util.Optional;

@Repository
public interface AuditoriumRepository extends JpaRepository<Auditorium, Integer> {

    @EntityGraph(attributePaths = {"building", "building.location"})
    Optional<Auditorium> findWithDetailsById(Integer id);

    boolean existsByNameAndBuildingId(String name, Integer buildingId);
}
