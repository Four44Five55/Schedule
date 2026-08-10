package ru.dto.educator;

import java.util.Set;
import ru.enums.AcademicDegree;
import ru.enums.AcademicTitle;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;

/**
 * DTO для полного представления информации о преподавателе.
 *
 * <p>Регалии едут <b>и структурой, и готовой подписью</b>. Структура нужна форме и фильтрам,
 * подпись — карточке и выгрузке: собирает её бэк ({@code EducatorTitles}), потому что потребителей
 * у формата двое и вторая независимая склейка на фронте разошлась бы с первой (аудит §1.2 —
 * ровно это уже случалось с описателем занятия). Прецедент поля-удобства рядом — {@code orgUnitName}.</p>
 *
 * @param id                 Уникальный идентификатор.
 * @param name               ФИО преподавателя.
 * @param preferredDays      Набор предпочитаемых дней недели.
 * @param preferredTimeSlots Набор предпочитаемых пар.
 * @param compactSchedule    Флаг компактности расписания.
 * @param orgUnitId          Подразделение (кафедра или отдел); {@code null} — не распределён.
 * @param orgUnitName        Название подразделения — чтобы список читался без склейки на клиенте.
 * @param specialRankId      Специальное звание; {@code null} — не указано.
 * @param specialRankName    Имя звания («полковник») — для показа без второго запроса.
 * @param rankServiceId      Род службы к званию; почти всегда {@code null}.
 * @param rankServiceName    Имя рода службы («юстиции»).
 * @param academicDegree     Уровень учёной степени (кандидат/доктор).
 * @param scienceBranchId    Отрасль науки степени; вместе с уровнем даёт «к.т.н.».
 * @param scienceBranchName  Имя отрасли («технические»).
 * @param academicTitle      Учёное звание (доцент/профессор) — <b>не должность</b>.
 * @param titleLine          Готовая подпись: «п-к юст Иванов И.И., к.т.н., доц». Совпадает с ФИО,
 *                           если регалий нет.
 */
public record EducatorDto(
        Integer id,
        String name,
        Set<DayOfWeek> preferredDays,
        Set<TimeSlotPair> preferredTimeSlots,
        boolean compactSchedule,
        Integer orgUnitId,
        String orgUnitName,
        Integer specialRankId,
        String specialRankName,
        Integer rankServiceId,
        String rankServiceName,
        AcademicDegree academicDegree,
        Integer scienceBranchId,
        String scienceBranchName,
        AcademicTitle academicTitle,
        String titleLine
) {}
