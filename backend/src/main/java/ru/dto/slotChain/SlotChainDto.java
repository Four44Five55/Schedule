package ru.dto.slotChain;

/**
 * DTO для представления информации о "сцепке" слотов.
 *
 * @param id      Уникальный идентификатор сцепки.
 * @param slotA   Краткая информация о первом слоте.
 * @param slotB   Краткая информация о втором слоте.
 */
public record SlotChainDto(
        Integer id,
        CurriculumSlotBriefDto slotA,
        CurriculumSlotBriefDto slotB
) {
    /**
     * Краткое DTO для слота, используемое внутри SlotChainDto.
     */
    public record CurriculumSlotBriefDto(Integer id, Integer position, String kindOfStudyName) {}
}
