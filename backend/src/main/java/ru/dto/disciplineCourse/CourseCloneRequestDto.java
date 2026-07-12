package ru.dto.disciplineCourse;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Запрос на копирование учебного плана: глубокая копия выбранных курсов-источников
 * в целевой учебный период (наполнение нового периода без ручного ввода).
 *
 * <p>Копируются курс + слоты (скаляры и ссылки на тему/аудитории/пул) + внутрикурсовые
 * сцепки с ремапом старых слотов на новые. Темы глобальны и не копируются; назначения
 * (потоки/преподаватели) меняются по периодам и в копию не входят.</p>
 *
 * @param sourceCourseIds курсы-источники (из любого периода), которые нужно скопировать
 * @param targetPeriodId  целевой учебный период, куда создаются копии
 */
public record CourseCloneRequestDto(
        @NotEmpty(message = "Выберите хотя бы один курс для копирования")
        List<Integer> sourceCourseIds,

        @NotNull(message = "Не указан целевой учебный период")
        Integer targetPeriodId
) {
}
