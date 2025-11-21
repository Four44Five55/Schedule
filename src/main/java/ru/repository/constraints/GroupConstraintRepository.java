package ru.repository.constraints;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.constraints.GroupConstraint;

@Repository
public interface GroupConstraintRepository extends JpaRepository<GroupConstraint, Integer> {
}
