package ru.dto.educator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.enums.AcademicDegree;
import ru.enums.AcademicTitle;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;

import java.util.Set;

public record EducatorUpdateDto(
        @NotBlank @Size(max = 255)
        String name,
        Set<DayOfWeek> preferredDays,
        Set<TimeSlotPair> preferredTimeSlots,
        boolean compactSchedule,

        // Подразделение; null — открепить от подразделения
        Integer orgUnitId,

        // Регалии; null у любого поля — снять значение (звание не пожизненно в рамках карточки:
        // ошибочно проставленное надо уметь убрать)
        Integer specialRankId,
        Integer rankServiceId,
        AcademicDegree academicDegree,
        Integer scienceBranchId,
        AcademicTitle academicTitle
) {
}
