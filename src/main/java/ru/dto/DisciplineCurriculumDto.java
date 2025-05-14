package ru.dto;

import ru.entity.logicSchema.DisciplineCurriculum;

import java.util.List;
import java.util.Map;

public record DisciplineCurriculumDto(
        int id,
        DisciplineDto discipline,
        List<CurriculumSlotDto> slots,
        Map<Integer, Integer> slotChains // Для хранения связей между слотами
) {
    public static DisciplineCurriculumDto fromEntity(DisciplineCurriculum curriculum) {
        return new DisciplineCurriculumDto(
                curriculum.getId(),
                DisciplineDto.fromEntity(curriculum.getDiscipline()),
                curriculum.getCurriculumSlots().stream()
                        .map(CurriculumSlotDto::fromEntity)
                        .toList(),
                curriculum.getChainManager().getAllChains()
        );
    }
}
