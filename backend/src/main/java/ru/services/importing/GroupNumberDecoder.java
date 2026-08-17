package ru.services.importing;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Расшифровка номера группы из чужой выгрузки: {@code «10593/13»} → факультет 10, набор …5,
 * кафедра «93», суффикс «13».
 *
 * <p><b>Чистая функция без Spring и БД</b> — по образцу {@link ru.services.orgunit.OrgUnitSubtree},
 * {@code EducatorTitles}, {@code LessonOrderRule}: разбор покрывается быстрыми тестами и не зависит
 * ни от файлов заказчика, ни от того, что лежит в базе. Сопоставление расшифрованного с нашими
 * сущностями — уже не её дело.</p>
 *
 * <p><b>Таблица форм, а не цепочка {@code if}</b> (решение И-5). Форм номера уже три, и третья
 * появилась по доменной причине: у 11 факультета короткие курсы повышения квалификации, года набора
 * в номере нет вовсе. Значит появится и четвёртая — новый факультет добавляется <b>строкой в
 * {@link #FORMS}</b> и не трогает разбор остальных.</p>
 *
 * <p><b>Суффикс отрезается ДО измерения длины базы.</b> Иначе {@code 103/12} (пять цифр всего)
 * притворится пятизначной формой десятого факультета. Это не гипотетический случай, а живой номер
 * из выгрузки.</p>
 *
 * <p><b>Нумерация — соглашение, а не инвариант.</b> Функция обязана уметь сказать «номер не по
 * стандарту» и назвать причину, а не угадать: такие группы показываются списком и разбираются
 * глазами. Поэтому неудача — это {@link GroupNumber} с заполненным {@code problem}, а не
 * исключение; отчёт сверки строится по одному проходу без {@code try/catch}.</p>
 *
 * <p><b>Суффикс не разбирается на специальность и подгруппу</b>, хотя формат известен («855/11» —
 * специальность 1, подгруппа 1). Потребителя у этого разбора нет: для сопоставления групп суффикс —
 * часть имени ({@code 955/2} и {@code 955/11} — разные строки в {@code groups}), а отрезается он
 * только ради факультета, года и кафедры. Моделировать то, чего никто не спрашивает, значит завести
 * второе представление имени группы.</p>
 *
 * @see <a href="file:../../../../../docs/IMPORT_FORMAT.md">IMPORT_FORMAT.md §4</a>
 */
public final class GroupNumberDecoder {

    private GroupNumberDecoder() {
    }

    /**
     * Форма номера: как нарезать базу на факультет, цифру набора и кафедру.
     *
     * @param baseLength                  длина базы (номер без суффикса)
     * @param facultyDigits               сколько первых цифр — код факультета
     * @param carriesEnrollmentDigit      есть ли в номере цифра года набора
     * @param carriesDepartment           кодирует ли остаток номера кафедру; {@code false} — остаток
     *                                    ни о чём известном не говорит, и выдавать его за краткое имя
     *                                    кафедры нельзя: по нему завелась бы кафедра-призрак
     * @param departmentPrefixedByFaculty склеивать ли код факультета с цифрой кафедры («9» + «1»)
     * @param shortCourse                 короткие курсы: обучение длится от двух недель до трёх
     *                                    месяцев, поэтому года набора в номере нет, а семестр у
     *                                    таких групп всегда первый (правило заказчика)
     * @param requiredFacultyCode         код факультета, которому принадлежит форма;
     *                                    {@code null} — форма любого факультета такой длины
     */
    private record NumberForm(
            int baseLength,
            int facultyDigits,
            boolean carriesEnrollmentDigit,
            boolean carriesDepartment,
            boolean departmentPrefixedByFaculty,
            boolean shortCourse,
            String requiredFacultyCode
    ) {
    }

    /**
     * Известные формы номера. Порядок значения не имеет: форма выбирается по длине базы, а среди
     * форм одной длины — по коду факультета (строка с {@code null} служит запасной).
     *
     * <ul>
     *   <li>{@code 951} — ф. 9, набор …5, каф. 1 → кафедра называется «91» (склейка);</li>
     *   <li>{@code 1152} — ф. 11, каф. 52, года набора нет;</li>
     *   <li>{@code 10593} — ф. 10, набор …5, каф. 93 (двузначная, без склейки);</li>
     *   <li>{@code 11434} — ф. 11, пятизначная форма: ни года набора, ни кафедры (2026-08-17).</li>
     * </ul>
     *
     * <p>⚠️ Порядок форм одной длины важен ровно в одном: форма с указанным факультетом побеждает
     * запасную. Пятизначных форм теперь две — десятого факультета и одиннадцатого, — и обе названы
     * по коду, поэтому «11434» больше не читается ни десятым факультетом, ни «номером не по
     * стандарту».</p>
     */
    private static final List<NumberForm> FORMS = List.of(
            // факультеты 1–9
            new NumberForm(3, 1, true, true, true, false, null),
            // 11 факультет: короткие курсы, года набора нет
            new NumberForm(4, 2, false, true, false, true, "11"),
            // 10 факультет: кафедра двузначная
            new NumberForm(5, 2, true, true, false, false, "10"),
            // 11 факультет пятизначный: что кодирует остаток («434»), неизвестно — кафедрой его не
            // объявляем. Пустая принадлежность честнее выдуманной: её проставит человек (И-25).
            new NumberForm(5, 2, false, false, false, true, "11")
    );

    /** Окно обучения: длиннее его номер набора цифрой не закодируешь однозначно. */
    private static final int MAX_STUDY_YEARS = 6;

    /**
     * Разделитель суффикса: «101/1» и «101-1» — <b>одна и та же группа</b>.
     *
     * <p>Косая черта — настоящее написание номера, дефис появляется там, где номер попадает в
     * <b>имя файла</b>: «/» в именах файлов запрещён, и чужая программа подменяет его дефисом.
     * Разбор обязан узнавать оба, иначе один и тот же поток приезжает дважды, а заведение создаёт
     * <b>две группы на одну</b> — дубль, который разрезает расписание надвое тише и вреднее, чем
     * кривые занятия.</p>
     */
    private static final Pattern SUFFIX_SEPARATOR = Pattern.compile("[/\\-]");

    /**
     * Каким знаком писать суффикс в наших именах.
     *
     * <p>Выбор человека, а не догадка разбора: в выгрузке встречаются оба написания, и какое из них
     * считать своим — решение о справочнике, а не о файле. Умолчание — косая черта: это номер как
     * он есть, дефис же вынужден ограничением файловой системы.</p>
     */
    public enum SuffixStyle {
        SLASH('/'),
        DASH('-');

        private final char separator;

        SuffixStyle(char separator) {
            this.separator = separator;
        }

        public char separator() {
            return separator;
        }
    }

    /**
     * Ключ сопоставления номеров: «101/1», «101-1» и « 101/1 » дают одно значение.
     *
     * <p>Приводить к ключу нужно <b>обе</b> стороны — и файл, и базу: у нас группа может быть
     * заведена в любом написании (в том числе прошлым прогоном импорта), и сравнение «как есть»
     * молча завело бы вторую.</p>
     */
    public static String key(String raw) {
        return raw == null ? "" : SUFFIX_SEPARATOR.matcher(raw.trim().toLowerCase()).replaceAll("/");
    }

    /**
     * Номер в выбранном написании: «101-1» → «101/1» при {@link SuffixStyle#SLASH}.
     *
     * <p>Трогает только разделитель. Всё остальное остаётся как в файле — приводить номер к
     * «правильному» виду целиком разбор не вправе: нумерация это соглашение, а не инвариант.</p>
     */
    public static String render(String raw, SuffixStyle style) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        SuffixStyle chosen = style == null ? SuffixStyle.SLASH : style;
        return SUFFIX_SEPARATOR.matcher(value).replaceAll(String.valueOf(chosen.separator()));
    }

    /**
     * Расшифрованный номер группы. При неудаче заполнен {@code problem}, а остальные поля — те,
     * что успели разобраться (обычно {@code base}/{@code suffix}).
     *
     * @param raw                 номер как в файле: «10593/13»
     * @param base                номер без суффикса: «10593»
     * @param suffix              часть после «/» либо {@code null}; часть имени группы
     * @param facultyCode         код факультета: «9», «10», «11»
     * @param departmentShortName краткое имя кафедры: «91», «93», «52» — ложится на
     *                            {@code org_unit.short_name}; {@code null} — форма номера кафедру
     *                            не кодирует, и принадлежность проставляется вручную (И-25)
     * @param enrollmentDigit     цифра года набора либо {@code null} (11 факультет)
     * @param shortCourse         короткий курс (11 факультет): года набора нет, семестр всегда первый
     * @param problem             причина, по которой номер не разобран; {@code null} — разобран
     */
    public record GroupNumber(
            String raw,
            String base,
            String suffix,
            String facultyCode,
            String departmentShortName,
            Integer enrollmentDigit,
            boolean shortCourse,
            String problem
    ) {
        public boolean recognized() {
            return problem == null;
        }

        static GroupNumber notStandard(String raw, String base, String suffix, String problem) {
            return new GroupNumber(raw, base, suffix, null, null, null, false, problem);
        }
    }

    /**
     * Короткий ли это курс — по одному номеру группы, без базы.
     *
     * <p>Нужно расчёту плана: у таких групп года набора в номере нет вовсе, а семестр всё равно
     * известен — первый (обучение длится от двух недель до трёх месяцев, правило заказчика
     * 2026-08-17). Без этого занятия 11 факультета уходили в блокеры «год набора неизвестен —
     * семестр курса не вычислить».</p>
     *
     * @param name номер группы как он записан у нас или в файле
     */
    public static boolean isShortCourse(String name) {
        return decode(name).shortCourse();
    }

    /**
     * Разбирает номер группы. Функция <b>тотальна</b>: {@code null}, пустая строка и любой мусор
     * дают {@link GroupNumber} с {@code problem}, а не исключение.
     *
     * @param raw номер как в файле («951», «103/12», «1152»); пробелы по краям снимаются
     * @return расшифровка либо описание, почему номер не по стандарту
     */
    public static GroupNumber decode(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return GroupNumber.notStandard(raw, null, null, "пустой номер группы");
        }

        // Суффикс — первым делом: он не участвует в измерении длины базы. Разделителем считается и
        // дефис: в именах файлов «/» запрещён и подменён им, а группа при этом та же самая.
        String[] parts = SUFFIX_SEPARATOR.split(value, -1);
        if (parts.length > 2) {
            return GroupNumber.notStandard(value, null, null, "больше одного разделителя суффикса в номере");
        }
        String base = parts[0];
        String suffix = parts.length == 2 ? parts[1] : null;

        if (suffix != null && suffix.isEmpty()) {
            return GroupNumber.notStandard(value, base, null, "разделитель без суффикса");
        }
        if (!isDigits(base) || (suffix != null && !isDigits(suffix))) {
            return GroupNumber.notStandard(value, base, suffix, "номер содержит не только цифры");
        }

        NumberForm form = formFor(base);
        if (form == null) {
            return GroupNumber.notStandard(value, base, suffix, whyNoForm(base));
        }

        String facultyCode = base.substring(0, form.facultyDigits());
        if (facultyCode.startsWith("0")) {
            return GroupNumber.notStandard(value, base, suffix, "код факультета начинается с нуля");
        }

        int departmentStart = form.facultyDigits() + (form.carriesEnrollmentDigit() ? 1 : 0);
        Integer enrollmentDigit = form.carriesEnrollmentDigit()
                ? base.charAt(form.facultyDigits()) - '0'
                : null;
        String departmentShortName = null;
        if (form.carriesDepartment()) {
            String department = base.substring(departmentStart);
            departmentShortName = form.departmentPrefixedByFaculty() ? facultyCode + department : department;
        }

        return new GroupNumber(value, base, suffix, facultyCode, departmentShortName, enrollmentDigit,
                form.shortCourse(), null);
    }

    /**
     * Год набора по цифре из номера и году начала периода импорта.
     *
     * <p>Отдельным вызовом, а не полем {@link GroupNumber}: цифра лежит в номере, а год выводится
     * из <b>контекста периода</b>, которого разбор строки не знает и знать не должен.</p>
     *
     * <p>Однозначность держится на том, что окно обучения ({@value #MAX_STUDY_YEARS} лет) короче
     * десяти: внутри «год периода минус срок обучения» цифра встречается ровно один раз. Для
     * 2026/27 цифра 5 — это 2025, а не 2015.</p>
     *
     * @param enrollmentDigit цифра года набора из номера; {@code null} допустим (11 факультет)
     * @param periodStartYear год начала периода импорта: для 2026/27 — 2026
     * @return год набора либо {@code null}, если цифра в окно не попадает (номер странный —
     * такую группу показывают списком, а не чинят догадкой)
     */
    public static Integer enrollmentYear(Integer enrollmentDigit, int periodStartYear) {
        if (enrollmentDigit == null || enrollmentDigit < 0 || enrollmentDigit > 9) {
            return null;
        }
        for (int year = periodStartYear; year > periodStartYear - MAX_STUDY_YEARS; year--) {
            if (year % 10 == enrollmentDigit) {
                return year;
            }
        }
        return null;
    }

    /**
     * Форма по базе: сначала точное совпадение по коду факультета, иначе запасная строка для этой
     * длины. Такой порядок и делает добавление факультета одной строкой: у нового факультета с
     * четырёхзначным номером своя форма, а прежние остаются нетронутыми.
     */
    private static NumberForm formFor(String base) {
        NumberForm fallback = null;
        for (NumberForm form : FORMS) {
            if (form.baseLength() != base.length()) {
                continue;
            }
            if (form.requiredFacultyCode() == null) {
                fallback = form;
            } else if (base.startsWith(form.requiredFacultyCode())) {
                return form;
            }
        }
        return fallback;
    }

    /**
     * Почему форма не подобралась. Два случая различаются намеренно: «таких номеров у нас не
     * бывает вовсе» и «длина знакомая, а факультет чужой» — в отчёте сверки это разные новости,
     * и вторая обычно означает новый факультет, то есть новую строку в {@link #FORMS}.
     */
    private static String whyNoForm(String base) {
        List<String> knownFaculties = FORMS.stream()
                .filter(form -> form.baseLength() == base.length())
                .map(NumberForm::requiredFacultyCode)
                .filter(code -> code != null)
                .toList();

        if (knownFaculties.isEmpty()) {
            return "длина базы " + base.length() + " не соответствует ни одной известной форме";
        }
        return "номер длины " + base.length() + " не принадлежит ни одному известному факультету ("
                + String.join(", ", knownFaculties) + ")";
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
