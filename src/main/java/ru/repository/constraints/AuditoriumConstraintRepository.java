package ru.repository.constraints;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.constraints.AuditoriumConstraint;

@Repository
public interface AuditoriumConstraintRepository extends JpaRepository<AuditoriumConstraint, Integer> {
}
