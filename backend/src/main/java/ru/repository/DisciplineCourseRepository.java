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
     * Сколько курсов у дисциплины. Для запрета удаления: каскад БД уносит вместе с курсами их планы,
     * назначения и уже размещённые занятия, поэтому дисциплину с курсами удалять нельзя — и сказать
     * об этом надо до попытки, а не сырым отказом после.
     */
    long countByDisciplineId(Integer disciplineId);

    /**
     * Проверяет дубль курса с учётом семестра: одна дисциплина в одном периоде может
     * существовать на разных семестрах, поэтому уникальность — (discipline, period, semester).
     */
    boolean existsByDisciplineIdAndStudyPeriodIdAndSemester(Integer disciplineId, Integer studyPeriodId, int semester);

    /**
     * Находит все курсы для указанной дисциплины и сортирует их по дате начала учебного периода.
     * Spring Data JPA сгенерирует SQL: SELECT * FROM discipline_course WHERE discipline_id = ? ORDER BY study_period.start_date ASC
     */
    List<DisciplineCourse> findByDisciplineIdOrderByStudyPeriod_StartDate(Integer disciplineId);

    /**
     * Находит все курсы указанного учебного периода (для генерации/планирования «по периоду»).
     * Сортировка по дисциплине и семестру — стабильный порядок для UI.
     */
    List<DisciplineCourse> findByStudyPeriodIdOrderByDiscipline_NameAscSemesterAsc(Integer studyPeriodId);

    /** Все курсы периода — вход отката: снос курса каскадом уносит слоты, назначения и размещения. */
    List<DisciplineCourse> findByStudyPeriodId(Integer studyPeriodId);

    /** Сколько курсов у периода — часть цены отката, названной заранее. */
    long countByStudyPeriodId(Integer studyPeriodId);
}
