package ru.dto;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import java.time.LocalDate;
import java.util.List;
/**
 * DTO для представления одного размещённого занятия в расписании.
 * Плоская структура — все данные в одном объекте для удобства отображения на фронтенде.
 *
 * @param id                    Уникальный ID занятия (Lesson).
 * @param date                  Дата проведения занятия (yyyy-MM-dd).
 * @param timeSlotPair          Временной слот (1-я, 2-я, 3-я, 4-я пара).
 * @param disciplineName        Название дисциплины (например, "Программирование").
 * @param disciplineAbbreviation Аббревиатура дисциплины (например, "Прогр.").
 * @param kindOfStudy           Вид занятия (лекция, практика и т.д.).
 * @param kindOfStudyName       Русское название вида занятия.
 * @param kindOfStudyAbbr       Аббревиатура вида занятия (Л, ПЗ, ЛР, ...).
 * @param position              Номер занятия в учебном плане (1, 2, 3...).
 * @param themeNumber           Номер темы занятия (например, "1.1").
 * @param themeTitle            Название темы занятия.
 * @param educatorIds           Список ID преподавателей.
 * @param educatorNames         Список имён преподавателей.
 * @param streamName            Название потока (например, "Поток ПИ-1").
 * @param groupNames            Список названий групп (например, ["ПИ-101", "ПИ-102"]).
 * @param auditoriumNames       Список названий аудиторий (например, ["101", "201"]).
 * @param auditoriumIds         Список ID аудиторий.
 */
public record ScheduledLessonDto(
        Integer id,
        LocalDate date,
        TimeSlotPair timeSlotPair,
        String disciplineName,
        String disciplineAbbreviation,
        KindOfStudy kindOfStudy,
        String kindOfStudyName,
        String kindOfStudyAbbr,
        Integer position,
        String themeNumber,
        String themeTitle,
        List<Integer> educatorIds,
        List<String> educatorNames,
        String streamName,
        List<String> groupNames,
        List<String> auditoriumNames,
        List<Integer> auditoriumIds
) {
}
