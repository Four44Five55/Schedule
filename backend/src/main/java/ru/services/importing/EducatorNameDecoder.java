package ru.services.importing;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подпись преподавателя из чужой выгрузки → звание · фамилия · инициалы · регалии.
 *
 * <p>Разбирает три места, где подпись встречается в одном и том же виде: подвал группового файла
 * («Волков В.Ф. двн проф; п/п-к Чащин С.В.»), шапку преподавательского файла («к-н Горяинов Р.И.
 * ктн») и <b>имя файла</b> («ГоряиновР.И.» — без пробела, «старая реализация»). Последнее выходит
 * само: якорь разбора — инициалы, а не пробелы.</p>
 *
 * <h2>Три решения, без которых это место врёт тихо</h2>
 *
 * <p><b>1. Ключ сопоставления — фамилия и инициалы, и только они.</b> Звание меняется во времени
 * (капитан становится майором), степень защищается, должность двигается. Всё это в ключ входить не
 * может, иначе один и тот же человек в двух файлах разных лет станет двумя людьми и разрежет
 * расписание надвое — тише и вреднее, чем кривые занятия. Расхождение файла с базой по званию это
 * строка отчёта «уточнить», а не отказ и не тихая перезапись.</p>
 *
 * <p><b>2. Звание снимается по справочнику, а не эвристикой.</b> На вход подаётся плоский список
 * {@code special_rank.short_name} — тем же приёмом, каким чистая функция обхода дерева берёт список
 * подразделений. Список сходится с файлом дословно («п/п-к», «к-н»), поэтому угадывать нечего:
 * префикс либо есть в справочнике, либо это часть фамилии.</p>
 *
 * <p><b>3. Регалии НЕ разбираются на степень и звание — отдаются хвостом как есть.</b> Хвост
 * («двн проф», «ктн») — это уже собранная подпись, а не части, и собирать её умеет ровно один класс
 * в проекте, {@code EducatorTitles}. Разбирать её обратно значило бы завести второго владельца
 * формата.</p>
 *
 * <p>⚠️ Сравнивать хвост с нашей подписью <b>можно только после выравнивания справочника</b>
 * (решение заказчика 2026-08-14): сокращения степени приведены к слитной форме без точек — «ктн»,
 * «кфмн», «двн» — ровно как в файле, а расхождения в {@code science_branch.short_name} («воен»
 * против «в») заказчик правит данными. Пока значения не выровнены, сверка хвоста даст ложные
 * расхождения, поэтому она и остаётся строкой отчёта «уточнить», а не отказом.</p>
 *
 * <p><b>Тотальна</b> ({@link GroupNumberDecoder}, {@link ScheduleSheetParser}): не разобрал — вернул
 * {@link EducatorName} с {@code problem}, а не исключение. Первый прогон импорта заведомо кривой, и
 * список «вот кого не понял» ценнее стектрейса.</p>
 *
 * @see <a href="file:../../../../../docs/IMPORT_FORMAT.md">IMPORT_FORMAT.md §8</a>
 */
public final class EducatorNameDecoder {

    private EducatorNameDecoder() {
    }

    /**
     * Инициалы — якорь разбора: «В.Ф.», «В. Ф.», «Р.И.».
     *
     * <p>Всё до них (за вычетом звания) — фамилия, всё после — регалии. Отсюда бесплатно выходят два
     * тяжёлых случая: <b>двойная фамилия</b> («Петров-Водкин А.А.») и <b>имя файла без пробела</b>
     * («ГоряиновР.И.»), на которых сломалась бы любая эвристика «где кончается фамилия».</p>
     *
     * <p><b>Последняя точка необязательна</b> (2026-08-15, по живым данным): «Иванов И.И» без точки
     * после отчества — то же имя, и терять человека из-за пропущенной точки нельзя. Первая точка
     * обязательна: без неё «Иванов ИИ» неотличим от обычного текста.</p>
     */
    private static final Pattern INITIALS = Pattern.compile("([А-ЯЁA-Z])\\.\\s?([А-ЯЁA-Z])\\.?");

    /**
     * Разобранная подпись.
     *
     * @param raw         подпись как в файле
     * @param rank        краткое звание из справочника («п/п-к») либо {@code null}
     * @param surname     фамилия («Чащин»)
     * @param initials    инициалы, приведённые к «И.О.» без пробела
     * @param credentials хвост регалий как в файле («двн проф») либо {@code null}
     * @param problem     причина, по которой подпись не разобрана; {@code null} — разобрана
     */
    public record EducatorName(
            String raw,
            String rank,
            String surname,
            String initials,
            String credentials,
            String problem
    ) {
        public boolean recognized() {
            return problem == null;
        }

        /**
         * Ключ сопоставления с нашим {@code educator.name}: «Чащин С.В.».
         *
         * <p>Звания и степени в ключе нет намеренно — см. решение 1 в описании класса.</p>
         *
         * @return ключ либо {@code null}, если подпись не разобрана
         */
        public String key() {
            return recognized() ? surname + " " + initials : null;
        }

        static EducatorName notRecognized(String raw, String problem) {
            return new EducatorName(raw, null, null, null, null, problem);
        }
    }

    /**
     * Разбирает подпись.
     *
     * @param raw            подпись из файла: «п/п-к Чащин С.В.», «Волков В.Ф. двн проф», «ГоряиновР.И.»
     * @param rankShortNames краткие звания из справочника ({@code special_rank.short_name}); может
     *                       быть пустым — тогда звание просто не снимается
     * @return разбор либо описание, почему не вышло
     */
    public static EducatorName decode(String raw, Collection<String> rankShortNames) {
        // Нормализация общая с разбором полотна: в подписях из подвала неразрывные пробелы
        // встречаются постоянно, а вторая копия правила разъехалась бы с первой.
        String value = ScheduleSheetParser.normalize(raw);
        if (value.isEmpty()) {
            return EducatorName.notRecognized(raw, "пустая подпись преподавателя");
        }

        String rank = matchRank(value, rankShortNames);
        String rest = rank == null ? value : value.substring(rank.length()).trim();

        Matcher initials = INITIALS.matcher(rest);
        if (!initials.find()) {
            return EducatorName.notRecognized(raw, "не найдены инициалы вида «И.О.» — сопоставить не по чему");
        }

        // Разделителем перед инициалами бывает и точка: «Иванов.И.И.». Она принадлежит разметке
        // подписи, а не фамилии — иначе ключ станет «Иванов. И.И.» и не совпадёт с «Иванов И.И.»,
        // причём молча: человек просто не найдётся в базе.
        String surname = rest.substring(0, initials.start()).replaceAll("[.\\s]+$", "").trim();
        if (surname.isEmpty()) {
            return EducatorName.notRecognized(raw, "инициалы есть, а фамилии перед ними нет");
        }

        String credentials = rest.substring(initials.end()).trim();
        return new EducatorName(
                raw,
                rank,
                surname,
                initials.group(1) + "." + initials.group(2) + ".",
                credentials.isEmpty() ? null : credentials,
                null
        );
    }

    /**
     * Ключ сопоставления для <b>нашего</b> {@code educator.name} — вторая половина той же пары.
     *
     * <p>Обе формы записи дают один ключ: «Иванов И.И.» и «Иванов Иван Иванович» → {@code «Иванов
     * И.И.»}. Приводить к ключу надо именно обе стороны, а не одну: сравнение «как в файле» с «как в
     * базе» — это и есть то место, где сопоставление молча теряет людей.</p>
     *
     * <p>Отчество может отсутствовать («Иванов Иван») — тогда ключ короче на одну букву и с
     * «Иванов И.И.» не совпадёт. Это правильно: двух разных людей нельзя склеивать по неполному
     * совпадению, такая строка обязана попасть в «не сопоставлено» и на глаза человеку.</p>
     *
     * @param storedName имя из нашей базы
     * @return ключ либо {@code null}, если из имени ключа не выходит
     */
    public static String keyOfStoredName(String storedName) {
        return keyOfStoredName(storedName, List.of());
    }

    /**
     * То же, но со справочником званий.
     *
     * <p>По модели звание живёт отдельным полем, и в {@code educator.name} его быть не должно. Но
     * данные заводились руками и раньше этого поля, поэтому «п-к Иванов И.И.» в имени вполне
     * возможен — а тогда ключ получился бы «п-к Иванов И.И.» и <b>никогда</b> не совпал бы с ключом
     * из файла. Промах был бы тихим: строка просто ушла бы в «не сопоставлено», и объяснили бы её
     * тем, что человека нет в базе.</p>
     *
     * @param storedName     имя из нашей базы
     * @param rankShortNames краткие звания из справочника
     */
    public static String keyOfStoredName(String storedName, Collection<String> rankShortNames) {
        EducatorName parsed = decode(storedName, rankShortNames);
        if (parsed.recognized()) {
            return parsed.key();
        }

        String[] words = ScheduleSheetParser.normalize(storedName).split(" ");
        if (words.length < 2 || words[0].isEmpty()) {
            return null;
        }
        StringBuilder initials = new StringBuilder();
        for (int i = 1; i < words.length && i <= 2; i++) {
            if (!words[i].isEmpty()) {
                initials.append(Character.toUpperCase(words[i].charAt(0))).append('.');
            }
        }
        return initials.isEmpty() ? null : words[0] + " " + initials;
    }

    /**
     * Самое длинное звание-префикс из справочника.
     *
     * <p>Длиннейшее, а не первое совпавшее: «мл л-т» начинается не с «л-т», зато «ст л-т» и «л-т»
     * различаются только началом, и порядок перебора не должен решать исход. Сравнение
     * без учёта регистра — регистр это оформление, а не признак.</p>
     *
     * @return звание в том виде, как оно записано <b>в файле</b> (не в справочнике), либо {@code null}
     */
    private static String matchRank(String value, Collection<String> rankShortNames) {
        if (rankShortNames == null) {
            return null;
        }
        List<String> byLengthDesc = rankShortNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        String lower = value.toLowerCase();
        for (String rank : byLengthDesc) {
            String candidate = rank.trim().toLowerCase();
            // Звание отделяется пробелом: иначе «м-р» съел бы начало фамилии «М-ра…», а «п-к» —
            // фамилию, которая случайно начинается на эти буквы.
            if (lower.startsWith(candidate + " ")) {
                return value.substring(0, candidate.length());
            }
        }
        return null;
    }
}
