package ru.dto.moveLesson;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;

public record MoveOptionDto(LocalDate date,
                            TimeSlotPair timeSlot) {
}
