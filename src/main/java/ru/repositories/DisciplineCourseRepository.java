package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.DisciplineCourse;

import java.util.List;

@Repository
public interface DisciplineCourseRepository extends JpaRepository<DisciplineCourse, Integer> {
    // Проверяет, существует ли курс для пары дисциплина-семестр
    boolean existsByDisciplineIdAndSemester(Integer disciplineId, int semester);

    // Находит все курсы для дисциплины и сортирует их
    List<DisciplineCourse> findByDisciplineIdOrderBySemester(Integer disciplineId);
}
