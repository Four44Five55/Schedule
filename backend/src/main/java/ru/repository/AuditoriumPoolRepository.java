package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.AuditoriumPool;
import java.util.Optional;

@Repository
public interface AuditoriumPoolRepository extends JpaRepository<AuditoriumPool, Integer> {

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"auditoriums"})
    Optional<AuditoriumPool> findWithAuditoriumsById(Integer id);
}
