package ru.services.importing;

import ru.services.importing.ParsedSheet.CutKind;

import java.time.LocalDate;
import java.util.List;

/**
 * Сводка по одному разобранному файлу — то, что видно ДО всякого сопоставления с нашей базой.
 *
 * <p>Отвечает на единственный вопрос: <b>что программа поняла из файла</b>. Ни одна строка отсюда
 * никуда не пишется — по решению И-10 первый прогон импорта вообще не создаёт сущностей, потому
 * что завести преподавателя легко, а убрать (когда на него сошлётся назначение) уже нет.</p>
 *
 * <p>Списки обозначений (дисциплины, группы, аудитории, маркеры) отдаются целиком: именно по ним
 * человек глазом видит, что разбор поехал, — раньше, чем это покажет сопоставление.</p>
 *
 * @param file        имя файла, как его прислали
 * @param cut         опознанный разрез
 * @param owner       чей это разрез: «911», «252-3», «к-н Ветров Р.И. ктн»
 * @param faculty     факультет из шапки
 * @param department  кафедра из шапки
 * @param studyYear   первый год учебного года
 * @param semester    «осенний» / «весенний»
 * @param lessons     сколько ячеек прочитано как занятия
 * @param markers     сколько ячеек — маркеры занятости («ЭкзС», «Отп»)
 * @param firstDate   самая ранняя дата занятия
 * @param lastDate    самая поздняя дата занятия
 * @param disciplines обозначения дисциплин, встреченные в ячейках
 * @param groups      номера групп
 * @param rooms       номера аудиторий
 * @param markerCodes коды маркеров занятости
 * @param footer      подвал: дисциплины с преподавателями (только групповой разрез)
 * @param problems    замечания разбора
 * @param sample      несколько первых занятий — чтобы глазом сверить с файлом
 */
public record SheetInspection(
        String file,
        CutKind cut,
        String owner,
        String faculty,
        String department,
        Integer studyYear,
        String semester,
        int lessons,
        int markers,
        LocalDate firstDate,
        LocalDate lastDate,
        List<String> disciplines,
        List<String> groups,
        List<String> rooms,
        List<String> markerCodes,
        List<DisciplineFooterParser.FooterRow> footer,
        List<String> problems,
        List<CellDialect.LessonEntry> sample
) {
}
