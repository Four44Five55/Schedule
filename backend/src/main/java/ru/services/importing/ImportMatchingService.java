package ru.services.importing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Auditorium;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Discipline;
import ru.entity.OrgUnit;
import ru.repository.AuditoriumRepository;
import ru.repository.DisciplineRepository;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.OrgUnitRepository;
import ru.repository.SpecialRankRepository;
import ru.repository.StudyStreamRepository;
import ru.services.educator.EducatorCredentialsAssembler;
import ru.services.educator.EducatorTitles;
import ru.services.importing.EducatorNameDecoder.EducatorName;
import ru.services.importing.GroupNumberDecoder.GroupNumber;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ImportMatchReport.MatchRow;
import ru.services.importing.ImportMatchReport.MatchSection;
import ru.services.importing.OrgStructureReader.OrgUnitDraft;
import ru.services.importing.RoomNumberDecoder.RoomNumber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * Сверка разобранных файлов с нашими справочниками — <b>без единой записи</b> (решение И-10).
 *
 * <p>Отвечает на вопрос «чего у нас нет» до того, как импорт что-то создаст. Порядок разделов — от
 * самого слабого места к самому надёжному: преподаватели (стабильного ключа нет вовсе, §8
 * спецификации), группы, дисциплины, аудитории.</p>
 *
 * <h2>Общее правило всех четырёх разделов: не угадывать</h2>
 * <p>Ни одна неоднозначность не разрешается «взять первого». Несколько кандидатов — это строка
 * отчёта с перечислением, а не молчаливый выбор: ошибка выбора здесь не видна вообще никак
 * (расписание встанет и будет выглядеть правдоподобно), а цена — разрезанное надвое расписание
 * человека или занятия в физически другом здании.</p>
 *
 * <p>Совпадения тоже проверяются: если преподаватель нашёлся, но звание или степень в файле другие,
 * это строка «уточнить», а не повод отказаться и не повод молча перезаписать. Звание меняется во
 * времени — расхождение нормально и должно быть видно человеку.</p>
 *
 * <h2>Каждая строка называет свои файлы</h2>
 * <p>Отчёт сводит значения из полутора тысяч файлов, и без указания источника проверить находку
 * нечем: «в базе нет: 10073-19» не подсказывает, где эту запись искать глазами. Поэтому вместе со
 * значением собираются и <b>файлы, где оно встретилось</b> ({@link Origins}) — по тому же правилу,
 * по которому {@code ImportFolderService} запоминает файл-образец для каждого замечания разбора.
 * Показывается несколько первых имён и общее число: сотня имён в ячейке таблицы прячет всё
 * остальное ровно так же, как их отсутствие.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportMatchingService {

    private final EducatorRepository educatorRepository;
    private final GroupRepository groupRepository;
    private final DisciplineRepository disciplineRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final SpecialRankRepository specialRankRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final StudyStreamRepository streamRepository;

    /**
     * Сверяет пачку разобранных файлов со справочниками.
     *
     * @param sheets          разобранные файлы (любых разрезов)
     * @param locationId      локация, в которой искать аудитории; {@code null} — искать во всех и
     *                        сообщать о неоднозначности (И-17: локации в файле нет вовсе)
     * @param groupNameStyle  каким знаком писать суффикс группы («101/1» или «101-1»);
     *                        {@code null} — косая черта. На <b>сопоставление</b> не влияет: оба
     *                        написания это одна группа. Влияет только на то, как мы её назовём
     */
    @Transactional(readOnly = true)
    public ImportMatchReport match(List<ParsedSheet> sheets, Integer locationId, SuffixStyle groupNameStyle) {
        SuffixStyle style = groupNameStyle == null ? SuffixStyle.SLASH : groupNameStyle;
        List<ImportMatchReport.MatchSection> sections = List.of(
                orgUnits(sheets),
                educators(sheets),
                groups(sheets, style),
                streams(sheets, style),
                disciplines(sheets),
                rooms(sheets, locationId)
        );
        sections.forEach(section ->
                log.info("Сверка «{}»: сопоставлено {} из {}", section.title(), section.matched(), section.total()));
        return new ImportMatchReport(sections);
    }

    // =======================================================================
    // Подразделения — раньше всех: на них ссылаются и группа, и преподаватель
    // =======================================================================

    /**
     * Кафедры и факультеты из файлов против дерева {@code org_unit}.
     *
     * <p>Раздел стоит первым, потому что он <b>предусловие остальных</b>: группе и преподавателю
     * подразделение нужно при заведении, и если его нет, заводить их некуда.</p>
     *
     * <p>Каналов о подразделении в выгрузке четыре — шапка группы, шапка преподавателя, подвал и
     * цифры в номере группы (§5 спецификации). Здесь они сводятся в одно множество: расхождение
     * каналов это находка, а не повод выбрать «главный».</p>
     */
    private MatchSection orgUnits(List<ParsedSheet> sheets) {
        List<OrgUnit> all = orgUnitRepository.findAll();

        // «51» из номера группы и «51 кафедра» из шапки — одно подразделение, названное по-разному
        // в разных каналах. Схлопываем до сверки: иначе одна кафедра приедет двумя строками, а
        // заведение (если его сюда когда-нибудь пустят) сделало бы из неё два узла дерева.
        Origins origins = collect(sheets, ImportMatchingService::orgUnitNames);
        Map<String, List<String>> variants = new LinkedHashMap<>();
        for (String raw : origins.values()) {
            variants.computeIfAbsent(orgUnitKey(raw), key -> new ArrayList<>()).add(raw);
        }

        // Роль и родителя сверка сама не выводит — она сводит четыре канала в одно множество имён.
        // Их знает чтение оргструктуры: роль написана в КАНАЛЕ, а родитель — в шапке того же файла.
        // Берём оттуда, чтобы отчёт показывал, во что узел войдёт, ЕЩЁ ДО заведения (И-4: не тот
        // родитель тихо выкидывает кафедру из охвата института, и увидеть это надо заранее).
        Map<String, OrgUnitDraft> drafts = OrgStructureReader.read(sheets).stream()
                .collect(Collectors.toMap(OrgUnitDraft::key, Function.identity(), (first, second) -> first));

        Map<String, List<String>> files = new LinkedHashMap<>();
        List<MatchRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<String>> unit : variants.entrySet()) {
            String raw = unit.getValue().get(0);
            files.put(raw, origins.filesOf(unit.getValue()));
            String seenAs = unit.getValue().size() == 1 ? null
                    : "встречено как: " + String.join(", ", new LinkedHashSet<>(unit.getValue()));

            if (OAK.equalsIgnoreCase(raw.trim())) {
                // И-4: «ОАК» — метка отношения «кафедра подчинена институту напрямую», а не
                // подразделение. Узел с таким именем стал бы факультетом-призраком, в который
                // сложились бы все внефакультетские кафедры.
                rows.add(MatchRow.unreadable(raw, "метка «без факультета», а не подразделение",
                        "кафедра подчиняется институту напрямую — узел заводить не нужно (И-4)"));
                continue;
            }

            // Приводим к ключу ОБЕ стороны: у нас краткое имя может быть записано и «51», и
            // «51 кафедра» — ровно так же, как в файлах.
            String wanted = unit.getKey();
            List<OrgUnit> candidates = all.stream()
                    .filter(ours -> wanted.equals(orgUnitKey(ours.getShortName()))
                            || wanted.equals(orgUnitKey(ours.getName())))
                    .toList();

            OrgUnitDraft draft = drafts.get(wanted);
            if (candidates.size() == 1) {
                OrgUnit found = candidates.get(0);
                // У заведённого показываем, где он числится У НАС: столбец отвечает «во что входит»,
                // а не «что об этом думает файл». Расхождение с файлом — отдельная строка ниже.
                rows.add(MatchRow.matched(raw, found.getType().getFullName(),
                                found.getId(), found.getName(), joinNotes(seenAs, parentDiffers(draft, found)))
                        .withOrgUnit(found.getParent() == null ? null : found.getParent().getName()));
            } else if (candidates.isEmpty()) {
                // Спор каналов виден ЗДЕСЬ, до заведения: раньше о нём узнавали строкой «пропущено»
                // уже после нажатия «Завести», и ответить на вопрос было негде. Статус
                // «неоднозначно» — ровно про это: кандидатов несколько, выбирает человек.
                MatchRow row = draft != null && draft.problem() != null
                        ? MatchRow.ambiguous(raw, joinNotes(role(draft), seenAs), draft.problem())
                        : MatchRow.missing(raw, joinNotes(role(draft), seenAs), "в базе нет");
                // Кандидаты из файлов — вместе со строкой: нужного факультета может ещё не быть в
                // базе (он заводится этим же прогоном), и выбрать его из справочника нельзя.
                rows.add(row.withOrgUnit(parentOf(draft))
                        .withOrgUnitOptions(draft == null ? List.of() : draft.parentCandidates()));
            } else {
                rows.add(MatchRow.ambiguous(raw, seenAs, "в базе несколько: " + candidates.stream()
                        .map(OrgUnit::getName).collect(Collectors.joining(", "))));
            }
        }
        return section(ImportMatchReport.ORG_UNITS, "по краткому имени, затем по полному", rows, files);
    }

    /**
     * Во что войдёт новое подразделение: факультет из шапки того же файла либо институт напрямую.
     *
     * <p>{@code null} — родителя нет: файл факультета не назвал, и заведение такую кафедру
     * пропустит («куда вешать, неизвестно»). Это ровно та строка, из-за которой стоило заводить
     * столбец: раньше о ней узнавали только после нажатия «Завести».</p>
     */
    private static String parentOf(OrgUnitDraft draft) {
        if (draft == null) {
            return null;
        }
        return draft.underInstitute() ? "институт (напрямую, ОАК)" : draft.parentName();
    }

    /** Чем узел объявлен в файлах — роль читается из канала, а не из самой строки. */
    private static String role(OrgUnitDraft draft) {
        return draft == null ? null : draft.type().getFullName();
    }

    /**
     * Файл называет одного родителя, у нас записан другой — строка «уточнить», а не отказ.
     *
     * <p>Переподчинять подразделение импорт не станет: это master-данные, и решение о том, чья
     * кафедра, принимает человек. Но промолчать нельзя — не тот родитель тихо выкидывает кафедру
     * из охвата института (И-4).</p>
     */
    private static String parentDiffers(OrgUnitDraft draft, OrgUnit ours) {
        String fromFile = draft == null || draft.underInstitute() ? null : draft.parentName();
        if (fromFile == null) {
            return null;
        }
        if (ours.getParent() == null) {
            // Ровно тихий отказ из И-4: подразделение без родителя выпадает из охвата института,
            // и «расписание института» недосчитается людей. Файл здесь говорит, куда его вернуть.
            return "в файлах числится в «" + fromFile + "», а у нас родителя нет — "
                    + "подразделение выпадает из охвата института";
        }
        return orgUnitKey(fromFile).equals(orgUnitKey(ours.getParent().getName()))
                || orgUnitKey(fromFile).equals(orgUnitKey(ours.getParent().getShortName()))
                ? null
                : "в файлах числится в «" + fromFile + "», у нас — в «" + ours.getParent().getName() + "»";
    }

    /** Метка «кафедра подчинена институту напрямую», приезжает вместо имени факультета. */
    private static final String OAK = "ОАК";

    /** Слова-приставки к номеру подразделения: в разных каналах выгрузки их то пишут, то нет. */
    private static final List<String> UNIT_WORDS = List.of("кафедра", "каф.", "каф", "факультет", "ф-т", "отдел");

    /**
     * Ключ подразделения: «51 кафедра», «Каф. 51» и «51» — одно и то же.
     *
     * <p>Каналов о подразделении четыре (§5 спецификации), и пишут они по-разному: шапка даёт
     * «51 кафедра», номер группы — «51». Без приведения к ключу одна кафедра приезжает несколькими
     * строками отчёта, а это прямой путь к дублю в дереве подразделений.</p>
     */
    static String orgUnitKey(String raw) {
        String value = key(raw);
        for (String word : UNIT_WORDS) {
            value = value.replace(word, " ");
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    /** Четыре канала об одном: шапка (факультет и кафедра), подвал и цифры номера группы. */
    private static List<String> orgUnitNames(ParsedSheet sheet) {
        List<String> found = new ArrayList<>();
        found.add(sheet.header().faculty());
        found.add(sheet.header().department());
        sheet.footer().forEach(row -> found.add(row.department()));
        if (sheet.header().kind() == ParsedSheet.CutKind.GROUP) {
            // По каждой группе шапки: файл бывает выписан на объединённые группы, и кафедра
            // закодирована в номере у каждой из них (§4 спецификации).
            for (String group : CellDialect.groups(sheet.header().owner())) {
                GroupNumber number = GroupNumberDecoder.decode(group);
                if (number.recognized()) {
                    found.add(number.departmentShortName());
                }
            }
        }
        return found;
    }

    // =======================================================================
    // Преподаватели — самое слабое место: стабильного ключа нет
    // =======================================================================

    private MatchSection educators(List<ParsedSheet> sheets) {
        List<String> ranks = specialRankRepository.findAll().stream()
                .map(rank -> rank.getShortName())
                .filter(Objects::nonNull)
                .toList();

        // Ключ строится и для файла, и для базы — сравнение «как в файле» с «как в базе» и есть то
        // место, где сопоставление молча теряет людей.
        Map<String, List<Educator>> ours = educatorRepository.findAll().stream()
                .filter(educator -> EducatorNameDecoder.keyOfStoredName(educator.getName(), ranks) != null)
                .collect(Collectors.groupingBy(
                        educator -> EducatorNameDecoder.keyOfStoredName(educator.getName(), ranks),
                        Collectors.toList()));

        // Один человек приезжает разными подписями: «Иванов Т.В. дин» и «Иванов Т.В. дин доц» —
        // это один преподаватель, у которого в разных файлах разный хвост регалий. Схлопывать
        // ОБЯЗАТЕЛЬНО до заведения: иначе на каждый вариант завёлся бы отдельный человек, а дубль
        // преподавателя разрезает расписание надвое тише и вреднее, чем кривые занятия.
        // Кафедра из шапки собственного файла — то же правило, по которому её проставит заведение
        // (владелец правила один, OrgUnitHints: две копии разошлись бы, и отчёт начал бы врать).
        Map<String, String> departmentsByEducator = OrgUnitHints.byEducator(sheets, ranks);
        List<OrgUnit> units = orgUnitRepository.findAll();

        Origins origins = collect(sheets, ImportMatchingService::educatorSignatures);
        Map<String, List<String>> variantsByKey = new LinkedHashMap<>();
        Map<String, List<String>> files = new LinkedHashMap<>();
        List<MatchRow> rows = new ArrayList<>();

        for (String signature : origins.values()) {
            EducatorName parsed = EducatorNameDecoder.decode(signature, ranks);
            if (!parsed.recognized()) {
                rows.add(MatchRow.unreadable(signature, describe(parsed), parsed.problem()));
                files.put(signature, origins.filesOf(signature));
                continue;
            }
            variantsByKey.computeIfAbsent(parsed.key(), key -> new ArrayList<>()).add(signature);
        }

        for (Map.Entry<String, List<String>> person : variantsByKey.entrySet()) {
            // Представителем берём самую полную подпись: в ней есть звание, и заведение получит его,
            // а не потеряет из-за того, что первым встретился обрезанный вариант.
            String signature = person.getValue().stream()
                    .max(Comparator.comparingInt(String::length))
                    .orElseThrow();
            EducatorName parsed = EducatorNameDecoder.decode(signature, ranks);
            String detail = describeVariants(person.getValue(), ranks);
            files.put(signature, origins.filesOf(person.getValue()));

            List<Educator> candidates = ours.getOrDefault(person.getKey(), List.of());
            if (candidates.isEmpty()) {
                // Куда попадёт при заведении — видно ДО записи (И-10). Где не вывелось, там в
                // интерфейсе встаёт выбор кафедры руками: угадывать за человека нечем.
                rows.add(MatchRow.missing(signature, detail, "в базе нет")
                        .withOrgUnit(unitName(departmentsByEducator.get(person.getKey()), units)));
            } else if (candidates.size() > 1) {
                // Однофамильцы с теми же инициалами. Разводятся подразделением, но выбирать за
                // человека нельзя: подпись в файле кафедру не несёт.
                rows.add(MatchRow.ambiguous(signature, detail,
                        "в базе несколько с таким же ФИО: " + candidates.stream()
                                .map(Educator::getName).collect(Collectors.joining(", "))));
            } else {
                Educator found = candidates.get(0);
                rows.add(MatchRow.matched(signature, detail, found.getId(), found.getName(),
                        credentialsDifference(parsed, found)));
            }
        }
        return section(ImportMatchReport.EDUCATORS, "по фамилии и инициалам; звание и степень в ключ не входят",
                rows, files);
    }

    /** Подписи из подвала (лекторы и практики) плюс владелец преподавательского файла. */
    private static List<String> educatorSignatures(ParsedSheet sheet) {
        List<String> found = new ArrayList<>();
        sheet.footer().forEach(row -> found.addAll(row.allEducators()));
        if (sheet.header().kind() == ParsedSheet.CutKind.EDUCATOR && sheet.header().owner() != null) {
            found.add(sheet.header().owner());
        }
        return found;
    }

    private static String describe(EducatorName parsed) {
        List<String> parts = new ArrayList<>();
        if (parsed.rank() != null) parts.add(parsed.rank());
        if (parsed.credentials() != null) parts.add(parsed.credentials());
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /**
     * Что о человеке говорят все его подписи разом.
     *
     * <p><b>Разная полнота — не расхождение</b> (правило заказчика 2026-08-15): «ктн» в одном файле
     * и «ктн доц» в другом это один и тот же человек, просто в одной выгрузке хвост подписи короче.
     * Берём <b>наибольшее</b> и молчим — спрашивать тут не о чем, а лишняя строка «уточнить» прячет
     * настоящие находки.</p>
     *
     * <p><b>Но «наибольшее» определено не всегда.</b> «ктн» и «дтн» — не разная полнота, а
     * противоречие (кандидат против доктора), и длина тут ничего не решает. Такое по-прежнему
     * показывается человеку: молчаливый выбор одного из двух был бы выдумыванием факта.</p>
     */
    private static String describeVariants(List<String> signatures, List<String> ranks) {
        Set<String> descriptions = signatures.stream()
                .map(signature -> describe(EducatorNameDecoder.decode(signature, ranks)))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (descriptions.isEmpty()) {
            return null;
        }
        if (descriptions.size() == 1) {
            return descriptions.iterator().next();
        }

        String fullest = descriptions.stream().max(Comparator.comparingInt(String::length)).orElseThrow();
        boolean nested = descriptions.stream().allMatch(fullest::contains);
        return nested ? fullest : "в файлах по-разному: " + String.join(" / ", descriptions);
    }

    /**
     * Расхождение регалий при совпавшем ФИО — строка «уточнить», а не отказ.
     *
     * <p>Сравнение стало возможным только после того, как сокращения степени свели к слитной форме
     * без точек («ктн»): до этого наша подпись и подпись из файла не совпадали никогда, и сверка
     * давала бы шум вместо находок.</p>
     */
    private static String credentialsDifference(EducatorName fromFile, Educator ours) {
        EducatorTitles.Credentials credentials = EducatorCredentialsAssembler.from(ours);
        List<String> notes = new ArrayList<>();

        String ourRank = credentials.rankShort();
        if (!sameToken(fromFile.rank(), ourRank)) {
            notes.add("звание: в файле «" + orDash(fromFile.rank()) + "», у нас «" + orDash(ourRank) + "»");
        }

        String ourTail = tailOf(credentials);
        if (!sameToken(fromFile.credentials(), ourTail) && !covers(ourTail, fromFile.credentials())) {
            // Наша запись полнее файла — уточнять нечего (то же правило «наибольшего»). А вот
            // обратное стоит показать: файл знает про человека больше, чем наш справочник.
            notes.add("регалии: в файле «" + orDash(fromFile.credentials()) + "», у нас «" + orDash(ourTail) + "»");
        }
        return notes.isEmpty() ? null : "уточнить — " + String.join("; ", notes);
    }

    /** Содержит ли одна подпись другую целиком: «ктн доц» покрывает «ктн». */
    private static boolean covers(String fuller, String shorter) {
        return shorter == null || shorter.isBlank()
                || (fuller != null && key(fuller).contains(key(shorter)));
    }

    /** Хвост подписи у нас: степень и учёное звание через пробел, как в файле. */
    private static String tailOf(EducatorTitles.Credentials credentials) {
        List<String> parts = new ArrayList<>();
        String degree = EducatorTitles.degreeAbbreviation(credentials);
        if (!degree.isEmpty()) parts.add(degree);
        if (credentials.titleShort() != null && !credentials.titleShort().isBlank()) {
            parts.add(credentials.titleShort().trim());
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    // =======================================================================
    // Группы
    // =======================================================================

    /**
     * Группы — <b>по одной</b>: объединённые номера сюда не попадают.
     *
     * <p>«10073/19, 10073/22» в ячейке — это не группа с таким именем, а <b>две</b> группы,
     * слушающие занятие вместе. Перечень разбирает {@link CellDialect}, поэтому здесь каждая строка
     * отчёта — одиночная группа, а факт их совместного занятия виден в разделе «Потоки». Пока
     * перечень не разбирался, такая запись доезжала до справочника целиком и заводила третью
     * группу — ту, которой не существует.</p>
     */
    private MatchSection groups(List<ParsedSheet> sheets, SuffixStyle style) {
        // Ключуем номером, а не строкой: «101/1» и «101-1» — одна группа, и у нас она может быть
        // заведена в любом из написаний (в том числе прошлым прогоном импорта).
        Map<String, Group> ours = byKey(groupRepository.findAll(),
                group -> GroupNumberDecoder.key(group.getName()));
        Integer periodYear = sheets.stream()
                .map(sheet -> sheet.header().startYear())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        List<OrgUnit> units = orgUnitRepository.findAll();

        Origins origins = collect(sheets, ImportMatchingService::groupNumbers);
        Map<String, List<String>> files = new LinkedHashMap<>();
        Map<String, List<String>> variantsByKey = new LinkedHashMap<>();
        List<MatchRow> rows = new ArrayList<>();

        for (String number : origins.values()) {
            if (!looksLikeGroupNumber(number)) {
                // Строка ячейки прочитана как группа, но группой быть не может. Не выбрасываем
                // молча: это находка о разметке файла, а не мусор — и заводить её нельзя.
                files.put(shorten(number), origins.filesOf(number));
                rows.add(MatchRow.unreadable(shorten(number), null,
                        "не похоже на номер группы — строка ячейки прочиталась как группа"));
                continue;
            }
            variantsByKey.computeIfAbsent(GroupNumberDecoder.key(number), key -> new ArrayList<>()).add(number);
        }

        for (Map.Entry<String, List<String>> group : variantsByKey.entrySet()) {
            List<String> variants = group.getValue();
            // Имя — в выбранном человеком написании, а не в том, которое первым встретилось в файлах.
            String name = GroupNumberDecoder.render(variants.get(0), style);
            files.put(name, origins.filesOf(variants));

            GroupNumber decoded = GroupNumberDecoder.decode(name);
            Group found = ours.get(group.getKey());

            List<String> detail = new ArrayList<>();
            detail.add(decoded.recognized() ? describeGroup(decoded, periodYear) : decoded.problem());
            if (variants.size() > 1) {
                // Расхождение написаний — находка, а не шум: по нему видно, какие файлы пишут номер
                // через дефис, и что мы свели их в одну группу, а не завели вторую.
                detail.add("встречено как: " + String.join(", ", new LinkedHashSet<>(variants)));
            }

            rows.add(found == null
                    ? MatchRow.missing(name, String.join(" · ", detail), "в базе нет")
                    // Кафедра группы закодирована в номере (§4). Номер не по стандарту — подсказки
                    // нет, и кафедру придётся проставить руками.
                    .withOrgUnit(unitName(OrgUnitHints.byGroupNumber(name), units))
                    : MatchRow.matched(name, String.join(" · ", detail), found.getId(), found.getName(),
                            joinNotes(spellingNote(name, found), enrollmentNote(decoded, periodYear, found))));
        }
        return section(ImportMatchReport.GROUPS,
                "по номеру как имени; «101/1» и «101-1» — одна группа, объединённые номера разнесены",
                rows, files);
    }

    /**
     * Наше имя отличается от выбранного написания — строка «уточнить», а не отказ.
     *
     * <p>Группа найдена, речь только о том, каким знаком записан суффикс. Переименовывать её сам
     * импорт не станет: имя группы — master-данные, на него смотрит человек в расписании и в
     * бланке, и молча править его по ходу разбора нельзя.</p>
     */
    private static String spellingNote(String name, Group ours) {
        return name.equals(ours.getName().trim()) ? null
                : "у нас записана как «" + ours.getName().trim() + "» — написание отличается от выбранного";
    }

    private static String joinNotes(String... notes) {
        String joined = Stream.of(notes).filter(Objects::nonNull).collect(Collectors.joining("; "));
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> groupNumbers(ParsedSheet sheet) {
        List<String> found = new ArrayList<>();
        if (sheet.header().kind() == ParsedSheet.CutKind.GROUP) {
            // Через тот же разбор перечня, что и ячейка: файл бывает выписан на объединённые группы
            // («Учебная группа 10073/19, 10073/22»), и без него шапка приносила бы в справочник
            // строку целиком — группу, которой не существует.
            found.addAll(CellDialect.groups(sheet.header().owner()));
        }
        CellDialect.readAll(sheet).forEach(lesson -> found.addAll(lesson.groups()));
        return found;
    }

    private static String describeGroup(GroupNumber decoded, Integer periodYear) {
        List<String> parts = new ArrayList<>();
        parts.add("ф. " + decoded.facultyCode());
        // Кафедра выводится не из всякой формы номера: у коротких курсов 11 факультета её там нет.
        // «каф. null» в отчёте выглядело бы разбором, который сломался, а он как раз сработал.
        if (decoded.departmentShortName() != null) {
            parts.add("каф. " + decoded.departmentShortName());
        }
        Integer year = enrollmentYear(decoded, periodYear);
        if (year != null) {
            parts.add("набор " + year);
        } else if (decoded.shortCourse()) {
            parts.add("короткий курс — семестр 1");
        }
        return String.join(" · ", parts);
    }

    private static Integer enrollmentYear(GroupNumber decoded, Integer periodYear) {
        if (decoded.enrollmentDigit() == null || periodYear == null) {
            return null;
        }
        return GroupNumberDecoder.enrollmentYear(decoded.enrollmentDigit(), periodYear);
    }

    /**
     * Год набора у нас пуст, а из номера он выводится — это понадобится шагу записи (семестр курса
     * считается от года набора), поэтому лучше увидеть заранее.
     */
    private static String enrollmentNote(GroupNumber decoded, Integer periodYear, Group ours) {
        Integer fromNumber = enrollmentYear(decoded, periodYear);
        if (fromNumber == null || ours.getEnrollmentYear() != null) {
            return null;
        }
        return "год набора у нас не заполнен, из номера выходит " + fromNumber;
    }

    // =======================================================================
    // Потоки — состав слушателей, а не список групп
    // =======================================================================

    /**
     * Потоки, увиденные в расписании: каждый различный <b>состав групп</b> — один поток.
     *
     * <p>Поток в файле не назван — он виден тем, что несколько групп стоят в одной ячейке времени
     * (потоковое занятие). Одна и та же группа встречается сотни раз: и сама по себе, и в разных
     * потоках. Поэтому ключ здесь — <b>множество групп</b>, приведённое к канону (отсортировано,
     * без повторов): пятьсот одинаковых ячеек дают одну строку отчёта.</p>
     *
     * <p><b>Сверка идёт по составу, а не по имени.</b> Имя потока мы придумываем сами, а у уже
     * заведённого оно может быть любым — сравнивать по нему значило бы завести второй поток с тем
     * же составом. Обратный случай (имя занято потоком с другим составом) — не отказ и не
     * переименование, а строка «неоднозначно»: решает человек.</p>
     *
     * <p><b>Одногруппные потоки помечаются производными</b> ({@link MatchRow#asDerived()}) и на
     * экране сворачиваются. Их ровно столько же, сколько групп, зовутся они так же, и решения по
     * ним те же — то есть они дословно повторяют раздел «Группы» и вытесняют с экрана
     * <b>сводные</b> потоки, ради которых раздел и читают. Из отчёта они при этом не исчезают:
     * заводится только названное (И-10), а без своего потока занятие группы не к чему привязать.</p>
     */
    private MatchSection streams(List<ParsedSheet> sheets, SuffixStyle style) {
        Map<String, Set<String>> ours = streamRepository.findAll().stream()
                .collect(Collectors.toMap(
                        stream -> streamKey(stream.getGroups().stream().map(Group::getName).toList()),
                        stream -> Set.of(stream.getName()),
                        (first, second) -> {
                            Set<String> merged = new LinkedHashSet<>(first);
                            merged.addAll(second);
                            return merged;
                        }));

        Set<String> takenNames = streamRepository.findAll().stream()
                .map(stream -> key(stream.getName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<String>> files = new LinkedHashMap<>();
        List<MatchRow> rows = new ArrayList<>();
        for (Seen seen : seenCompositions(sheets, style).values()) {
            List<String> composition = seen.groups();
            String canonical = streamKey(composition);
            String name = streamName(composition);
            boolean single = composition.size() == 1;
            String detail = single
                    ? "одна группа — заводится вместе с ней"
                    : composition.size() + " групп(ы): " + String.join(", ", composition);
            files.put(name, List.copyOf(seen.files()));

            MatchRow row;
            Set<String> existing = ours.get(canonical);
            if (existing != null) {
                row = MatchRow.matched(name, detail, null, String.join(", ", existing),
                        existing.contains(name) ? null : "у нас называется иначе — переименовывать не нужно");
            } else if (takenNames.contains(key(name))) {
                // Занятое имя — решение человека, а значит строка не рутинная, даже если группа одна.
                row = MatchRow.ambiguous(name, detail,
                        "имя занято потоком с другим составом групп — развести должен человек");
                single = false;
            } else {
                row = MatchRow.missing(name, detail, "в базе нет");
            }
            rows.add(single ? row.asDerived() : row);
        }
        return section(ImportMatchReport.STREAMS,
                "по составу групп, а не по имени: несколько групп в одной ячейке времени = поток; "
                        + "одногруппные свёрнуты — они повторяют раздел «Группы»",
                rows, files);
    }

    /**
     * Состав групп и файлы, где он встретился.
     *
     * @param groups состав, приведённый к канону (без повторов, отсортирован)
     * @param files  файлы, в которых этот состав виден
     */
    private record Seen(List<String> groups, LinkedHashSet<String> files) {
    }

    /**
     * Различные составы групп во всех файлах, в порядке первой встречи.
     *
     * <p>Состав из одной группы — тоже поток: у неё будут собственные занятия, и им нужен
     * одноимённый поток, иначе занятие не к чему привязать.</p>
     */
    private static Map<String, Seen> seenCompositions(List<ParsedSheet> sheets, SuffixStyle style) {
        Map<String, Seen> byKey = new LinkedHashMap<>();
        for (ParsedSheet sheet : sheets) {
            for (CellDialect.LessonEntry lesson : CellDialect.readAll(sheet)) {
                // Тот же фильтр, что в разделе «Группы»: состав потока не может включать строку,
                // которая группой не является, иначе поток заведётся с мусорным участником.
                // Написание приводится сразу: иначе один и тот же поток, записанный в одном файле
                // через «/», а в другом через «-», приехал бы двумя разными составами.
                List<String> groups = lesson.groups().stream()
                        .filter(ImportMatchingService::looksLikeGroupNumber)
                        .map(group -> GroupNumberDecoder.render(group, style))
                        .distinct()
                        .sorted()
                        .toList();
                if (!groups.isEmpty()) {
                    byKey.computeIfAbsent(streamKey(groups), key -> new Seen(groups, new LinkedHashSet<>()))
                            .files().add(sheet.sourceName());
                }
            }
        }
        return byKey;
    }

    /** Различные составы групп — вход для заведения потоков; имена в выбранном написании. */
    static List<List<String>> compositions(List<ParsedSheet> sheets, SuffixStyle style) {
        return seenCompositions(sheets, style).values().stream().map(Seen::groups).toList();
    }

    /** Длиннее этого номера групп не бывает; всё длиннее — это текст, попавший не в ту строку. */
    private static final int MAX_GROUP_NUMBER_LENGTH = 20;

    /** То же для имени комнаты: «Сп. зал» длинное, но не настолько. */
    private static final int MAX_ROOM_NAME_LENGTH = 40;

    /**
     * Похоже ли значение на номер группы.
     *
     * <p><b>Зачем проверка вообще.</b> Разбор ячейки правило «всё между первой и последней строкой —
     * группы» применяет буквально, и это правильно: судить о содержимом — не его дело. Но в живых
     * файлах между строками попадается и не группа, а текст; без проверки он доезжал до
     * {@code INSERT} и падал на длине колонки (2026-08-15, {@code value too long for character
     * varying(255)}) — то есть ошибка вылезала в самом дальнем от причины месте.</p>
     *
     * <p>Правило нарочно широкое: номера бывают «911», «1155-1», «855/11», «10593». Требуем лишь
     * цифру и разумную длину — этого хватает, чтобы отсечь прозу, и не хватает, чтобы отсечь
     * нестандартный номер, который должен попасть в отчёт как «не по стандарту», а не исчезнуть.</p>
     */
    static boolean looksLikeGroupNumber(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= MAX_GROUP_NUMBER_LENGTH
                && trimmed.chars().anyMatch(Character::isDigit);
    }

    /** Длинный текст в отчёте обрезаем: строка таблицы не должна разъезжаться на абзац. */
    private static String shorten(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "…";
    }

    /**
     * Канон состава: отсортированные имена без повторов — по нему и сверяем, и схлопываем.
     *
     * <p>Имя группы приводится к <b>номеру</b> ({@code GroupNumberDecoder.key}), а не просто к
     * нижнему регистру: у заведённого потока группы могут быть записаны через дефис, а в файле — через
     * косую черту, и сравнение «как есть» завело бы второй поток с тем же составом.</p>
     */
    private static String streamKey(Collection<String> groups) {
        return groups.stream().map(GroupNumberDecoder::key).distinct().sorted()
                .collect(Collectors.joining("+"));
    }

    /**
     * Имя потока: у одиночной группы — её собственное («911»), у сводного — склейка через «+».
     *
     * <p>Одноимённость для одиночной группы не украшение: она делает поток узнаваемым в расписании
     * и в бланке, где иначе стояло бы служебное «поток №17».</p>
     */
    static String streamName(List<String> groups) {
        return String.join("+", groups);
    }

    // =======================================================================
    // Дисциплины
    // =======================================================================

    private MatchSection disciplines(List<ParsedSheet> sheets) {
        List<Discipline> all = disciplineRepository.findAll();
        Map<String, Discipline> byAbbreviation = byKey(all, Discipline::getAbbreviation);
        Map<String, Discipline> byName = byKey(all, Discipline::getName);

        // «Та же дисциплина» знает словарь — он же кормит склейку разрезов. Одну дисциплину в разных
        // файлах сокращают по-разному, а полное название есть только в подвале группового файла;
        // две копии этого правила разъехались бы при первой правке.
        DisciplineDictionary dictionary = DisciplineDictionary.of(sheets);

        Origins origins = collect(sheets, ImportMatchingService::disciplineCodes);
        Map<String, List<String>> codesByCanonical = new LinkedHashMap<>();
        for (String code : origins.values()) {
            codesByCanonical.computeIfAbsent(dictionary.canonicalOf(code), ignored -> new ArrayList<>()).add(code);
        }

        Map<String, List<String>> files = new LinkedHashMap<>();
        List<MatchRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : codesByCanonical.entrySet()) {
            String code = entry.getKey();
            List<String> variants = entry.getValue();
            files.put(code, origins.filesOf(variants));
            DisciplineFooterParser.FooterRow row = dictionary.footerOf(code);
            String fullName = row == null ? null : row.name();

            // Ищем по любому из обозначений: у нас могло быть заведено под тем, что в этом файле
            // не встретилось ни разу.
            Discipline found = variants.stream()
                    .map(variant -> byAbbreviation.get(key(variant)))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            String note = null;
            if (found == null && fullName != null) {
                found = byName.get(key(fullName));
                if (found != null) {
                    note = "нашлась по названию: обозначение у нас «" + orDash(found.getAbbreviation()) + "»";
                }
            }

            String detail = row == null ? null
                    : fullName + (row.department().isBlank() ? "" : " · каф. " + row.department());
            if (variants.size() > 1) {
                // Расхождение обозначений — находка, а не шум: одна дисциплина, а сокращают её в
                // разных файлах по-своему, и склейка занятий по обозначению это должна учитывать.
                detail = (detail == null ? "" : detail + " · ")
                        + "обозначения: " + String.join(", ", variants);
            }

            if (found != null) {
                rows.add(MatchRow.matched(code, detail, found.getId(), found.getName(), note));
            } else if (row == null) {
                // Обозначение из ячейки, а в подвале такой строки нет: названия взять неоткуда,
                // а заводить дисциплину с именем-аббревиатурой — плодить мусор в справочнике.
                rows.add(MatchRow.unreadable(code, null,
                        "в подвале нет строки с этим обозначением — названия взять неоткуда"));
            } else if (fullName == null || fullName.isBlank()
                    || DisciplineFooterParser.looksLikePlanIndex(fullName)) {
                rows.add(MatchRow.unreadable(code, detail,
                        "в подвале вместо названия индекс плана — завести вручную"));
            } else {
                rows.add(MatchRow.missing(code, detail, "в базе нет"));
            }
        }
        return section(ImportMatchReport.DISCIPLINES, "по обозначению, затем по названию из подвала", rows, files);
    }

    private static List<String> disciplineCodes(ParsedSheet sheet) {
        List<String> found = new ArrayList<>();
        sheet.footer().forEach(row -> found.add(row.code()));
        CellDialect.readAll(sheet).forEach(lesson -> found.add(lesson.discipline()));
        return found;
    }

    // =======================================================================
    // Аудитории
    // =======================================================================

    private MatchSection rooms(List<ParsedSheet> sheets, Integer locationId) {
        List<Auditorium> all = auditoriumRepository.findAll();
        Map<String, String> owners = OrgUnitHints.byRoom(sheets);

        Origins origins = collect(sheets, ImportMatchingService::roomNumbers);
        Map<String, List<String>> files = new LinkedHashMap<>();
        List<MatchRow> rows = new ArrayList<>();
        for (String raw : origins.values()) {
            files.put(raw.trim().length() > MAX_ROOM_NAME_LENGTH ? shorten(raw) : raw, origins.filesOf(raw));
            if (raw.trim().length() > MAX_ROOM_NAME_LENGTH) {
                // Хвост групповой ячейки читается как аудитории целиком — и туда попадает всё
                // лишнее, что в ней оказалось. Имена комнат короткие («Сп. зал», «262А-7»),
                // поэтому длина здесь единственный надёжный признак.
                rows.add(MatchRow.unreadable(shorten(raw), null,
                        "не похоже на номер аудитории — строка ячейки прочиталась как комната"));
                continue;
            }
            RoomNumber decoded = RoomNumberDecoder.decode(raw);
            if (decoded == null) {
                continue;
            }

            // Правило отбора — у одного владельца ({@link AuditoriumResolver}): сверка показывает
            // человеку ровно то, во что запись потом поставит занятие.
            List<Auditorium> candidates = AuditoriumResolver.candidates(all, raw, locationId);

            String detail = decoded.buildingUnknown()
                    ? "комната «" + decoded.name() + "», корпус не указан"
                    : "комната «" + decoded.name() + "», корпус " + decoded.building();

            if (candidates.size() == 1) {
                Auditorium found = candidates.get(0);
                rows.add(MatchRow.matched(raw, detail, found.getId(), fullNameOf(found),
                        ownerDifference(owners.get(raw.trim()), found)));
            } else if (candidates.isEmpty()) {
                rows.add(MatchRow.missing(raw, detail, locationId == null
                        ? "в базе нет; локация не выбрана — заводить некуда"
                        : "в базе нет"));
            } else {
                // И-17: локации в файле нет вовсе, а корпус «3» законно существует в нескольких.
                // Выбрать первую значило бы поставить занятие в физически другое здание.
                rows.add(MatchRow.ambiguous(raw, detail,
                        "одноимённых несколько — выберите локацию: " + candidates.stream()
                                .map(ImportMatchingService::fullNameOf)
                                .collect(Collectors.joining(", "))));
            }
        }
        return section(ImportMatchReport.ROOMS, "по имени комнаты и корпусу; локация — параметр импорта", rows, files);
    }

    /**
     * Имя нашего подразделения по тому, как оно названо в файле; {@code null} — не опознано.
     *
     * <p>Ровно то, что увидит человек в колонке «Кафедра»: не строка из файла, а <b>наша</b>
     * сущность. Не опознали или одноимённых несколько — пусто, и тогда в строке появляется выбор
     * вручную: назвать чужую кафедру хуже, чем не назвать никакой.</p>
     */
    private static String unitName(String fromFile, List<OrgUnit> units) {
        return OrgUnitHints.unique(units, fromFile).map(OrgUnit::getName).orElse(null);
    }

    /**
     * Что сказать о кафедре-владельце уже заведённой комнаты.
     *
     * <p>Импорт её <b>не проставляет и не переписывает</b>: принадлежность комнаты — master-данные,
     * и правило то же, что с написанием имени группы, — расхождение показываем, решает человек.
     * Заполняется кафедра только у комнат, которые импорт заводит сам.</p>
     *
     * @return замечание либо {@code null}, если говорить не о чем
     */
    private String ownerDifference(String fromFile, Auditorium ours) {
        if (fromFile == null) {
            return null;
        }
        OrgUnit owner = ours.getOrgUnit();
        if (owner == null) {
            return "в файле кафедра «" + fromFile + "» — у нас не указана";
        }
        String wanted = orgUnitKey(fromFile);
        boolean same = wanted.equals(orgUnitKey(owner.getShortName()))
                || wanted.equals(orgUnitKey(owner.getName()));
        return same ? null : "в файле кафедра «" + fromFile + "», у нас «" + owner.getName() + "»";
    }

    private static List<String> roomNumbers(ParsedSheet sheet) {
        List<String> found = new ArrayList<>();
        if (sheet.header().kind() == ParsedSheet.CutKind.AUDITORIUM && sheet.header().owner() != null) {
            found.add(sheet.header().owner());
        }
        CellDialect.readAll(sheet).forEach(lesson -> found.addAll(lesson.rooms()));
        return found;
    }

    private static String fullNameOf(Auditorium room) {
        return room.getName() + " (корпус " + room.getBuilding().getName()
                + ", " + room.getBuilding().getLocation().getName() + ")";
    }

    // =======================================================================
    // Общая механика разделов
    // =======================================================================

    /**
     * Значения одной категории и файлы, где они встретились: без повторов и в порядке встречи.
     *
     * <p>Порядок встречи, а не алфавит: он повторяет порядок файла, и найденное легче искать
     * глазами в исходнике.</p>
     */
    private static Origins collect(List<ParsedSheet> sheets, Function<ParsedSheet, List<String>> source) {
        Origins origins = new Origins();
        for (ParsedSheet sheet : sheets) {
            for (String value : source.apply(sheet)) {
                if (value != null && !value.isBlank()) {
                    origins.add(value.trim(), sheet.sourceName());
                }
            }
        }
        return origins;
    }

    /**
     * Откуда что приехало: значение из файла → файлы, в которых оно встретилось.
     *
     * <p>Ведётся <b>по сырому значению</b>, а не по ключу сопоставления: ключ схлопывает варианты
     * («Иванов Т.В. дин» и «Иванов Т.В. дин доц»), а вопрос человека звучит наоборот — «в каком
     * файле написано именно так». Объединение по вариантам делает уже строка отчёта.</p>
     */
    static final class Origins {

        private final Map<String, LinkedHashSet<String>> byValue = new LinkedHashMap<>();

        void add(String value, String file) {
            byValue.computeIfAbsent(value, key -> new LinkedHashSet<>()).add(file);
        }

        /** Значения в порядке первой встречи. */
        Set<String> values() {
            return byValue.keySet();
        }

        /** Файлы всех перечисленных вариантов значения — без повторов, в порядке встречи. */
        List<String> filesOf(Collection<String> variants) {
            LinkedHashSet<String> files = new LinkedHashSet<>();
            variants.forEach(variant -> files.addAll(byValue.getOrDefault(variant, new LinkedHashSet<>())));
            return List.copyOf(files);
        }

        List<String> filesOf(String value) {
            return filesOf(List.of(value));
        }
    }

    /**
     * Несопоставленные — вперёд: это то, что требует действия.
     *
     * <p>Провенанс приклеивается здесь, одним местом на все разделы: каждый раздел знает, какие
     * варианты значения свелись в строку, и кладёт файлы под её {@code source}.</p>
     *
     * @param files источник строки (её {@code source}) → файлы, откуда она приехала
     */
    private static MatchSection section(String title, String hint, List<MatchRow> rows,
                                        Map<String, List<String>> files) {
        List<MatchRow> sorted = rows.stream()
                .map(row -> row.withFiles(files.getOrDefault(row.source(), List.of())))
                // Производные — в самый хвост: они ничего не требуют, а фронт их ещё и сворачивает.
                .sorted(Comparator.comparing(MatchRow::derived)
                        .thenComparing(MatchRow::isMatched)
                        .thenComparing(MatchRow::source))
                .toList();
        int matched = (int) rows.stream().filter(MatchRow::isMatched).count();
        // Где принадлежность к подразделению вообще есть — решает бэк: у дисциплины и комнаты
        // «кафедры» в этом смысле нет, а фронт не должен выводить это из заголовка.
        // Отношение «входит в» одно и то же: человек и группа входят в кафедру, кафедра — в
        // факультет. Поэтому и столбец один, и правится он везде, где есть: у подразделения выбор
        // родителя снимает спор каналов, из-за которого заведение иначе просто пропустит узел.
        boolean column = ImportMatchReport.EDUCATORS.equals(title)
                || ImportMatchReport.GROUPS.equals(title)
                || ImportMatchReport.ORG_UNITS.equals(title);
        boolean assignable = column;
        // Подразделения — предусловие остальных разделов: на них ссылаются и человек, и группа,
        // и комната. Порядок заведения от этого уже зависит, теперь об этом знает и интерфейс.
        boolean prerequisite = ImportMatchReport.ORG_UNITS.equals(title);
        return new MatchSection(title, hint, rows.size(), matched, sorted, column, assignable, prerequisite);
    }

    private static <T> Map<String, T> byKey(List<T> entities, Function<T, String> naming) {
        return entities.stream()
                .filter(entity -> naming.apply(entity) != null && !naming.apply(entity).isBlank())
                .collect(Collectors.toMap(
                        entity -> key(naming.apply(entity)),
                        Function.identity(),
                        (first, second) -> first));
    }

    /** Ключ сравнения имён: регистр и краевые пробелы — оформление, а не различие. */
    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static boolean sameToken(String left, String right) {
        return key(left).equals(key(right));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
