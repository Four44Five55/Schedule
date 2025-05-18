package ru.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.Lesson;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.SlotChain;
import ru.repositories.SlotChainRepository;

import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class SlotChainService {
    private final SlotChainRepository repository;
    private final CurriculumSlotService slotService;

    public void createChain(Integer slotAId, Integer slotBId) {
        if (slotAId.equals(slotBId)) {
            throw new IllegalArgumentException("Cannot chain slot to itself");
        }
        if (repository.existsBySlotAIdAndSlotBId(slotAId, slotBId)) {
            throw new IllegalStateException("Chain already exists");
        }

        CurriculumSlot slotA = slotService.getById(slotAId);
        CurriculumSlot slotB = slotService.getById(slotBId);

        repository.save(new SlotChain(slotA, slotB));
    }

    public SlotChain getByID(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public SlotChain update(Integer id, SlotChain slotChain) {
        SlotChain old = repository.findById(id).orElse(null);
        if (old != null) {
            old.setSlotA(slotChain.getSlotA());
            old.setSlotB(slotChain.getSlotB());
        }
        return repository.save(old != null ? old : null);
    }

    public SlotChain save(SlotChain slotChain) {
        return repository.save(slotChain);

    }

    public void removeChain(Integer slotAId, Integer slotBId) {
        repository.deleteBySlotAIdAndSlotBId(slotAId, slotBId);
    }

    public boolean isChained(Integer slotAId, Integer slotBId) {
        return repository.existsBySlotAIdAndSlotBId(slotAId, slotBId) ||
                repository.existsBySlotAIdAndSlotBId(slotBId, slotAId);
    }

    /**
     * Возвращает всю последовательность slotId связанных с запрашиваемым curriculumSlot
     *
     * @param slotId id проверяемого curriculumSlot
     * @return List<Integer> с отсортированным списком id
     */
    public List<Integer> getAllLinkedSlotIds(Integer slotId) {
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        Set<Integer> result = new TreeSet<>();

        queue.add(slotId);
        visited.add(slotId);

        while (!queue.isEmpty()) {
            Integer currentSlotId = queue.poll();

            List<Integer> directLinks = repository.findLinkedSlotIds(currentSlotId);

            for (Integer linkedSlotId : directLinks) {
                if (!visited.contains(linkedSlotId)) {
                    visited.add(linkedSlotId);
                    result.add(linkedSlotId);
                    queue.add(linkedSlotId);
                }
            }
        }

        if (!result.isEmpty()) {
            result.add(slotId); // Добавляем исходный slotId, только если есть связи
        }

        return new ArrayList<>(result);
    }

    /**
     * Находит занятия, связанные через цепочку слотов и совпадающие по группам.
     *
     * @param lesson  Исходное занятие для сравнения групп.
     * @param lessons Список всех занятий для фильтрации.
     * @return Список связанных занятий с теми же группами.
     */
    public List<Lesson> findLessonsInSlotChain(Lesson lesson, List<Lesson> lessons) {
        if (lesson == null || lesson.getCurriculumSlotId() == null || lesson.getGroupCombinations() == null) {
            return Collections.emptyList(); // Защита от null
        }

        // Получаем цепочку связанных слотов
        List<Integer> slotChainList = getAllLinkedSlotIds(lesson.getCurriculumSlotId());

        return lessons.stream()
                .filter(lesson1 ->
                        slotChainList.contains(lesson1.getCurriculumSlotId()) &&
                                lesson1.getGroupCombinations().equals(lesson.getGroupCombinations())
                )
                .toList();
    }
}
