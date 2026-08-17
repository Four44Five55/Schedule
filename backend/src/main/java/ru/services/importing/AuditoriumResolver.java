package ru.services.importing;

import ru.entity.Auditorium;
import ru.services.importing.RoomNumberDecoder.RoomNumber;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Номер комнаты из файла → наша {@link Auditorium}. Единый владелец правила.
 *
 * <h2>Правило</h2>
 * <p>Совпадение по <b>имени комнаты и корпусу</b> внутри <b>выбранной локации</b> (И-17). Корпус в
 * номере может отсутствовать («Сп. зал») — тогда он в отборе не участвует. Кандидат ровно один —
 * берём; ни одного или несколько — <b>не берём никого</b>.</p>
 *
 * <p><b>Почему «несколько» это отказ, а не выбор первого.</b> Локации в файле нет вовсе, а корпус
 * «3» законно существует в нескольких кампусах: занятие встало бы в физически другом здании и
 * выглядело бы правдоподобно. Та же причина, по которой локация — обязательный параметр прогона.</p>
 *
 * <h2>Зачем отдельный класс</h2>
 * <p>Потребителей двое: сверка («что из файла есть у нас») и запись размещений («в какую комнату
 * ставим»). Две копии правила разошлись бы при первой правке, и человек подтверждал бы одно
 * сопоставление, а в расписание попадало другое — ровно так уже расходилась «та же дисциплина»,
 * пока у неё не появился {@link DisciplineDictionary}.</p>
 *
 * <p><b>Чистая функция</b>: список комнат приходит параметром, в базу класс не ходит.</p>
 */
public final class AuditoriumResolver {

    private AuditoriumResolver() {
    }

    /**
     * Комнаты, подходящие под номер из файла.
     *
     * @param all        все аудитории базы
     * @param raw        номер как в файле: «252-3», «Сп. зал»
     * @param locationId локация прогона; {@code null} — не выбрана, отбор по локации не идёт
     * @return кандидаты; пусто — такой комнаты у нас нет
     */
    public static List<Auditorium> candidates(List<Auditorium> all, String raw, Integer locationId) {
        RoomNumber decoded = RoomNumberDecoder.decode(raw);
        if (decoded == null) {
            return List.of();
        }
        return all.stream()
                .filter(room -> key(room.getName()).equals(key(decoded.name())))
                .filter(room -> decoded.buildingUnknown()
                        || key(room.getBuilding().getName()).equals(key(decoded.building())))
                .filter(room -> locationId == null
                        || locationId.equals(room.getBuilding().getLocation().getId()))
                .toList();
    }

    /**
     * Комната, если она <b>одна и только одна</b>.
     *
     * @return найденная комната либо пусто — и пусто это законный ответ, а не сбой: размещение
     * встанет без аудитории, а строка об этом попадёт в отчёт. Время занятия при этом не врёт,
     * тогда как чужая комната врала бы молча.
     */
    public static Optional<Auditorium> unique(List<Auditorium> all, String raw, Integer locationId) {
        List<Auditorium> found = candidates(all, raw, locationId);
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
