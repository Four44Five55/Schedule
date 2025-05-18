package ru.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.entity.Lesson;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.repositories.CurriculumSlotRepository;
import ru.repositories.DisciplineRepository;
import ru.repositories.ThemeLessonRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class CurriculumSlotService {
    private final CurriculumSlotRepository repository;
    private final DisciplineRepository disciplineRepository;
    private final ThemeLessonRepository themeLessonRepository;

    private static final Logger logger = LoggerFactory.getLogger(CurriculumSlotService.class);

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
    public List<CurriculumSlot> getAllSlotsForDiscipline(DisciplineCurriculum curriculum) {
        int startId = curriculum.getStartSlot().getId();
        int endId = curriculum.getEndSlot().getId();

        List<CurriculumSlot> slots = new ArrayList<>();

        for (int id = startId; id <= endId; id++) {
            CurriculumSlot slot = repository.findById(id).orElse(null);

            if (slot != null) {
                // Явная инициализация связей
                if (slot.getDiscipline() != null) {
                    Hibernate.initialize(slot.getDiscipline());
                }
                if (slot.getThemeLesson() != null) {
                    Hibernate.initialize(slot.getThemeLesson());
                }
                slots.add(slot);
            }
        }

        return slots;
    }

    public Integer getPreviousSlotIdForDiscipline(Lesson lesson) {
        Integer currentSlotId = lesson.getCurriculumSlotId();
        Integer disciplineId = lesson.getDiscipline().getId();

        // Проверяем, что текущий слот существует и принадлежит указанной дисциплине
        CurriculumSlot currentSlot = repository.findById(currentSlotId)
                .orElseThrow(() -> new RuntimeException("CurriculumSlot не найден id: " + currentSlotId));

        if (!currentSlot.getDiscipline().getId().equals(disciplineId)) {
            return null; // или можно бросить исключение, если это ошибка
        }

        // Ищем предыдущий слот для этой дисциплины
        Optional<CurriculumSlot> previousSlot = repository.findFirstByIdLessThanAndDisciplineIdOrderByIdDesc(
                currentSlotId, disciplineId);

        return previousSlot.map(CurriculumSlot::getId).orElse(null);
    }
}
