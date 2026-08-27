package ru.abstracts;

import lombok.Getter;
import ru.inter.IGrid;

import java.time.LocalDate;

@Getter
abstract public class AbstractGrid implements IGrid {
    private final LocalDate startDate;
    private final LocalDate endDate;

    public AbstractGrid(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

}
