package ru.dto.constraint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Тело создания и правки вида ограничения.
 *
 * <p>Одна форма на обе операции — полей «только при создании» у справочника нет. Код сюда не
 * входит: у новых видов его выдаёт сервис, у системных он неизменен (на него ссылаются уже
 * проставленные ограничения).</p>
 *
 * @param name      полное название, обязательно и уникально
 * @param shortName сокращение для ячейки сетки, обязательно и уникально
 * @param color     ключ палитры ({@code amber}, {@code sky}…); {@code null} → {@code slate}.
 *                  Не hex: классы Tailwind собираются статически, произвольный цвет из базы в
 *                  вёрстку не подставить
 * @param sortOrder порядок; {@code null} → 0
 * @param active    активность; {@code null} → true (новый вид сразу доступен в выборе)
 */
public record ConstraintKindFormDto(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 50) String shortName,
        @Size(max = 20) String color,
        Integer sortOrder,
        Boolean active
) {}
