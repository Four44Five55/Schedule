package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.DisciplineCourse;

import java.util.List;

@Repository
public interface DisciplineCourseRepository extends JpaRepository<DisciplineCourse, Integer> {
    /**
     * Проверяет, существует ли курс для указанной дисциплины и учебного периода.
     * Spring Data JPA сгенерирует SQL: SELECT COUNT(*) > 0 FROM discipline_course WHERE discipline_id = ? AND study_period_id = ?
     */
    boolean existsByDisciplineIdAndStudyPeriodId(Integer disciplineId, Integer studyPeriodId);

    /**
     * Находит все курсы для указанной дисциплины и сортирует их по дате начала учебного периода.
     * Spring Data JPA сгенерирует SQL: SELECT * FROM discipline_course WHERE discipline_id = ? ORDER BY study_period.start_date ASC
     */
    List<DisciplineCourse> findByDisciplineIdOrderByStudyPeriod_StartDate(Integer disciplineId);
}
