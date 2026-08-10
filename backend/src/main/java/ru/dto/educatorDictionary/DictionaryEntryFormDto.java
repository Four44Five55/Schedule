package ru.dto.educatorDictionary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Тело создания и правки строки справочника.
 *
 * <p>Одна форма на обе операции: у справочника нет полей, которые задаются только при создании
 * или только при правке. Заводить два одинаковых record'а ради симметрии с остальными сущностями
 * значило бы дублировать без причины.</p>
 *
 * @param name      полное имя, обязательно
 * @param shortName сокращение, обязательно и уникально: по нему собирается подпись. Хранится
 *                  <b>без точек</b> — точки, где они нужны по форме («к.т.н.»), расставляет
 *                  форматтер
 * @param sortOrder порядок; {@code null} → 0
 * @param active    активность; {@code null} → true (новое значение сразу доступно в выборе)
 */
public record DictionaryEntryFormDto(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 50) String shortName,
        Integer sortOrder,
        Boolean active
) {}
