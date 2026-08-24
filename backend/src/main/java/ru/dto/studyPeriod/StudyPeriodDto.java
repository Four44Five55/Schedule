package ru.dto.studyPeriod;

import ru.enums.PeriodType;
import java.time.LocalDate;

/**
 * DTO для представления информации об учебном периоде.
 *
 * <p>Три поля {@code signer*} — подпись под выгруженным расписанием (чейнджлог 026): должность,
 * регалии и фамилия с инициалами того, кто расписание подписывает. Это <b>реквизит документа</b>,
 * поэтому свободный текст, а не ссылка на преподавателя, и живёт он у периода: подписант меняется
 * от семестра к семестру, а выгрузка и так период-центрична.</p>
 */
public record StudyPeriodDto(
        Integer id,
        String name,
        int studyYear,
        PeriodType periodType,
        LocalDate startDate,
        LocalDate endDate,
        String signerPosition,
        String signerCredentials,
        String signerName
) {}
