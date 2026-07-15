package ru.dto.location;

/**
 * Предпросмотр последствий удаления локации.
 *
 * <p>Единственный блокер — привязанные корпуса: {@code building.location_id → location ON DELETE
 * RESTRICT}, поэтому БД не даст удалить локацию, пока у неё есть корпуса, и раньше это вылезало
 * сырым 500. Тогда {@code deletable = false}. Ничего каскадного локация не уносит, в read-модель
 * не денормализуется.</p>
 *
 * <p><b>{@code deletable} — поле, а не вывод на фронте</b> (прецедент —
 * {@code AuditoriumDeletionImpactDto}/{@code BuildingDeletionImpactDto}): правило считается ЗДЕСЬ,
 * {@code LocationService.deleteLocation} им же и отказывает, UI лишь показывает семантику.</p>
 *
 * @param locationId    id локации
 * @param name          название (для текста подтверждения)
 * @param deletable     можно ли удалять (нет привязанных корпусов)
 * @param buildingCount корпусов привязано; &gt; 0 → {@code deletable = false}
 */
public record LocationDeletionImpactDto(
        Integer locationId,
        String name,
        boolean deletable,
        long buildingCount
) {}
