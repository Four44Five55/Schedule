package ru.repository.constraints;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.constraints.EducatorConstraint;

@Repository
public interface EducatorConstraintRepository extends JpaRepository<EducatorConstraint, Integer> {
}
