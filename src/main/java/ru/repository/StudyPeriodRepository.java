package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.StudyPeriod;
import ru.enums.PeriodType;

import java.util.Optional;

@Repository
public interface StudyPeriodRepository extends JpaRepository<StudyPeriod, Integer> {

    /**
     * Проверяет существование периода по учебному году и типу.
     * Используется для предотвращения создания дубликатов.
     */
    boolean existsByStudyYearAndPeriodType(int studyYear, PeriodType periodType);

    /**
     * Находит период по учебному году и типу.
     */
    Optional<StudyPeriod> findByStudyYearAndPeriodType(int studyYear, PeriodType periodType);
}
