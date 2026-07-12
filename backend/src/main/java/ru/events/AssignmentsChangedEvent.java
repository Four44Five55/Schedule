package ru.events;

import lombok.Getter;

import java.util.List;

/**
 * Событие: у назначений изменился состав преподавателей и/или поток.
 *
 * <p>Командная сторона лишь объявляет факт — какие {@link ru.entity.Assignment} правились.
 * Что с этим делать, решает подписчик: {@link ru.services.ScheduleSynchronizer} находит уже
 * стоящие размещения этих назначений и перепроецирует {@link ru.entity.read.ScheduleView},
 * потому что она денормализует преподавателя и группу СНИМКОМ, а не ссылкой на назначение.
 * Без этого правка «доходит» только до раздела «Назначения»: сетка, отчёты и экспорт
 * продолжают показывать прежнего преподавателя (полная перегенерация их перетирает, а
 * инкрементальная уже стоящее не трогает вовсе).</p>
 *
 * <p>Парное по смыслу к {@link PlacementChangedEvent}, но по другой оси: там меняется само
 * размещение (перенос, замок), здесь — сущность, снимок которой размещение несёт.</p>
 *
 * @see ru.services.ScheduleSynchronizer
 */
@Getter
public class AssignmentsChangedEvent {

    /**
     * Изменённые назначения.
     */
    private final List<Integer> assignmentIds;

    public AssignmentsChangedEvent(List<Integer> assignmentIds) {
        this.assignmentIds = List.copyOf(assignmentIds);
    }
}
