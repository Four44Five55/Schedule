package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.ThemeLesson;

import java.util.List;

@Repository
public interface ThemeLessonRepository extends JpaRepository<ThemeLesson, Integer> {
    List<ThemeLesson> findByDisciplineIdOrderByThemeNumber(Integer disciplineId);
    boolean existsByDisciplineIdAndThemeNumber(Integer disciplineId, String themeNumber);
}
