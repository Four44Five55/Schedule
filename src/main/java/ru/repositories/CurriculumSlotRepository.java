package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.CurriculumSlot;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumSlotRepository extends JpaRepository<CurriculumSlot, Integer> {
    Optional<CurriculumSlot> findFirstByIdLessThanAndDisciplineIdOrderByIdDesc(Integer id, Integer disciplineId);

    @Query("SELECT cs FROM CurriculumSlot cs " +
            "WHERE cs.discipline.id = :disciplineId " +
            "AND cs.kindOfStudy = 'LECTURE' " +
            "AND cs.id <:currentSlotId " +
            "ORDER BY cs.id DESC " +
            "LIMIT 1")
    Optional<CurriculumSlot> findPreviousLecture(
            @Param("currentSlotId") Integer currentSlotId,
            @Param("disciplineId") Integer disciplineId);

    @Query("SELECT cs FROM CurriculumSlot cs WHERE cs.discipline.id = :disciplineId ORDER BY cs.id")
    List<CurriculumSlot> findByDisciplineId(@Param("disciplineId") Integer disciplineId);
}
