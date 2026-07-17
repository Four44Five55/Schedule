package ru.services.reindex;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Политика подбора комнат при пересортировке по умолчанию (фаза 1). Чистая функция, тестируется
 * юнитами; {@code @Component} — чтобы {@code TrackReorderService} получил её через контракт
 * {@link ReorderRoomResolver} (DIP), а фаза 2 могла заменить реализацию, не трогая сервис.
 *
 * <p><b>Две ветки — по природе комнаты.</b></p>
 * <ol>
 *   <li><b>Комната взаимозаменяема</b> (у слота нет жёсткой привязки) → <i>комната остаётся с
 *       ячейкой</i>: занятие, приехавшее в ячейку, берёт ту комнату, что там уже была. Множество
 *       физических броней «ячейка → комната» при этом не меняется, поэтому такая пересортировка
 *       <b>провабельно не создаёт и не чинит</b> конфликтов аудиторий. Это массовый случай
 *       (практики группы в родной аудитории).</li>
 *   <li><b>Жёсткая привязка</b> (required-аудитория или пул) → комната <i>едет с занятием</i>:
 *       требование нельзя нарушить, посадив занятие в чужую комнату. Проверяем, свободна ли его
 *       комната на целевой ячейке (против не-классных размещений). Свободна — едет со своей;
 *       занята — сажаем в базовую аудиторию группы и помечаем
 *       {@link ReorderProblem.Reason#AUDITORIUM_CONFLICT} (перенос не блокируем — требование
 *       заказчика; диспетчер поправит вручную сменой комнаты).</li>
 * </ol>
 *
 * <p><b>Приговор — числом занятости, а не догадкой.</b> Проверка идёт по снимку занятости
 * не-классных размещений: они при пересортировке не двигаются, а внутри класса ячейки уникальны,
 * поэтому этого снимка достаточно и он полон. Ветку «переподбор любой свободной через
 * {@code AuditoriumSelector}» сюда сознательно не тащим — это фаза 2 (нужен workspace).</p>
 */
@Component
public class DefaultReorderRoomResolver implements ReorderRoomResolver {

    @Override
    public ReorderRoomPlan resolve(ReorderRoomContext context) {
        // Комнаты, которые сейчас стоят в каждой ячейке класса, — для ветки «остаётся с ячейкой».
        Map<Cell, Set<Integer>> roomsByCurrentCell = new HashMap<>();
        for (ReorderRoomInput in : context.classPlacements()) {
            roomsByCurrentCell.put(in.currentCell(), in.currentRoomIds());
        }

        Map<UUID, Set<Integer>> rooms = new HashMap<>();
        List<ReorderProblem> problems = new ArrayList<>();

        for (ReorderRoomInput in : context.classPlacements()) {
            if (!in.moved()) {
                continue; // не переехал — комнаты не трогаем
            }

            if (!in.hardRoomBound()) {
                // Комната остаётся с ячейкой: берём то, что стояло в целевой ячейке.
                rooms.put(in.placementId(),
                        roomsByCurrentCell.getOrDefault(in.targetCell(), in.currentRoomIds()));
                continue;
            }

            // Жёсткая привязка: комната едет с занятием, если свободна на целевой ячейке.
            boolean free = in.currentRoomIds().stream()
                    .noneMatch(roomId -> context.occupiedByOthers()
                            .contains(new RoomSlot(roomId, in.targetCell())));
            if (free) {
                rooms.put(in.placementId(), in.currentRoomIds());
            } else if (in.baseRoomId() != null) {
                rooms.put(in.placementId(), Set.of(in.baseRoomId()));
                problems.add(new ReorderProblem(in.placementId(), ReorderProblem.Reason.AUDITORIUM_CONFLICT));
            } else {
                // Базовой аудитории нет — лучше некуда, оставляем свою и помечаем.
                rooms.put(in.placementId(), in.currentRoomIds());
                problems.add(new ReorderProblem(in.placementId(), ReorderProblem.Reason.AUDITORIUM_CONFLICT));
            }
        }

        return new ReorderRoomPlan(rooms, problems);
    }
}
