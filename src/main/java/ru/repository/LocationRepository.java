package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Location;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Integer> {
    boolean existsByName(String name);

    Optional<Location> findByName(String name);

    @EntityGraph(attributePaths = {"buildings"})
    Optional<Location> findWithBuildingsById(Integer id);
}
