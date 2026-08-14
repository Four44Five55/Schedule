package ru.services.importing;

/**
 * Расшифровка номера аудитории из чужой выгрузки: {@code «252-3»} → комната 252, корпус 3.
 *
 * <p><b>Чистая функция без Spring и БД</b> — как {@link GroupNumberDecoder} и
 * {@link ScheduleSheetParser}.</p>
 *
 * <p><b>Зачем вообще разбирать.</b> В ячейке напечатан только номер, а одноимённые комнаты в разных
 * корпусах — разные помещения с разной принадлежностью. Наша схема это уже выражает:
 * {@code auditorium} уникален по паре {@code (building_id, name)}. Суффикс после дефиса и есть
 * корпус (подтверждено заказчиком), поэтому <b>новой колонки и миграции не требуется</b> — нужно
 * лишь не потерять корпус при разборе.</p>
 *
 * <p><b>Корпус хранится ссылкой, а не в имени.</b> Класть в {@code auditorium.name} строку «252-3»
 * целиком было бы вторым представлением одного факта: корпус оказался бы и в поле, и в имени, и
 * они разошлись бы при первой же правке. Имя — «252», корпус — {@code building_id}.</p>
 *
 * <p><b>Комната без корпуса — не ошибка.</b> «Сп. зал» суффикса не имеет и разбирается как имя без
 * корпуса. Это законное значение, а не кривизна: решение «какой из одноимённых» принимает слой
 * сопоставления, и он же обязан сказать «одноимённых несколько» вместо того, чтобы выбрать первую.
 * Поэтому здесь такой случай не помечается проблемой — функция сообщает, что видит.</p>
 */
public final class RoomNumberDecoder {

    private RoomNumberDecoder() {
    }

    /**
     * Разобранный номер аудитории.
     *
     * @param raw      как в файле: «252-3», «Сп. зал»
     * @param name     имя комнаты без корпуса: «252», «Сп. зал» — ложится на {@code auditorium.name}
     * @param building код корпуса: «3»; {@code null}, если в номере его нет
     */
    public record RoomNumber(String raw, String name, String building) {

        /** Корпус в номере не указан — сопоставлять придётся по имени среди всех корпусов. */
        public boolean buildingUnknown() {
            return building == null;
        }
    }

    /**
     * Разбирает номер аудитории. Тотальна: {@code null} и пустая строка дают пустой разбор.
     *
     * <p>Правило одно: <b>если последний сегмент после дефиса — число, это корпус</b>. Всё
     * остальное целиком считается именем. Такой разбор не ломается о комнаты без суффикса
     * («Сп. зал») и о буквенные добавки, которых мы ещё не видели: они просто останутся в имени,
     * а не будут молча приняты за корпус.</p>
     *
     * @param raw номер как в ячейке файла; пробелы по краям снимаются
     * @return разбор; при пустом входе — {@code null}
     */
    public static RoomNumber decode(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        int dash = value.lastIndexOf('-');
        if (dash <= 0 || dash == value.length() - 1) {
            return new RoomNumber(value, value, null);
        }

        String head = value.substring(0, dash).trim();
        String tail = value.substring(dash + 1).trim();
        if (head.isEmpty() || !isDigits(tail)) {
            return new RoomNumber(value, value, null);
        }
        return new RoomNumber(value, head, tail);
    }

    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
