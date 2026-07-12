package ru.services.reindex;

import java.util.UUID;

/**
 * Размещение, которое переупорядочивание оставило «под флаг», с причиной — для
 * предупреждения на фронте (причину считает бэк, фронт только рендерит).
 *
 * @param placementId размещение под флагом
 * @param reason      причина
 */
public record ReorderProblem(UUID placementId, Reason reason) {

    public enum Reason {
        /**
         * Сцепка распалась: связанные слоты после сдвига оказались на несоседних ячейках.
         * Сдвиг выполнен, но неразрывность нарушена — пользователь пересобирает вручную.
         */
        CHAIN_BROKEN
    }
}
