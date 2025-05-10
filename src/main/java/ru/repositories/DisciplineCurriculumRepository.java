package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.DisciplineCurriculum;
@Repository
public interface DisciplineCurriculumRepository extends JpaRepository<DisciplineCurriculum, Integer> {
}
