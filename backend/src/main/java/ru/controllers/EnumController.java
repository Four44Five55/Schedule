package ru.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.EnumDto;
import ru.enums.*;

import java.util.Arrays;
import java.util.List;

/**
 * Контроллер для получения справочных значений (enum-ов).
 * Фронтенд запрашивает эти данные один раз при загрузке приложения
 * и использует для отображения лейблов, заполнения select-ов и фильтров.
 * <p>
 * ЕДИНЫЙ ИСТОЧНИК ПРАВДЫ: все лейблы хранятся только в Java-enum-ах.
 * На фронтенде НЕТ дублирования значений.
 */
@RestController
@RequestMapping("/api/enums")
public class EnumController {

    /**
     * Виды занятий: Лекция, Практика, Лабораторная и т.д.
     */
    @GetMapping("/kind-of-study")
    public List<EnumDto> getKindOfStudy() {
        return Arrays.stream(KindOfStudy.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviationName()
                ))
                .toList();
    }

    /**
     * Дни недели: Понедельник – Воскресенье.
     */
    @GetMapping("/day-of-week")
    public List<EnumDto> getDaysOfWeek() {
        return Arrays.stream(DayOfWeek.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviation()
                ))
                .toList();
    }

    /**
     * Временные слоты (пары): 1-я, 2-я, 3-я, 4-я с указанием времени.
     */
    @GetMapping("/time-slots")
    public List<EnumDto> getTimeSlots() {
        return Arrays.stream(TimeSlotPair.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviation(),
                        e.getTimeRange()  // "09:00 – 10:35"
                ))
                .toList();
    }

    /**
     * Виды ограничений: Командировка, Отпуск, Сессия и т.д.
     */
    @GetMapping("/kind-of-constraints")
    public List<EnumDto> getKindOfConstraints() {
        return Arrays.stream(KindOfConstraints.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviationName()
                ))
                .toList();
    }

    /**
     * Типы учебных периодов: Осенний семестр, Весенняя сессия и т.д.
     */
    @GetMapping("/period-type")
    public List<EnumDto> getPeriodTypes() {
        return Arrays.stream(PeriodType.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviation()
                ))
                .toList();
    }

    /**
     * Виды подразделений: Институт, Факультет, Кафедра, Отдел.
     */
    @GetMapping("/org-unit-type")
    public List<EnumDto> getOrgUnitTypes() {
        return Arrays.stream(OrgUnitType.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviationName()
                ))
                .toList();
    }

    /**
     * Уровни учёной степени: кандидат наук, доктор наук.
     *
     * <p>Enum, а не справочник: два значения, заданы нормативкой. Вторая половина степени —
     * отрасль науки — наоборот, справочник, который ведёт пользователь
     * ({@code /api/educator-dictionaries/science-branches}). {@code extra} несёт форму без
     * отрасли («канд наук»): она нужна, когда отрасль не указана, — записи «к.н.» не существует.</p>
     */
    @GetMapping("/academic-degree")
    public List<EnumDto> getAcademicDegrees() {
        return Arrays.stream(AcademicDegree.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviation(),
                        e.getStandalone()
                ))
                .toList();
    }

    /**
     * Учёные звания: доцент, профессор. ⚠️ Не должность — «доцент кафедры» это другое поле,
     * которого в модели пока нет.
     */
    @GetMapping("/academic-title")
    public List<EnumDto> getAcademicTitles() {
        return Arrays.stream(AcademicTitle.values())
                .map(e -> new EnumDto(
                        e.name(),
                        e.getFullName(),
                        e.getAbbreviation()
                ))
                .toList();
    }

    /**
     * Получить ВСЕ enum-ы одним запросом.
     * Удобно для инициализации приложения — один запрос вместо восьми.
     */
    @GetMapping("/all")
    public AllEnumsDto getAllEnums() {
        return new AllEnumsDto(
                getKindOfStudy(),
                getDaysOfWeek(),
                getTimeSlots(),
                getKindOfConstraints(),
                getPeriodTypes(),
                getOrgUnitTypes(),
                getAcademicDegrees(),
                getAcademicTitles()
        );
    }

    /**
     * DTO-обёртка для всех enum-ов сразу.
     */
    public record AllEnumsDto(
            List<EnumDto> kindOfStudy,
            List<EnumDto> daysOfWeek,
            List<EnumDto> timeSlots,
            List<EnumDto> kindOfConstraints,
            List<EnumDto> periodTypes,
            List<EnumDto> orgUnitTypes,
            List<EnumDto> academicDegrees,
            List<EnumDto> academicTitles
    ) {
    }
}
