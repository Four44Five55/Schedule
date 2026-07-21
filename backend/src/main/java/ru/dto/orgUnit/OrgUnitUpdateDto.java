package ru.dto.orgUnit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.enums.OrgUnitType;

/**
 * Правка подразделения, включая перенос в другого родителя ({@code parentId = null} — поднять на
 * верхний уровень). Допустимость нового родителя проверяет {@code OrgUnitHierarchyRule}.
 */
public record OrgUnitUpdateDto(
        @NotBlank @Size(max = 255)
        String name,

        @Size(max = 50)
        String shortName,

        @NotNull
        OrgUnitType type,

        Integer parentId,

        @NotNull
        Boolean active
) {}
