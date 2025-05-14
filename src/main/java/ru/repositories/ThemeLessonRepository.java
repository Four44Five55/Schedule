package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.ThemeLesson;

@Repository
public interface ThemeLessonRepository extends JpaRepository<ThemeLesson, Integer> {

}
