package ru.services.importing;

import ru.enums.OrgUnitType;
import ru.services.importing.GroupNumberDecoder.GroupNumber;
import ru.services.importing.ParsedSheet.CutKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Оргструктура, как она видна в шапках файлов: кто факультет, кто кафедра и кто чей.
 *
 * <h2>Почему заводить можно автоматически</h2>
 * <p>По строке «91» не видно, кафедра это или факультет, а не тот родитель тихо выкидывает кафедру
 * из охвата института (И-4). Но <b>роль написана не в строке, а в канале</b>: «Факультет 9Ф» это
 * факультет, «Кафедра: 91 кафедра» это кафедра, цифры кафедры из номера группы — тоже кафедра (§4
 * спецификации). Сверка сводит все четыре канала в одно множество имён и роль теряет — поэтому роль
 * читается здесь, а не там.</p>
 *
 * <p><b>Родитель тоже не догадка, а факт файла:</b> факультет и кафедра стоят в шапке <b>одного</b>
 * файла, то есть файл прямо говорит «эта кафедра на этом факультете». Догадкой было бы связывать их
 * по совпадению цифр или по алфавиту — этого здесь нет.</p>
 *
 * <p><b>«ОАК» — не подразделение, а метка отношения</b> (И-4): кафедра подчинена институту напрямую.
 * Такая кафедра получает {@code underInstitute}, а узел «ОАК» не заводится никогда — иначе в дереве
 * появился бы факультет-призрак, в который сложились бы все внефакультетские кафедры.</p>
 *
 * <p><b>Чистая функция</b>: ни Spring, ни базы. Что из этого уже заведено и куда именно вешать —
 * дело слоя записи.</p>
 */
public final class OrgStructureReader {

    private OrgStructureReader() {
    }

    /** Метка «кафедра подчинена институту напрямую», приезжает вместо имени факультета. */
    private static final String OAK = "ОАК";

    /**
     * Подразделение, каким его увидели файлы.
     *
     * @param key             ключ сравнения («91 кафедра» и «91» — одно и то же)
     * @param name            имя, как в файле (самое полное из встреченных написаний)
     * @param shortName       краткое имя — по нему кафедру находит номер группы
     * @param type            вид: факультет или кафедра
     * @param parentName      имя факультета-родителя либо {@code null}
     * @param underInstitute  родитель — институт напрямую (факультет либо кафедра с «ОАК»)
     * @param problem         почему заводить нельзя; {@code null} — можно
     * @param parentCandidates все факультеты, которые файлы называют родителем этого узла. Обычно
     *                        один; несколько — это и есть спор, и <b>именно из них человек
     *                        выбирает</b>. Отдаются наружу потому, что нужного факультета может ещё
     *                        не быть в базе — он заводится этим же прогоном, и выбрать его из
     *                        справочника нельзя
     * @param roleDisputed    файлы называют узел и факультетом, и кафедрой. <b>Отдельно от
     *                        {@link #problem}</b>, потому что это единственный спор, который
     *                        человек не может снять выбором родителя: сначала надо понять, что это
     *                        за узел вообще. Спор о родителе — снимается (см.
     *                        {@code ImportCreationService})
     * @param files           файлы, где подразделение встретилось
     */
    public record OrgUnitDraft(
            String key,
            String name,
            String shortName,
            OrgUnitType type,
            String parentName,
            List<String> parentCandidates,
            boolean underInstitute,
            String problem,
            boolean roleDisputed,
            List<String> files
    ) {
    }

    /**
     * Читает оргструктуру из шапок.
     *
     * @return факультеты первыми, кафедры следом — в этом же порядке их и заводить
     */
    public static List<OrgUnitDraft> read(List<ParsedSheet> sheets) {
        Map<String, Accumulator> units = new LinkedHashMap<>();

        for (ParsedSheet sheet : sheets) {
            String faculty = trimmed(sheet.header().faculty());
            boolean underInstitute = faculty != null && OAK.equalsIgnoreCase(faculty);
            String file = sheet.sourceName();

            if (faculty != null && !underInstitute) {
                accumulate(units, faculty, OrgUnitType.FACULTY, null, true, file);
            }
            for (String department : departments(sheet)) {
                accumulate(units, department, OrgUnitType.DEPARTMENT,
                        underInstitute ? null : faculty, underInstitute, file);
            }
        }

        return units.values().stream()
                .map(Accumulator::build)
                // Факультет заводится раньше своей кафедры — иначе её некуда вешать.
                .sorted(Comparator.comparingInt(draft -> draft.type().getNestingRank()))
                .toList();
    }

    /**
     * Кафедры этого файла: из шапки и из номера группы.
     *
     * <p>Два канала об одном, и оба говорят «кафедра»: «Кафедра: 91 кафедра» в шапке
     * преподавательского файла и цифры «91» в номере группы. Пишут они по-разному, поэтому
     * схлопываются ключом — иначе одна кафедра завелась бы двумя узлами.</p>
     */
    private static List<String> departments(ParsedSheet sheet) {
        List<String> found = new ArrayList<>();
        String fromHeader = trimmed(sheet.header().department());
        if (fromHeader != null) {
            found.add(fromHeader);
        }
        if (sheet.header().kind() == CutKind.GROUP) {
            for (String group : CellDialect.groups(sheet.header().owner())) {
                GroupNumber number = GroupNumberDecoder.decode(group);
                // Разобранный номер ещё не значит «кафедра известна»: у коротких курсов 11 факультета
                // («11434») форма её не кодирует вовсе, и это законное состояние, а не сбой разбора.
                // Такой группе принадлежность проставляет человек в сверке (И-25).
                if (number.recognized() && number.departmentShortName() != null) {
                    found.add(number.departmentShortName());
                }
            }
        }
        return found;
    }

    /**
     * Копит один узел.
     *
     * <p><b>Безымянный узел не заводится вовсе.</b> Единственная дверь сюда — значит и проверка
     * одна: пустое имя схлопнулось бы в пустой ключ, собрало бы под собой все каналы, которым нечего
     * сказать, и завелось бы подразделением без названия. Случай не гипотетический: у коротких курсов
     * 11 факультета номер кафедру не кодирует, и оттуда приходит {@code null}.</p>
     */
    private static void accumulate(Map<String, Accumulator> units, String name, OrgUnitType type,
                                   String parentName, boolean underInstitute, String file) {
        if (trimmed(name) == null) {
            return;
        }
        Accumulator unit = units.computeIfAbsent(ImportMatchingService.orgUnitKey(name),
                key -> new Accumulator(key, type));
        unit.names.add(name);
        unit.files.add(file);
        unit.types.add(type);
        unit.underInstitute |= underInstitute;
        if (parentName != null) {
            unit.parents.add(parentName);
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Накопитель: одно подразделение приезжает из десятков файлов и разными написаниями. */
    private static final class Accumulator {
        private final String key;
        private final OrgUnitType firstType;
        private final Set<String> names = new LinkedHashSet<>();
        private final Set<String> parents = new LinkedHashSet<>();
        private final Set<OrgUnitType> types = new LinkedHashSet<>();
        private final Set<String> files = new LinkedHashSet<>();
        private boolean underInstitute;

        private Accumulator(String key, OrgUnitType firstType) {
            this.key = key;
            this.firstType = firstType;
        }

        private OrgUnitDraft build() {
            // Самое полное написание — в имя («91 кафедра»), ключ — в краткое («91»): по краткому
            // кафедру находит номер группы, и именно так её ищет сверка.
            String name = names.stream().max(Comparator.comparingInt(String::length)).orElse(key);
            String problem = null;
            boolean roleDisputed = types.size() > 1;
            if (roleDisputed) {
                // Один и тот же текст пришёл и как факультет, и как кафедра — это находка о файлах,
                // а не повод выбрать вид наугад: не тот вид ломает всю политику вложенности.
                problem = "в разных файлах названо и факультетом, и кафедрой — разобрать вручную";
            } else if (parents.size() > 1) {
                problem = "разные факультеты в разных файлах: " + String.join(", ", parents)
                        + " — выбрать родителя должен человек";
            }
            return new OrgUnitDraft(key, name, key, firstType,
                    parents.isEmpty() ? null : parents.iterator().next(),
                    List.copyOf(parents),
                    underInstitute, problem, roleDisputed, List.copyOf(files));
        }
    }
}
