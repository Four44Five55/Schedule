package ru.services.distribution.swap;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;

/**
 * Класс для хранения информации о возможной перестановке занятий
 */
public record SwapOption(
    CellForLesson originalCell,
    AbstractLesson originalLesson,
    CellForLesson targetCell,
    AbstractLesson targetLesson
) {} 