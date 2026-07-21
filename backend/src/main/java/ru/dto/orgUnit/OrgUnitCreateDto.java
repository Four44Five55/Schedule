package ru.dto.orgUnit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.enums.OrgUnitType;

/**
 * Создание подразделения. {@code parentId} необязателен: верхний уровень — легитимное место и
 * для кафедры, не входящей в факультет.
 */
public record OrgUnitCreateDto(
        @NotBlank @Size(max = 255)
        String name,

        @Size(max = 50)
        String shortName,

        @NotNull
        OrgUnitType type,

        Integer parentId
) {}
