package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.Discipline;

import java.util.Optional;
@Repository
public interface DisciplineRepository extends JpaRepository<Discipline, Integer> {
    // Проверка существования по названию
    boolean existsByName(String name);

    // Проверка существования по аббревиатуре
    boolean existsByAbbreviation(String abbreviation);

    // Поиск по названию
    Optional<Discipline> findByName(String name);

    // Поиск по аббревиатуре
    Optional<Discipline> findByAbbreviation(String abbreviation);

/*    // Комбинированная проверка (либо имя, либо аббревиатура)
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
            "FROM Discipline d WHERE d.name = :name OR d.abbreviation = :abbreviation")
    boolean existsByNameOrAbbreviation(@Param("name") String name,
                                       @Param("abbreviation") String abbreviation);*/
}