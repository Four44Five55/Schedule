package ru.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.logicSchema.CurriculumSlot;
import ru.repositories.CurriculumSlotRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class CurriculumSlotService {
    private final CurriculumSlotRepository repository;

    public CurriculumSlot create(CurriculumSlot curriculumSlot) {
        return repository.save(curriculumSlot);
    }

    public CurriculumSlot getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("CurriculumSlot не найден id: " + id));
    }

    public CurriculumSlot update(Integer id, CurriculumSlot updatedSlot) {
        CurriculumSlot curriculumSlot = getById(id);
        curriculumSlot.setKindOfStudy(updatedSlot.getKindOfStudy());
        curriculumSlot.setThemeLesson(updatedSlot.getThemeLesson());
        return repository.save(curriculumSlot);
    }

    public void delete(Integer id) {
        repository.deleteById(id);
    }


    @Transactional
    public List<CurriculumSlot> getAllSlotsForDiscipline(Integer disciplineCourseId) {
        return repository.findByDisciplineCourseId(disciplineCourseId);
    }

    public Optional<CurriculumSlot> getPreviousLecture(Integer currentSlotId, Integer disciplineId) {
        return repository.findPreviousLecture(currentSlotId, disciplineId);
    }

}
