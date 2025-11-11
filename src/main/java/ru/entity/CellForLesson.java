package ru.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.Objects;

@Getter
@NoArgsConstructor
public class CellForLesson {
    private LocalDate date;
    private TimeSlotPair timeSlotPair;

    public CellForLesson(LocalDate date, TimeSlotPair timeSlotPair) {
        this.date = date;
        this.timeSlotPair = timeSlotPair;
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

    @Override
    public String toString() {
        return "Cell{" +
                "date=" + date +
                ", Pair=" + timeSlotPair +
                '}';
    }
}
