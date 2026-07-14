package ru.dto.command;

import ru.enums.KindOfStudy;

import java.util.List;

/**
 * Запрос на очистку размещений сессии (КРОМЕ закреплённых). Поля опциональны и сужают охват.
 *
 * @param courseId курс (дисциплина) или {@code null} — все курсы сессии
 * @param kinds       виды занятий к удалению или {@code null}/пусто — все виды
 *                    (напр. «кроме лекций» = все виды, кроме {@code LECTURE})
 * @param educatorIds преподаватели или {@code null}/пусто — все (зеркально охвату генерации:
 *                    удаляются только занятия, которые ведёт кто-то из них)
 * @param version     ожидаемая версия сессии (optimistic lock). Очистка сносит расписание пачкой,
 *                    поэтому «затереть чужую работу молча» тут дороже всего
 */
public record ClearPlacementsRequest(
        Integer courseId,
        List<KindOfStudy> kinds,
        List<Integer> educatorIds,
        Long version
) {}
