package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Educator;

@Repository
public interface EducatorRepository extends JpaRepository<Educator, Integer> {
}
