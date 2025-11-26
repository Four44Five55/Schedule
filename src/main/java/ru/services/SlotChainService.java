package ru.services;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dto.slotChain.SlotChainCreateDto;
import ru.dto.slotChain.SlotChainDto;
import ru.entity.Lesson;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.SlotChain;
import ru.mapper.SlotChainMapper;
import ru.repository.SlotChainRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotChainService {

    private final SlotChainRepository slotChainRepository;
    private final CurriculumSlotService curriculumSlotService;
    private final SlotChainMapper slotChainMapper;

    @Transactional
    public SlotChainDto createChain(SlotChainCreateDto createDto) {
        Integer slotAId = createDto.slotAId();
        Integer slotBId = createDto.slotBId();

        if (slotAId.equals(slotBId)) {
            throw new IllegalArgumentException("Нельзя связать слот сам с собой.");
        }
        // Проверяем на дублирование в обе стороны
        if (slotChainRepository.existsBySlotAIdAndSlotBId(slotAId, slotBId) ||
                slotChainRepository.existsBySlotAIdAndSlotBId(slotBId, slotAId)) {
            throw new IllegalStateException("Такая сцепка или ее обратная версия уже существует.");
        }

        // УБИРАЕМ ПРОВЕРКУ, запрещающую длинные цепочки.

        CurriculumSlot slotA = curriculumSlotService.findEntityById(slotAId);
        CurriculumSlot slotB = curriculumSlotService.findEntityById(slotBId);

        if (!slotA.getDisciplineCourse().getId().equals(slotB.getDisciplineCourse().getId())) {
            throw new IllegalArgumentException("Нельзя сцепить слоты из разных учебных курсов.");
        }

        SlotChain newChain = new SlotChain(slotA, slotB);
        return slotChainMapper.toDto(slotChainRepository.save(newChain));
    }

    @Transactional
    public void deleteChain(Integer chainId) {
        if (!slotChainRepository.existsById(chainId)) {
            throw new EntityNotFoundException("Сцепка с id=" + chainId + " не найдена.");
        }
        slotChainRepository.deleteById(chainId);
    }

    @Transactional(readOnly = true)
    public List<SlotChainDto> findAll() {
        return slotChainRepository.findAll().stream()
                .map(slotChainMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Находит всю цепочку связанных слотов, начиная с указанного.
     * Использует обход графа в ширину (BFS) для поиска всех соединенных компонент.
     *
     * @param startSlotId ID слота, с которого начинается поиск.
     * @return Отсортированный список ID всех слотов в цепочке.
     */
    @Transactional(readOnly = true)
    public List<Integer> getFullChain(Integer startSlotId) {
        if (!curriculumSlotService.existsById(startSlotId)) {
            throw new EntityNotFoundException("Слот с id=" + startSlotId + " не найден.");
        }

        // Используем TreeSet для автоматической сортировки и уникальности
        Set<Integer> fullChain = new TreeSet<>();
        Queue<Integer> toVisit = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();

        // Начинаем обход
        toVisit.add(startSlotId);
        visited.add(startSlotId);

        while (!toVisit.isEmpty()) {
            Integer currentSlotId = toVisit.poll();
            fullChain.add(currentSlotId);

            // Находим всех прямых "соседей"
            List<Integer> neighbors = slotChainRepository.findDirectlyLinkedSlotIds(currentSlotId);

            for (Integer neighborId : neighbors) {
                if (!visited.contains(neighborId)) {
                    visited.add(neighborId);
                    toVisit.add(neighborId);
                }
            }
        }

        return new ArrayList<>(fullChain);
    }
}
