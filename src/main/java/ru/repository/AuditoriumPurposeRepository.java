package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.AuditoriumPurpose;

@Repository
public interface AuditoriumPurposeRepository extends JpaRepository<AuditoriumPurpose, Integer> {
    boolean existsByName(String name);
}
