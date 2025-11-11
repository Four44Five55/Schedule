package ru.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.repositories.CurriculumSlotRepository;
import ru.repositories.DisciplineCurriculumRepository;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DisciplineCurriculumService {
    private final DisciplineCurriculumRepository repository;

    public DisciplineCurriculum create(DisciplineCurriculum disciplineCurriculum) {
        return repository.save(disciplineCurriculum);
    }

    public DisciplineCurriculum getById(Integer id) {
        DisciplineCurriculum curriculum = repository.findFullById(id);
        if (curriculum == null) {
            throw new RuntimeException("DisciplineCurriculum не найден id: " + id);
        }
        return curriculum;
    }

    public DisciplineCurriculum findByDisciplineId(Integer disciplineId){
        return repository.findByDisciplineId(disciplineId);
    }
}