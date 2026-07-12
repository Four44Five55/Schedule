package ru.dto.board;

/**
 * Лёгкий счётчик размещённости курса в сессии — для индикатора «распределено N/M»
 * во вкладке генерации. Единица счёта — {@link ru.entity.Assignment} (в сессии ≤1
 * размещение на назначение), согласуется с заголовочными счётчиками доски раскладки.
 *
 * @param courseId курс (DisciplineCourse)
 * @param total    всего назначений курса (к раскладке)
 * @param placed   сколько из них уже размещено в этой сессии
 */
public record CoursePlacementCountDto(
        Integer courseId,
        int total,
        int placed
) {
}
