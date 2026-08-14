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
import ru.services.importing.ImportMatchReport.MatchRow;
import ru.services.importing.ImportMatchReport.MatchSection;
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
     * @param sheets     разобранные файлы (любых разрезов)
     * @param locationId локация, в которой искать аудитории; {@code null} — искать во всех и
     *                   сообщать о неоднозначности (И-17: локации в файле нет вовсе)
     */
    @Transactional(readOnly = true)
    public ImportMatchReport match(List<ParsedSheet> sheets, Integer locationId) {
        List<ImportMatchReport.MatchSection> sections = List.of(
                orgUnits(sheets),
                educators(sheets),
                groups(sheets),
                streams(sheets),
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
        Map<String, List<String>> variants = new LinkedHashMap<>();
        for (String raw : collect(sheets, ImportMatchingService::orgUnitNames)) {
            variants.computeIfAbsent(orgUnitKey(raw), key -> new ArrayList<>()).add(raw);
        }

        List<MatchRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<String>> unit : variants.entrySet()) {
            String raw = unit.getValue().get(0);
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

            if (candidates.size() == 1) {
                OrgUnit found = candidates.get(0);
                rows.add(MatchRow.matched(raw, found.getType().getFullName(),
                        found.getId(), found.getName(), seenAs));
            } else if (candidates.isEmpty()) {
                rows.add(MatchRow.missing(raw, seenAs, "в базе нет"));
            } else {
                rows.add(MatchRow.ambiguous(raw, seenAs, "в базе несколько: " + candidates.stream()
                        .map(OrgUnit::getName).collect(Collectors.joining(", "))));
            }
        }
        return section("Подразделения", "по краткому имени, затем по полному", rows);
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
            GroupNumber number = GroupNumberDecoder.decode(sheet.header().owner());
            if (number.recognized()) {
                found.add(number.departmentShortName());
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
        Map<String, List<String>> variantsByKey = new LinkedHashMap<>();
        List<MatchRow> rows = new ArrayList<>();

        for (String signature : collect(sheets, ImportMatchingService::educatorSignatures)) {
            EducatorName parsed = EducatorNameDecoder.decode(signature, ranks);
            if (!parsed.recognized()) {
                rows.add(MatchRow.unreadable(signature, describe(parsed), parsed.problem()));
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

            List<Educator> candidates = ours.getOrDefault(person.getKey(), List.of());
            if (candidates.isEmpty()) {
                rows.add(MatchRow.missing(signature, detail, "в базе нет"));
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
        return section("Преподаватели", "по фамилии и инициалам; звание и степень в ключ не входят", rows);
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
     * <p>Расхождение регалий между файлами показывается, а не прячется: «дин» в одном файле и
     * «дин доц» в другом — это либо разное время выгрузки, либо опечатка, и решает человек.
     * Сводить их к «самому полному» варианту молча нельзя — тогда исчезнет сам факт расхождения.</p>
     */
    private static String describeVariants(List<String> signatures, List<String> ranks) {
        Set<String> descriptions = signatures.stream()
                .map(signature -> describe(EducatorNameDecoder.decode(signature, ranks)))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (descriptions.isEmpty()) {
            return null;
        }
        return descriptions.size() == 1
                ? descriptions.iterator().next()
                : "в файлах по-разному: " + String.join(" / ", descriptions);
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
        if (!sameToken(fromFile.credentials(), ourTail)) {
            notes.add("регалии: в файле «" + orDash(fromFile.credentials()) + "», у нас «" + orDash(ourTail) + "»");
        }
        return notes.isEmpty() ? null : "уточнить — " + String.join("; ", notes);
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

    private MatchSection groups(List<ParsedSheet> sheets) {
        Map<String, Group> ours = byKey(groupRepository.findAll(), Group::getName);
        Integer periodYear = sheets.stream()
                .map(sheet -> sheet.header().startYear())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        List<MatchRow> rows = new ArrayList<>();
        for (String number : collect(sheets, ImportMatchingService::groupNumbers)) {
            if (!looksLikeGroupNumber(number)) {
                // Строка ячейки прочитана как группа, но группой быть не может. Не выбрасываем
                // молча: это находка о разметке файла, а не мусор — и заводить её нельзя.
                rows.add(MatchRow.unreadable(shorten(number), null,
                        "не похоже на номер группы — строка ячейки прочиталась как группа"));
                continue;
            }
            GroupNumber decoded = GroupNumberDecoder.decode(number);
            Group found = ours.get(key(number));

            String detail = decoded.recognized()
                    ? describeGroup(decoded, periodYear)
                    : decoded.problem();

            rows.add(found == null
                    ? MatchRow.missing(number, detail, "в базе нет")
                    : MatchRow.matched(number, detail, found.getId(), found.getName(),
                            enrollmentNote(decoded, periodYear, found)));
        }
        return section("Группы", "по номеру группы как имени", rows);
    }

    private static List<String> groupNumbers(ParsedSheet sheet) {
        List<String> found = new ArrayList<>();
        if (sheet.header().kind() == ParsedSheet.CutKind.GROUP && sheet.header().owner() != null) {
            found.add(sheet.header().owner());
        }
        CellDialect.readAll(sheet).forEach(lesson -> found.addAll(lesson.groups()));
        return found;
    }

    private static String describeGroup(GroupNumber decoded, Integer periodYear) {
        List<String> parts = new ArrayList<>();
        parts.add("ф. " + decoded.facultyCode());
        parts.add("каф. " + decoded.departmentShortName());
        Integer year = enrollmentYear(decoded, periodYear);
        if (year != null) {
            parts.add("набор " + year);
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
     */
    private MatchSection streams(List<ParsedSheet> sheets) {
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

        List<MatchRow> rows = new ArrayList<>();
        for (List<String> composition : compositions(sheets)) {
            String canonical = streamKey(composition);
            String name = streamName(composition);
            String detail = composition.size() == 1
                    ? "одна группа"
                    : composition.size() + " групп(ы): " + String.join(", ", composition);

            Set<String> existing = ours.get(canonical);
            if (existing != null) {
                rows.add(MatchRow.matched(name, detail, null, String.join(", ", existing),
                        existing.contains(name) ? null : "у нас называется иначе — переименовывать не нужно"));
            } else if (takenNames.contains(key(name))) {
                rows.add(MatchRow.ambiguous(name, detail,
                        "имя занято потоком с другим составом групп — развести должен человек"));
            } else {
                rows.add(MatchRow.missing(name, detail, "в базе нет"));
            }
        }
        return section("Потоки", "по составу групп, а не по имени", rows);
    }

    /**
     * Различные составы групп во всех файлах, в порядке первой встречи.
     *
     * <p>Состав из одной группы — тоже поток: у неё будут собственные занятия, и им нужен
     * одноимённый поток, иначе занятие не к чему привязать.</p>
     */
    static List<List<String>> compositions(List<ParsedSheet> sheets) {
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        for (ParsedSheet sheet : sheets) {
            for (CellDialect.LessonEntry lesson : CellDialect.readAll(sheet)) {
                // Тот же фильтр, что в разделе «Группы»: состав потока не может включать строку,
                // которая группой не является, иначе поток заведётся с мусорным участником.
                List<String> groups = lesson.groups().stream()
                        .filter(ImportMatchingService::looksLikeGroupNumber)
                        .map(String::trim)
                        .distinct()
                        .sorted()
                        .toList();
                if (!groups.isEmpty()) {
                    byKey.putIfAbsent(streamKey(groups), groups);
                }
            }
        }
        return List.copyOf(byKey.values());
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

    /** Канон состава: отсортированные имена без повторов — по нему и сверяем, и схлопываем. */
    private static String streamKey(Collection<String> groups) {
        return groups.stream().map(ImportMatchingService::key).distinct().sorted()
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

        // Подвал знает и обозначение, и полное название — по нему сверка точнее, чем по ячейке.
        Map<String, DisciplineFooterParser.FooterRow> footer = sheets.stream()
                .flatMap(sheet -> sheet.footer().stream())
                .collect(Collectors.toMap(row -> key(row.code()), Function.identity(), (first, second) -> first));

        List<MatchRow> rows = new ArrayList<>();
        for (String code : collect(sheets, ImportMatchingService::disciplineCodes)) {
            DisciplineFooterParser.FooterRow row = footer.get(key(code));
            String fullName = row == null ? null : row.name();

            Discipline found = byAbbreviation.get(key(code));
            String note = null;
            if (found == null && fullName != null) {
                found = byName.get(key(fullName));
                if (found != null) {
                    note = "нашлась по названию: обозначение у нас «" + orDash(found.getAbbreviation()) + "»";
                }
            }

            String detail = row == null ? null
                    : fullName + (row.department().isBlank() ? "" : " · каф. " + row.department());

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
        return section("Дисциплины", "по обозначению, затем по названию из подвала", rows);
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

        List<MatchRow> rows = new ArrayList<>();
        for (String raw : collect(sheets, ImportMatchingService::roomNumbers)) {
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

            List<Auditorium> candidates = all.stream()
                    .filter(room -> key(room.getName()).equals(key(decoded.name())))
                    .filter(room -> decoded.buildingUnknown()
                            || key(room.getBuilding().getName()).equals(key(decoded.building())))
                    .filter(room -> locationId == null
                            || locationId.equals(room.getBuilding().getLocation().getId()))
                    .toList();

            String detail = decoded.buildingUnknown()
                    ? "комната «" + decoded.name() + "», корпус не указан"
                    : "комната «" + decoded.name() + "», корпус " + decoded.building();

            if (candidates.size() == 1) {
                Auditorium found = candidates.get(0);
                rows.add(MatchRow.matched(raw, detail, found.getId(), fullNameOf(found), null));
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
        return section("Аудитории", "по имени комнаты и корпусу; локация — параметр импорта", rows);
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
     * Собирает значения одной категории по всем файлам: без повторов и в порядке встречи.
     *
     * <p>Порядок встречи, а не алфавит: он повторяет порядок файла, и найденное легче искать
     * глазами в исходнике.</p>
     */
    private static Set<String> collect(List<ParsedSheet> sheets, Function<ParsedSheet, List<String>> source) {
        return sheets.stream()
                .flatMap(sheet -> source.apply(sheet).stream())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Несопоставленные — вперёд: это то, что требует действия. */
    private static MatchSection section(String title, String hint, List<MatchRow> rows) {
        List<MatchRow> sorted = rows.stream()
                .sorted(Comparator.comparing(MatchRow::isMatched).thenComparing(MatchRow::source))
                .toList();
        int matched = (int) rows.stream().filter(MatchRow::isMatched).count();
        return new MatchSection(title, hint, rows.size(), matched, sorted);
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
