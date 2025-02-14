package ru.entity;

import org.example.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.Objects;

public class CellForLesson {
    private LocalDate localDate;
    private TimeSlotPair timeSlotPair;

    public CellForLesson() {
    }

    public CellForLesson(LocalDate localDate, TimeSlotPair timeSlotPair) {
        this.localDate = localDate;
        this.timeSlotPair = timeSlotPair;
    }

    public CellForLesson(TimeSlotPair timeSlotPair) {
        this.timeSlotPair = timeSlotPair;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public TimeSlotPair getTimeSlotPair() {
        return timeSlotPair;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CellForLesson that = (CellForLesson) o;
        return timeSlotPair == that.timeSlotPair;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeSlotPair);
    }
}
