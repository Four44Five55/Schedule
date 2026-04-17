package ru.dto.slotChain;

import jakarta.validation.constraints.NotNull;

public record SlotChainCreateDto(
        @NotNull Integer slotAId,
        @NotNull Integer slotBId
) {}