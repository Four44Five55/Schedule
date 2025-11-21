package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.CurriculumSlot;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumSlotRepository extends JpaRepository<CurriculumSlot, Integer> {
    Optional<CurriculumSlot> findFirstByIdLessThanAndDisciplineIdOrderByIdDesc(Integer id, Integer disciplineCourseId);

    @Query("SELECT cs FROM CurriculumSlot cs " +
            "WHERE cs.disciplineCourse.id = :disciplineCourseId " +
            "AND cs.kindOfStudy = 'LECTURE' " +
            "AND cs.id <:currentSlotId " +
            "ORDER BY cs.id DESC " +
            "LIMIT 1")
    Optional<CurriculumSlot> findPreviousLecture(
            @Param("currentSlotId") Integer currentSlotId,
            @Param("disciplineCourseId") Integer disciplineCourseId);

    @Query("SELECT cs FROM CurriculumSlot cs WHERE cs.disciplineCourseId = :disciplineCourseId ORDER BY cs.position")
    List<CurriculumSlot> findByDisciplineCourseId(@Param("disciplineCourseId") Integer disciplineCourseId);
}
