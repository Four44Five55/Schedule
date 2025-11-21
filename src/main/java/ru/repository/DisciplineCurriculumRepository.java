package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.DisciplineCurriculum;

@Repository
public interface DisciplineCurriculumRepository extends JpaRepository<DisciplineCurriculum, Integer> {
    @EntityGraph(attributePaths = {"discipline", "startSlot", "endSlot"})
    DisciplineCurriculum findFullById(Integer id);

    DisciplineCurriculum findByDisciplineId(Integer disciplineId);
}
