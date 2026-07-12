package ru.dto.command;

import java.util.UUID;

/**
 * Размещение, которое переупорядочивание оставило «под флаг», с причиной — для
 * предупреждения на фронте. {@code reason} — имя константы
 * {@link ru.services.reindex.ReorderProblem.Reason} (напр. {@code CHAIN_BROKEN}); текст
 * сообщения формирует фронт.
 *
 * @param placementId размещение под флагом
 * @param reason      причина (имя enum-константы)
 */
public record ReorderProblemDto(UUID placementId, String reason) {
}
