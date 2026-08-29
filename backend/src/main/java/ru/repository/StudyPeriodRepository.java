package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.StudyPeriod;
import ru.enums.PeriodType;

import java.time.LocalDate;
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

    /**
     * Находит активный учебный период, который содержит сегодняшнюю дату.
     * Активный период — это период, где {@code startDate <= сегодня <= endDate}.
     *
     * @return Optional с активным периодом или пустой, если активного периода нет
     */
    @Query("SELECT sp FROM StudyPeriod sp WHERE sp.startDate <= :currentDate AND sp.endDate >= :currentDate")
    Optional<StudyPeriod> findActivePeriod(@Param("currentDate") LocalDate currentDate);
}
