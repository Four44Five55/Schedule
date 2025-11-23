package ru.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.Discipline;

import java.util.Optional;

@Repository
public interface DisciplineRepository extends JpaRepository<Discipline, Integer> {
    /**
     * Проверяет существование дисциплины по названию.
     */
    boolean existsByName(String name);

    /**
     * Находит дисциплину по названию.
     */
    Optional<Discipline> findByName(String name);

    /**
     * Находит дисциплину по ID и "жадно" подгружает связанные с ней курсы.
     * Это решает проблему "N+1 запросов" при маппинге в DisciplineDto.
     *
     * @param id ID дисциплины.
     * @return Optional с дисциплиной и ее курсами.
     */
    @EntityGraph(attributePaths = {"courses"})
    @Query("SELECT d FROM Discipline d WHERE d.id = :id")
    Optional<Discipline> findByIdWithCourses(@Param("id") Integer id);
}
