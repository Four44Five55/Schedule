package ru.entity;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.Objects;

public class CellForLesson {
    private LocalDate date;
    private TimeSlotPair timeSlotPair;

    public CellForLesson() {
    }

    public CellForLesson(LocalDate date, TimeSlotPair timeSlotPair) {
        this.date = date;
        this.timeSlotPair = timeSlotPair;
    }

    public CellForLesson(TimeSlotPair timeSlotPair) {
        this.timeSlotPair = timeSlotPair;
    }

    public LocalDate getDate() {
        return date;
    }

    public TimeSlotPair getTimeSlotPair() {
        return timeSlotPair;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CellForLesson that = (CellForLesson) o;
        return Objects.equals(date, that.date) && timeSlotPair == that.timeSlotPair;
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, timeSlotPair);
    }
}
