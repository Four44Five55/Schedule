package ru.dto.command;

import ru.enums.KindOfStudy;

import java.util.List;

/**
 * Запрос на аддитивную генерацию одного курса (дисциплины) в сессии.
 *
 * @param studyPeriodId учебный период (даты генерации)
 * @param courseId      курс (дисциплина в периоде), чьи неразмещённые занятия раскладываются
 * @param kinds         опциональный фильтр по видам (напр. только {@code LECTURE}, или «практики»
 *                      = все виды кроме лекций); {@code null}/пусто — вся дисциплина (все виды)
 * @param educatorIds   опциональный фильтр по преподавателям: раскладываются только занятия,
 *                      которые ведёт кто-то из них; {@code null}/пусто — все преподаватели курса
 */
public record GenerateCourseRequest(
        Integer studyPeriodId,
        Integer courseId,
        List<KindOfStudy> kinds,
        List<Integer> educatorIds
) {}
