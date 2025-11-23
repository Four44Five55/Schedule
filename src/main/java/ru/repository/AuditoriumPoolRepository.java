package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.AuditoriumPool;

@Repository
public interface AuditoriumPoolRepository extends JpaRepository<AuditoriumPool, Long> {
}
