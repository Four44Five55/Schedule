package ru.dto.board;

import java.util.List;

/**
 * Одно занятие (в разрезе одной сущности) на «доске раскладки».
 *
 * <p>Единица счёта — {@link ru.entity.Assignment}: в сессии на него приходится не более одного
 * {@link ru.entity.write.LessonPlacement}. Поля размещения ({@link #placementId}, {@link #date},
 * {@link #slot}, {@link #locked}, {@link #source}) заполнены, если занятие размещено, и {@code null},
 * если ещё в очереди — так фронт одним списком делит «Очередь» и «Размещено» без второго запроса.</p>
 *
 * @param assignmentId     назначение (что ставим/поставлено)
 * @param courseId         курс (дисциплина в периоде) — для «следующего в очереди» и группировки
 * @param curriculumSlotId слот учебного плана
 * @param kindOfStudy      вид занятия (enum name: LECTURE, …)
 * @param kindOfStudyAbbr  вид (аббревиатура: Л, ПЗ, …)
 * @param position         позиция занятия в плане
 * @param themeNumber      номер темы (или null)
 * @param themeTitle       название темы (или null)
 * @param studyStreamId    поток
 * @param streamName       имя потока
 * @param groupIds         id групп потока
 * @param groupNames       имена групп потока
 * @param educatorIds      id преподавателей
 * @param educatorNames    имена преподавателей
 * @param placementId      UUID размещения ({@code null} → не размещено)
 * @param date             дата размещения YYYY-MM-DD ({@code null} → не размещено)
 * @param slot             пара размещения FIRST..FOURTH ({@code null} → не размещено)
 * @param locked           закреплено ли размещение (пин); {@code null} → не размещено
 * @param source           происхождение: GENERATED | MANUAL ({@code null} → не размещено)
 */
public record BoardLessonDto(
        Integer assignmentId,
        Integer courseId,
        Integer curriculumSlotId,
        String kindOfStudy,
        String kindOfStudyAbbr,
        Integer position,
        String themeNumber,
        String themeTitle,
        Integer studyStreamId,
        String streamName,
        List<Integer> groupIds,
        List<String> groupNames,
        List<Integer> educatorIds,
        List<String> educatorNames,
        String placementId,
        String date,
        String slot,
        Boolean locked,
        String source
) {
    /** Размещено ли занятие (есть placement). */
    public boolean isPlaced() {
        return placementId != null;
    }
}
