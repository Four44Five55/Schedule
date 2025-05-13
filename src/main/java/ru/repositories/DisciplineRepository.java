package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Discipline;
@Repository
public interface DisciplineRepository extends JpaRepository<Discipline, Integer> {
}
