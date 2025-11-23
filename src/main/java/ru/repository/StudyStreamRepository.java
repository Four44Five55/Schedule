package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.StudyStream;

@Repository
public interface StudyStreamRepository extends JpaRepository<StudyStream, Integer> {
}
