package ru.services.generation;

import ru.entity.Lesson;
import ru.entity.StudyPeriod;

import java.util.List;

/**
 * Область генерации расписания — результат разрешения запроса «сгенерировать на период».
 *
 * <p>Единый источник истины для солвера: {@link #period} задаёт календарные рамки
 * (start/end), а {@link #lessons} — что именно раскладывать. Это убирает прежние
 * неявные привязки (период «из первого курса» и захардкоженные даты).</p>
 *
 * @param period календарный учебный период (даёт даты планирования)
 * @param lessons занятия к размещению (по всем выбранным курсам периода)
 */
public record GenerationScope(
        StudyPeriod period,
        List<Lesson> lessons
) {
}
