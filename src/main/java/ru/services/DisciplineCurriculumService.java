package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.repositories.DisciplineCurriculumRepository;

@Service
@RequiredArgsConstructor
public class DisciplineCurriculumService {
    private final DisciplineCurriculumRepository disciplineCurriculumRepository;

    public DisciplineCurriculum getDisciplineCurriculumById(int id) {
        return disciplineCurriculumRepository.findById(id).get();
    }
}
