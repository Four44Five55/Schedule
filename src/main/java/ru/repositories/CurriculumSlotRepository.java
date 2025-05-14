package ru.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.Discipline;
import ru.entity.logicSchema.CurriculumSlot;

import java.util.List;

@Repository
public interface CurriculumSlotRepository extends JpaRepository<CurriculumSlot, Integer> {

}
