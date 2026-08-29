package ru.services.importing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Auditorium;
import ru.entity.Building;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Location;
import ru.entity.OrgUnit;
import ru.dto.orgUnit.OrgUnitCreateDto;
import ru.dto.orgUnit.OrgUnitDto;
import ru.entity.dictionary.SpecialRank;
import ru.enums.OrgUnitType;
import ru.entity.logicSchema.StudyStream;
import ru.repository.AuditoriumRepository;
import ru.repository.BuildingRepository;
import ru.repository.DisciplineRepository;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.LocationRepository;
import ru.repository.OrgUnitRepository;
import ru.repository.SpecialRankRepository;
import ru.repository.StudyStreamRepository;
import ru.services.importing.EducatorNameDecoder.EducatorName;
import ru.services.importing.GroupNumberDecoder.GroupNumber;
import ru.services.importing.ImportCreationReport.CreatedRow;
import ru.services.importing.ImportCreationReport.CreationSection;
import ru.services.importing.ImportMatchReport.MatchRow;
import ru.services.importing.ImportMatchReport.MatchSection;
import ru.services.importing.ImportMatchReport.MatchStatus;
import ru.services.importing.OrgStructureReader.OrgUnitDraft;
import ru.services.orgunit.OrgUnitService;
import ru.services.importing.RoomNumberDecoder.RoomNumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import ru.exceptions.NotFoundException;

/**
 * Заводит в справочниках то, чего сверка не нашла. Расписания и учебного плана <b>не касается</b>.
 *
 * <h2>Почему это отдельный шаг и почему он безопасен именно сейчас</h2>
 * <p>Решение И-10 требует осторожности: завести преподавателя легко, а убрать — нет, как только на
 * него сошлётся назначение, удаление упрётся в {@code RESTRICT}. Но <b>пока ссылок нет, строки ещё
 * удаляемы</b> — поэтому справочники заводятся до плана и до размещений, отдельной командой, и
 * ошибку первого прогона можно снести руками. После шага записи такой возможности уже не будет.</p>
 *
 * <h2>Что НЕ заводится и почему</h2>
 * <ul>
 *   <li><b>Подразделение, роль которого файлы называют по-разному</b> — «и факультет, и кафедра»
 *       либо разные факультеты-родители в разных файлах. Сами подразделения <b>заводятся</b>
 *       (2026-08-15): роль написана в канале, а родитель — в шапке того же файла, см.
 *       {@link OrgStructureReader}. Но спор каналов разбирает человек: не тот родитель тихо
 *       выкидывает кафедру из охвата института (И-4).</li>
 *   <li><b>Неоднозначное</b> ({@link MatchStatus#AMBIGUOUS}) — «одноимённых несколько». Завести
 *       ещё одну строку поверх неоднозначности значило бы сделать её вечной.</li>
 *   <li><b>Непрочитанное</b> ({@link MatchStatus#UNREADABLE}) — например дисциплина, у которой в
 *       подвале вместо названия индекс плана: «ДС.1.О» не имя, и справочник от него испортится.</li>
 * </ul>
 *
 * <h2>Два числа, которых нет в файле</h2>
 * <p>{@code groups.size} и {@code auditorium.capacity} — {@code NOT NULL}, а выгрузка их не несёт.
 * Они приходят параметрами команды и помечаются в отчёте: вместимость это то, по чему потом
 * считается перебор в аудитории, и молчаливая константа дала бы выдуманные нарушения.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportCreationService {

    private final ImportMatchingService matchingService;
    private final OrgUnitService orgUnitService;
    private final EducatorRepository educatorRepository;
    private final GroupRepository groupRepository;
    private final DisciplineRepository disciplineRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final BuildingRepository buildingRepository;
    private final LocationRepository locationRepository;
    private final SpecialRankRepository specialRankRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final StudyStreamRepository streamRepository;

    /**
     * Настройки заведения — то, чего в файле нет.
     *
     * @param locationId          локация для аудиторий; без неё комнаты не заводятся вовсе
     * @param defaultGroupSize    размер заводимой группы; он же множитель для вместимости комнат
     * @param defaultRoomCapacity вместимость комнаты, про которую в файлах занятий нет
     * @param groupNameStyle      каким знаком писать суффикс группы: «101/1» или «101-1». В файлах
     *                            встречаются оба (дефис — там, где номер попал в имя файла), и какое
     *                            написание считать своим, решает человек, а не разбор
     * @param orgUnitsBySection   ручные привязки к подразделению: <b>раздел отчёта</b> → значение
     *                            строки → id подразделения. Одна карта, а не по карте на сущность:
     *                            иначе фронт обязан был бы знать, что «Преподаватели» кладутся в
     *                            одно поле, а «Группы» в другое, — то есть повторить у себя
     *                            классификацию разделов. См. {@link #overrideOrDefault}
     * @param orgUnitParents      родитель, выбранный человеком для самого подразделения:
     *                            имя строки → <b>имя</b> родителя. Именем, а не id, намеренно:
     *                            спорный факультет может быть ещё не заведён — его создаёт этот же
     *                            прогон, — и id у него попросту нет. Разрешается по тому же ключу,
     *                            что и всё остальное, уже после создания факультетов
     */
    public record Settings(Integer locationId, int defaultGroupSize, int defaultRoomCapacity,
                           GroupNumberDecoder.SuffixStyle groupNameStyle,
                           Map<String, Map<String, Integer>> orgUnitsBySection,
                           Map<String, String> orgUnitParents) {

        public Settings {
            orgUnitsBySection = orgUnitsBySection == null ? Map.of() : Map.copyOf(orgUnitsBySection);
            orgUnitParents = orgUnitParents == null ? Map.of() : Map.copyOf(orgUnitParents);
        }

        /** Прежняя форма — без ручных привязок; вызывающих у неё много, а решение это новое. */
        public Settings(Integer locationId, int defaultGroupSize, int defaultRoomCapacity,
                        GroupNumberDecoder.SuffixStyle groupNameStyle) {
            this(locationId, defaultGroupSize, defaultRoomCapacity, groupNameStyle, Map.of(), Map.of());
        }

        public Settings(Integer locationId, int defaultGroupSize, int defaultRoomCapacity,
                        GroupNumberDecoder.SuffixStyle groupNameStyle,
                        Map<String, Map<String, Integer>> orgUnitsBySection) {
            this(locationId, defaultGroupSize, defaultRoomCapacity, groupNameStyle, orgUnitsBySection, Map.of());
        }

        /** Что человек проставил в этом разделе; пусто — ничего не проставлял. */
        public Map<String, Integer> orgUnitsOf(String section) {
            return orgUnitsBySection.getOrDefault(section, Map.of());
        }
    }

    /**
     * Завести <b>только подразделения</b> — отдельным шагом, раньше всего остального.
     *
     * <h4>Почему отдельной командой, хотя внутри {@link #createMissing} они и так первые</h4>
     * <p>Порядок внутри одной транзакции спасает только <b>удачный</b> случай. А когда кафедру
     * завести не удалось — спор каналов, института нет или несколько, факультет-родитель не найден
     * (ровно случай «42 кафедра») — заведение не останавливается: преподаватели, группы и комнаты
     * той же командой создаются <b>без подразделения</b>. Отказа нет, есть сотни строк с тихо не
     * проставленной привязкой, и чинить их потом придётся по всему справочнику.</p>
     *
     * <p>Отдельный шаг разрывает это: человек заводит дерево, видит отчёт, разбирает спорные узлы —
     * и только потом заводит зависимых. Это то же правило И-10 («сначала отчёт, потом запись»),
     * применённое к <b>порядку</b>, а не только к моменту.</p>
     *
     * <p>Одноразовость шага держат сами данные: уже заведённое пропускается по ключу, поэтому
     * повторный запуск ничего не дублирует.</p>
     */
    @Transactional
    public ImportCreationReport createOrgUnitsOnly(List<ParsedSheet> sheets, Settings settings) {
        CreationSection section = createOrgUnits(sheets, settings);
        log.info("Заведение «{}»: создано {}, пропущено {}", section.title(), section.created(), section.skipped());
        return new ImportCreationReport(List.of(section));
    }

    /**
     * Заводит недостающее по тем же файлам, по которым считалась сверка.
     *
     * <p>Файлы разбираются заново, а не берутся из состояния сервера: у отчёта нет и не должно быть
     * серверной жизни между запросами — иначе появилась бы «висящая заявка», которую надо
     * протухать, чистить и синхронизировать. Разбор дешевле такой сущности.</p>
     *
     * <p>Подразделения заводятся и здесь — первыми: тот, кто нажал одну кнопку, не должен получить
     * людей без кафедры только потому, что не знал про отдельный шаг. Но если дерево спорное, шаг
     * {@link #createOrgUnitsOnly} даёт разобрать его <b>до</b> появления зависимых строк.</p>
     */
    @Transactional
    public ImportCreationReport createMissing(List<ParsedSheet> sheets, Settings settings) {
        ImportMatchReport report = matchingService.match(sheets, settings.locationId(), settings.groupNameStyle());

        Integer periodYear = sheets.stream()
                .map(sheet -> sheet.header().startYear())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        // Порядок — по зависимостям: подразделения первыми (на них ссылаются и преподаватель, и
        // группа), поток — после групп.
        List<CreationSection> sections = List.of(
                createOrgUnits(sheets, settings),
                createEducators(section(report, ImportMatchReport.EDUCATORS), sheets, settings),
                createGroups(section(report, ImportMatchReport.GROUPS), periodYear, settings),
                createStreams(section(report, ImportMatchReport.STREAMS), sheets, periodYear, settings),
                createDisciplines(section(report, ImportMatchReport.DISCIPLINES)),
                createRooms(section(report, ImportMatchReport.ROOMS), settings, sheets)
        );
        sections.forEach(s -> log.info("Заведение «{}»: создано {}, пропущено {}", s.title(), s.created(), s.skipped()));
        return new ImportCreationReport(sections);
    }

    // =======================================================================

    /**
     * Заводит подразделения — <b>первыми</b>: на них ссылаются и преподаватель, и группа.
     *
     * <p>Раньше этот шаг не делался, и причина была верной: по строке «91» не видно, кафедра это
     * или факультет. Но роль написана не в строке, а в <b>канале</b>, и {@link OrgStructureReader}
     * её сохраняет: «Факультет 9Ф» — факультет, «Кафедра: …» и цифры номера группы — кафедра, а
     * родитель берётся из шапки того же файла, а не подбирается по совпадению цифр.</p>
     *
     * <p>Заводится <b>через {@code OrgUnitService}</b>, а не через репозиторий: правило вложенности
     * (ранг родителя строго меньше) и уникальность имени среди соседей живут там, и второй вход,
     * который о них не знает, рано или поздно завёл бы кафедру под кафедрой.</p>
     */
    private CreationSection createOrgUnits(List<ParsedSheet> sheets, Settings settings) {
        // Индекс по ключу: и уже заведённое, и то, что создадим по ходу (кафедре нужен факультет,
        // созданный шагом выше в этом же проходе).
        Map<String, OrgUnit> known = new LinkedHashMap<>();
        for (OrgUnit unit : orgUnitRepository.findAll()) {
            known.putIfAbsent(ImportMatchingService.orgUnitKey(unit.getShortName()), unit);
            known.putIfAbsent(ImportMatchingService.orgUnitKey(unit.getName()), unit);
        }
        List<OrgUnit> institutes = orgUnitRepository.findAll().stream()
                .filter(unit -> unit.getType() == OrgUnitType.INSTITUTE)
                .toList();
        // Выбор человека приходит под тем именем, что стоит в отчёте; у черновика имя может быть
        // полнее («42 кафедра» против «42»). Сводим обе стороны к ключу — как и везде.
        Map<String, String> chosenParents = settings.orgUnitParents().entrySet().stream()
                .collect(Collectors.toMap(e -> ImportMatchingService.orgUnitKey(e.getKey()), Map.Entry::getValue,
                        (first, second) -> first));

        List<CreatedRow> rows = new ArrayList<>();
        for (OrgUnitDraft draft : OrgStructureReader.read(sheets)) {
            if (known.containsKey(draft.key())) {
                continue;
            }
            // Спор о РОЛИ выбором родителя не снимается: пока не ясно, факультет это или кафедра,
            // непонятно даже, какого ранга родитель ей нужен.
            if (draft.roleDisputed()) {
                rows.add(skipped(draft, draft.problem()));
                continue;
            }

            // Родителя ищем среди ВСЕГО известного: и заведённого раньше, и созданного этим же
            // прогоном шагом выше. Иначе выбрать спорный факультет было бы нельзя — в базе его ещё
            // нет, а в отчёте он есть.
            String chosenName = chosenParents.get(draft.key());
            OrgUnit chosen = chosenName == null ? null
                    : known.get(ImportMatchingService.orgUnitKey(chosenName));
            if (chosenName != null && chosen == null) {
                rows.add(skipped(draft, "выбранный родитель «" + chosenName
                        + "» не заведён — сначала заведите его"));
                continue;
            }

            // Спор о родителе («разные факультеты в разных файлах») — ровно тот вопрос, который
            // задавали человеку. Ответил — вопроса больше нет.
            if (chosen == null && draft.problem() != null) {
                rows.add(skipped(draft, draft.problem()));
                continue;
            }

            // И-4: институт должен существовать ДО импорта и быть ровно один. «Взять первый» здесь
            // особенно вредно — кафедра повисла бы в чужом институте, и это никак не видно.
            if (chosen == null && institutes.size() != 1) {
                rows.add(skipped(draft, institutes.isEmpty()
                        ? "института в базе нет — заведите его вручную, иначе подразделению не к чему крепиться"
                        : "институтов в базе несколько — выбрать родителя должен человек"));
                continue;
            }

            OrgUnit parent = chosen != null ? chosen : institutes.get(0);
            if (chosen == null && draft.type() == OrgUnitType.DEPARTMENT && !draft.underInstitute()) {
                parent = known.get(ImportMatchingService.orgUnitKey(draft.parentName()));
                if (parent == null) {
                    rows.add(skipped(draft, draft.parentName() == null
                            ? "в файлах не указан факультет — куда вешать кафедру, неизвестно"
                            : "факультет «" + draft.parentName() + "» не найден и не заведён"));
                    continue;
                }
            }

            OrgUnitDto created = orgUnitService.create(new OrgUnitCreateDto(
                    draft.name(), draft.shortName(), draft.type(), parent.getId()));
            known.put(draft.key(), orgUnitRepository.getReferenceById(created.id()));
            rows.add(new CreatedRow(draft.name(), created.id(),
                    created.name() + " (" + draft.type().getFullName().toLowerCase(Locale.ROOT)
                            + " в «" + parent.getName() + "»)",
                    "название и краткое имя взяты из файла — уточнить",
                    draft.files(), draft.files().size(), false));
        }
        return section(ImportMatchReport.ORG_UNITS, rows);
    }

    private static CreatedRow skipped(OrgUnitDraft draft, String reason) {
        return new CreatedRow(draft.name(), null, null, reason, draft.files(), draft.files().size(), false);
    }

    private CreationSection createEducators(List<MatchRow> missing, List<ParsedSheet> sheets,
                                            Settings settings) {
        List<SpecialRank> ranks = specialRankRepository.findAll();
        List<String> rankNames = ranks.stream().map(SpecialRank::getShortName).filter(Objects::nonNull).toList();
        Map<String, String> departments = OrgUnitHints.byEducator(sheets, rankNames);
        // Справочник читается один раз на раздел, а не на строку: строк тут сотни.
        List<OrgUnit> units = orgUnitRepository.findAll();

        List<CreatedRow> rows = new ArrayList<>();
        for (MatchRow row : missing) {
            EducatorName parsed = EducatorNameDecoder.decode(row.source(), rankNames);
            if (!parsed.recognized()) {
                rows.add(CreatedRow.of(row, null, null, parsed.problem()));
                continue;
            }

            Educator educator = new Educator();
            // Имя — в форме ключа: звание живёт отдельным полем, и «п/п-к» внутри имени сделал бы
            // сопоставление следующего импорта невозможным.
            educator.setName(parsed.key());
            ranks.stream()
                    .filter(rank -> rank.getShortName().equalsIgnoreCase(parsed.rank()))
                    .findFirst()
                    .ifPresent(educator::setSpecialRank);

            // Кафедра — из шапки ЕГО СОБСТВЕННОГО файла, а не из подвала группового: там «Каф.»
            // относится к дисциплине, и приписать по ней человека было бы догадкой. Выбор человека
            // (строка отчёта) сильнее файла: он для того и делается, что файл ответа не дал.
            String department = departments.get(parsed.key());
            Optional<OrgUnit> unit = overrideOrDefault(settings.orgUnitsOf(ImportMatchReport.EDUCATORS).get(row.source()), department, units);
            unit.ifPresent(educator::setOrgUnit);

            Educator saved = educatorRepository.save(educator);
            rows.add(CreatedRow.of(row, saved.getId(), saved.getName(), missingParts(parsed, department, unit)));
        }
        return section(ImportMatchReport.EDUCATORS, rows);
    }


    /**
     * Чего у заведённого преподавателя не хватает.
     *
     * <p>Степень и учёное звание из файла не разбираются: собирать подпись умеет ровно один класс в
     * проекте ({@code EducatorTitles}), и обратный разбор завёл бы второго владельца формата.</p>
     */
    private static String missingParts(EducatorName parsed, String department, Optional<OrgUnit> unit) {
        List<String> gaps = new ArrayList<>();
        if (unit.isEmpty()) {
            gaps.add(department == null
                    ? "подразделение не заполнено — его нет ни в одном файле этого человека"
                    : "кафедра «" + department + "» не найдена — подразделение не заполнено");
        }
        if (parsed.credentials() != null) {
            gaps.add("регалии «" + parsed.credentials() + "» не разобраны — проставить вручную");
        }
        return gaps.isEmpty() ? null : String.join("; ", gaps);
    }

    private CreationSection createGroups(List<MatchRow> missing, Integer periodYear, Settings settings) {
        List<OrgUnit> units = orgUnitRepository.findAll();
        List<CreatedRow> rows = new ArrayList<>();
        for (MatchRow row : missing) {
            GroupNumber number = GroupNumberDecoder.decode(row.source());

            Group group = new Group();
            group.setName(row.source());
            group.setSize(settings.defaultGroupSize());
            if (periodYear != null) {
                group.setEnrollmentYear(GroupNumberDecoder.enrollmentYear(number.enrollmentDigit(), periodYear));
            }

            // Кафедра группы известна точно — она закодирована в номере (§4 спецификации). Поэтому
            // здесь привязка не догадка, и если подразделение уже заведено, оно проставится само.
            // У преподавателя такой опоры нет: «Каф.» в подвале относится к дисциплине, а не к нему.
            // Ручной выбор сильнее номера — он и нужен там, где номер не по стандарту.
            Optional<OrgUnit> department =
                    overrideOrDefault(settings.orgUnitsOf(ImportMatchReport.GROUPS).get(row.source()),
                            number.departmentShortName(), units);
            department.ifPresent(group::setOrgUnit);

            Group saved = groupRepository.save(group);
            rows.add(CreatedRow.of(row, saved.getId(), saved.getName(),
                    "размер по умолчанию — уточнить"
                            + (department.isPresent() ? "" : "; кафедра "
                            + (number.departmentShortName() == null
                            ? "из номера не выводится" : "«" + number.departmentShortName() + "» не найдена")
                            + " — подразделение не заполнено")));
        }
        return section(ImportMatchReport.GROUPS, rows);
    }

    /**
     * Заводит потоки — по одному на различный состав групп.
     *
     * <p><b>Строго после групп:</b> состав ссылается на них, и группа, заведённая шагом выше,
     * должна быть уже видна. Поэтому список групп перечитывается из базы, а не берётся из отчёта.</p>
     *
     * <p>Дублей не будет по построению: сверка схлопнула составы в канон, а сюда приходят только
     * те, которых в базе нет ни под каким именем.</p>
     */
    private CreationSection createStreams(List<MatchRow> missing, List<ParsedSheet> sheets,
                                          Integer periodYear, Settings settings) {
        // Состав восстанавливаем из тех же файлов: в отчёте лежит имя потока, а не список групп.
        Map<String, List<String>> compositions =
                ImportMatchingService.compositions(sheets, settings.groupNameStyle()).stream()
                        .collect(Collectors.toMap(ImportMatchingService::streamName, Function.identity(),
                                (first, second) -> first));

        // По номеру, а не по строке имени: группа могла быть заведена с другим разделителем
        // суффикса («101-1» против «101/1»), и поиск «как есть» объявил бы её потерянной.
        Map<String, Group> groupsByName = groupRepository.findAll().stream()
                .collect(Collectors.toMap(group -> GroupNumberDecoder.key(group.getName()),
                        Function.identity(), (first, second) -> first));

        List<CreatedRow> rows = new ArrayList<>();
        for (MatchRow row : missing) {
            List<String> names = compositions.getOrDefault(row.source(), List.of());
            Set<Group> members = new LinkedHashSet<>();
            List<String> lost = new ArrayList<>();
            for (String name : names) {
                Group group = groupsByName.get(GroupNumberDecoder.key(name));
                if (group == null) {
                    lost.add(name);
                } else {
                    members.add(group);
                }
            }

            if (members.isEmpty() || !lost.isEmpty()) {
                // Поток без всех своих групп — это не поток, а полупустая запись, на которую потом
                // сошлются занятия. Лучше пропустить и назвать причину.
                rows.add(CreatedRow.of(row, null, null,
                        "нет групп: " + String.join(", ", lost.isEmpty() ? names : lost)));
                continue;
            }

            StudyStream stream = new StudyStream();
            stream.setName(row.source());
            stream.setGroups(members);
            stream.setSemester(semesterOf(members, periodYear));

            StudyStream saved = streamRepository.save(stream);
            rows.add(CreatedRow.of(row, saved.getId(), saved.getName(),
                    members.size() == 1 ? "поток из одной группы" : "групп: " + members.size()));
        }
        return section(ImportMatchReport.STREAMS, rows);
    }

    /**
     * Семестр потока: из года набора групп и года периода.
     *
     * <p>Поле обязательно, но на него никто не ветвится — оно описательное. Поэтому считаем так же,
     * как семестр курса, а при расхождении (в потоке группы разных наборов — законный случай для
     * сводного потока) берём наименьший и не делаем вид, что знаем точнее.</p>
     */
    private static int semesterOf(Set<Group> members, Integer periodYear) {
        if (periodYear == null) {
            return 1;
        }
        return members.stream()
                .map(Group::getEnrollmentYear)
                .filter(Objects::nonNull)
                .mapToInt(year -> Math.max(1, (periodYear - year) * 2 + 1))
                .min()
                .orElse(1);
    }

    private CreationSection createDisciplines(List<MatchRow> missing) {
        List<CreatedRow> rows = new ArrayList<>();
        for (MatchRow row : missing) {
            // detail собран сверкой как «полное название · каф. NN» — имя это часть до разделителя.
            String name = row.detail() == null ? row.source() : row.detail().split(" · ")[0].trim();

            // Страховка на случай, если два разных обозначения всё-таки принесут одно название:
            // имя дисциплины уникально, и такая вставка роняет весь прогон целиком. Пропустить с
            // объяснением полезнее, чем откатить сотню верных строк из-за одной.
            Optional<Discipline> existing = disciplineRepository.findByName(name);
            if (existing.isPresent()) {
                // id намеренно null: строка попадёт в «пропущено», а не в «заведено» — мы ничего
                // не создали, и счётчик не должен утверждать обратное.
                rows.add(CreatedRow.of(row, null, null,
                        "«" + name + "» уже есть под обозначением «"
                                + (existing.get().getAbbreviation() == null ? "—" : existing.get().getAbbreviation())
                                + "» — обозначения расходятся, свести вручную"));
                continue;
            }

            Discipline saved = disciplineRepository.save(new Discipline(name, row.source()));
            rows.add(CreatedRow.of(row, saved.getId(), saved.getName(), null));
        }
        return section(ImportMatchReport.DISCIPLINES, rows);
    }

    /**
     * Сколько групп одновременно сидит в комнате — по факту из расписания.
     *
     * <p><b>Вместимость лекционной не выдумывается, а выводится.</b> Правило заказчика — «лекционная
     * должна вмещать всех» — в файлах уже записано: если в одну дату и пару в комнате стоит занятие
     * у четырёх групп, значит поток четырёхгрупповой и комната на него рассчитана. Это тот же приём,
     * которым восстанавливаются потоки (§9 спецификации), только ответ снимается числом.</p>
     *
     * <p>Для лаборатории с одной группой выйдет один размер группы, для поточной аудитории —
     * четыре. Плоская константа дала бы обеим одно и то же, а по вместимости потом считается
     * перебор — то есть неверное число сразу превратилось бы в выдуманные нарушения.</p>
     *
     * @return комната (как в файле) → наибольшее число разных групп в одной ячейке времени
     */
    private static Map<String, Integer> concurrentGroupsByRoom(List<ParsedSheet> sheets) {
        // комната → (дата+пара) → множество групп
        Map<String, Map<String, Set<String>>> occupancy = new HashMap<>();
        for (ParsedSheet sheet : sheets) {
            for (CellDialect.LessonEntry lesson : CellDialect.readAll(sheet)) {
                if (lesson.date() == null) {
                    continue;
                }
                // Групп в ячейке может быть несколько (потоковое занятие) — тогда одна ячейка
                // времени сразу даёт весь поток, и вместимость выходит верной по одному файлу.
                for (String room : lesson.rooms()) {
                    Set<String> here = occupancy
                            .computeIfAbsent(room.trim(), key -> new HashMap<>())
                            .computeIfAbsent(lesson.date() + "/" + lesson.slot(), key -> new HashSet<>());
                    lesson.groups().forEach(group -> here.add(group.trim()));
                }
            }
        }

        Map<String, Integer> result = new HashMap<>();
        occupancy.forEach((room, cells) -> result.put(room,
                cells.values().stream().mapToInt(Set::size).max().orElse(0)));
        return result;
    }

    private CreationSection createRooms(List<MatchRow> missing, Settings settings, List<ParsedSheet> sheets) {
        Map<String, Integer> concurrent = concurrentGroupsByRoom(sheets);
        Map<String, String> departments = OrgUnitHints.byRoom(sheets);
        List<OrgUnit> units = orgUnitRepository.findAll();
        List<CreatedRow> rows = new ArrayList<>();
        if (settings.locationId() == null) {
            for (MatchRow row : missing) {
                rows.add(CreatedRow.of(row, null, null,
                        "локация не выбрана — в каком кампусе заводить комнату, неизвестно"));
            }
            return section(ImportMatchReport.ROOMS, rows);
        }

        Optional<Location> location = locationRepository.findById(settings.locationId());
        if (location.isEmpty()) {
            for (MatchRow row : missing) {
                rows.add(CreatedRow.of(row, null, null, "выбранной локации больше нет"));
            }
            return section(ImportMatchReport.ROOMS, rows);
        }

        for (MatchRow row : missing) {
            RoomNumber decoded = RoomNumberDecoder.decode(row.source());
            if (decoded == null) {
                continue;
            }
            if (decoded.buildingUnknown()) {
                // «Сп. зал» — законное имя без корпуса (И-16), но завести его вслепую нельзя:
                // корпус обязателен, а какой именно — файл не говорит.
                rows.add(CreatedRow.of(row, null, null,
                        "корпус в номере не указан — завести вручную, выбрав корпус"));
                continue;
            }

            Building building = buildingRepository.findAll().stream()
                    .filter(b -> b.getLocation().getId().equals(location.get().getId()))
                    .filter(b -> b.getName().trim().equalsIgnoreCase(decoded.building()))
                    .findFirst()
                    .orElseGet(() -> {
                        Building fresh = new Building();
                        fresh.setName(decoded.building());
                        fresh.setLocation(location.get());
                        return buildingRepository.save(fresh);
                    });

            int groups = concurrent.getOrDefault(row.source().trim(), 0);
            int capacity = groups > 0 ? groups * settings.defaultGroupSize() : settings.defaultRoomCapacity();

            Auditorium room = new Auditorium();
            room.setName(decoded.name());
            room.setCapacity(capacity);
            room.setBuilding(building);

            // Кафедра-владелец — из шапки ЕГО СОБСТВЕННОГО файла, ровно как у преподавателя:
            // «Загрузка учебной аудитории 252-3 … Кафедра: 91 кафедра». Корпус говорит, где комната
            // стоит, кафедра — чья она; это разные вопросы, и подбор комнат смотрит на второй.
            String department = departments.get(row.source().trim());
            Optional<OrgUnit> owner = OrgUnitHints.unique(units, department);
            owner.ifPresent(room::setOrgUnit);

            Auditorium saved = auditoriumRepository.save(room);
            rows.add(CreatedRow.of(row, saved.getId(),
                    saved.getName() + " (корпус " + building.getName()
                            + owner.map(unit -> ", " + unit.getName()).orElse("") + ")",
                    roomNote(groups, capacity, settings, department, owner)));
        }
        return section(ImportMatchReport.ROOMS, rows);
    }


    /** Замечание к заведённой комнате: вместимость выведена, а кафедра — нашлась ли. */
    private static String roomNote(int groups, int capacity, Settings settings,
                                   String department, Optional<OrgUnit> owner) {
        String about = groups > 0
                ? "вместимость " + capacity + " — по факту до " + groups
                + " групп(ы) одновременно × " + settings.defaultGroupSize() + "; уточнить"
                : "вместимость по умолчанию (занятий в этой комнате в файлах нет) — уточнить";
        if (department == null) {
            return about + ". Кафедра в файлах не указана";
        }
        // Не нашли или нашли несколько — это находка, а не повод привязать наугад: чужая кафедра
        // молча даст предпочтение не тем комнатам при подборе, и увидеть это будет нечем.
        return owner.isPresent()
                ? about
                : about + ". Кафедра «" + department + "» не опознана — привязать вручную";
    }

    // =======================================================================

    /**
     * Что победит: выбор человека или вывод из файла.
     *
     * <p><b>Человек сильнее файла — и только он.</b> Ручная привязка появляется там, где разбор
     * ответа не дал: у преподавателя нет своего файла, номер группы не по стандарту, кафедра в базе
     * не заведена или одноимённых несколько. Заводить в таких случаях «без подразделения» и чинить
     * потом руками по всему справочнику — та самая работа, которой человек и хочет избежать.</p>
     *
     * <p>Вывод из файла разрешается через {@link OrgUnitHints#unique} — «и только если оно одно»:
     * несколько одноимённых значит, что выбирать должен человек, и приписать к не той кафедре
     * молча нельзя (ошибку потом не увидеть, расписание будет выглядеть нормально).</p>
     *
     * <p>Несуществующий id молча не глотаем: выбор сделан осознанно, и если подразделение исчезло
     * между сверкой и записью, честнее упасть, чем тихо завести человека не там.</p>
     *
     * @param chosen   id, выбранный в отчёте, либо {@code null}
     * @param fromFile имя подразделения, выведенное разбором
     * @param units    справочник, прочитанный <b>один раз на раздел</b>: строк в разделе сотни, и
     *                 выборка на каждую превращала бы заведение в сотни одинаковых запросов
     */
    private Optional<OrgUnit> overrideOrDefault(Integer chosen, String fromFile, List<OrgUnit> units) {
        if (chosen == null) {
            return OrgUnitHints.unique(units, fromFile);
        }
        return Optional.of(orgUnitRepository.findById(chosen).orElseThrow(
                () -> new NotFoundException("Подразделение с id=" + chosen + " не найдено")));
    }

    /** Из отчёта сверки берутся только строки «в базе нет» — остальные пропускаются намеренно. */
    private static List<MatchRow> section(ImportMatchReport report, String title) {
        return report.sections().stream()
                .filter(s -> s.title().equals(title))
                .findFirst()
                .map(MatchSection::rows)
                .orElse(List.of())
                .stream()
                .filter(row -> row.status() == MatchStatus.MISSING)
                .toList();
    }

    private static CreationSection section(String title, List<CreatedRow> rows) {
        int created = (int) rows.stream().filter(row -> row.id() != null).count();
        return new CreationSection(title, created, rows.size() - created, rows);
    }
}
