package ru.dto.educatorDictionary;

/**
 * Строка справочника регалий (звание, род службы, отрасль науки).
 *
 * @param id            идентификатор
 * @param name          полное имя: «полковник»
 * @param shortName     сокращение <b>без точек</b>: «п-к». По нему собирается подпись
 *                      преподавателя и будет опознаваться звание при разборе чужого файла
 * @param sortOrder     порядок в списке (звания — по старшинству, шаг 10)
 * @param active        погашенное значение не предлагается в выборе, но остаётся в истории
 * @param educatorCount сколько преподавателей ссылается на строку
 * @param deletable     можно ли удалять. <b>Решение считает бэк, а не выводит фронт:</b> FK стоят
 *                      с {@code ON DELETE RESTRICT}, и удаление занятой строки БД отклонит. Тот
 *                      же приём, что у {@code delete-impact} аудитории и подразделения, только
 *                      цена здесь — одно число, поэтому отдельного эндпоинта ему не нужно
 */
public record DictionaryEntryDto(
        Integer id,
        String name,
        String shortName,
        Integer sortOrder,
        boolean active,
        long educatorCount,
        boolean deletable
) {}
