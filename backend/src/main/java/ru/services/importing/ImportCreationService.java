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
import ru.entity.dictionary.SpecialRank;
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
import ru.services.importing.RoomNumberDecoder.RoomNumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 *   <li><b>Подразделения.</b> Кафедре нужны вид и родитель: по строке «91» не видно, кафедра это
 *       или факультет, а поставить не того родителя значит тихо выкинуть кафедру из охвата
 *       института (И-4). Это структурное решение человека — раздел «Оргструктура» для него и есть.
 *       Группа и преподаватель заводятся без подразделения (поле nullable) и попадают в отчёт.</li>
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
     */
    public record Settings(Integer locationId, int defaultGroupSize, int defaultRoomCapacity) {
    }

    /**
     * Заводит недостающее по тем же файлам, по которым считалась сверка.
     *
     * <p>Файлы разбираются заново, а не берутся из состояния сервера: у отчёта нет и не должно быть
     * серверной жизни между запросами — иначе появилась бы «висящая заявка», которую надо
     * протухать, чистить и синхронизировать. Разбор дешевле такой сущности.</p>
     */
    @Transactional
    public ImportCreationReport createMissing(List<ParsedSheet> sheets, Settings settings) {
        ImportMatchReport report = matchingService.match(sheets, settings.locationId());

        Integer periodYear = sheets.stream()
                .map(sheet -> sheet.header().startYear())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        // Порядок — по зависимостям: поток ссылается на группы, поэтому идёт после них.
        List<CreationSection> sections = List.of(
                createEducators(section(report, "Преподаватели")),
                createGroups(section(report, "Группы"), periodYear, settings),
                createStreams(section(report, "Потоки"), sheets, periodYear),
                createDisciplines(section(report, "Дисциплины")),
                createRooms(section(report, "Аудитории"), settings, sheets)
        );
        sections.forEach(s -> log.info("Заведение «{}»: создано {}, пропущено {}", s.title(), s.created(), s.skipped()));
        return new ImportCreationReport(sections);
    }

    // =======================================================================

    private CreationSection createEducators(List<MatchRow> missing) {
        List<SpecialRank> ranks = specialRankRepository.findAll();
        List<String> rankNames = ranks.stream().map(SpecialRank::getShortName).filter(Objects::nonNull).toList();

        List<CreatedRow> rows = new ArrayList<>();
        for (MatchRow row : missing) {
            EducatorName parsed = EducatorNameDecoder.decode(row.source(), rankNames);
            if (!parsed.recognized()) {
                rows.add(new CreatedRow(row.source(), null, null, parsed.problem()));
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

            Educator saved = educatorRepository.save(educator);
            rows.add(new CreatedRow(row.source(), saved.getId(), saved.getName(), missingParts(parsed)));
        }
        return section("Преподаватели", rows);
    }

    /**
     * Чего у заведённого преподавателя не хватает.
     *
     * <p>Степень и учёное звание из файла не разбираются: собирать подпись умеет ровно один класс в
     * проекте ({@code EducatorTitles}), и обратный разбор завёл бы второго владельца формата.
     * Подразделение из группового файла не выводится — «Каф.» в подвале относится к дисциплине, а
     * не к человеку, и приписать преподавателя к ней было бы догадкой.</p>
     */
    private static String missingParts(EducatorName parsed) {
        List<String> gaps = new ArrayList<>();
        gaps.add("подразделение не заполнено");
        if (parsed.credentials() != null) {
            gaps.add("регалии «" + parsed.credentials() + "» не разобраны — проставить вручную");
        }
        return String.join("; ", gaps);
    }

    private CreationSection createGroups(List<MatchRow> missing, Integer periodYear, Settings settings) {
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
            Optional<OrgUnit> department = findUnique(number.departmentShortName());
            department.ifPresent(group::setOrgUnit);

            Group saved = groupRepository.save(group);
            rows.add(new CreatedRow(row.source(), saved.getId(), saved.getName(),
                    "размер по умолчанию — уточнить"
                            + (department.isPresent() ? "" : "; кафедра «" + number.departmentShortName()
                            + "» не найдена — подразделение не заполнено")));
        }
        return section("Группы", rows);
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
    private CreationSection createStreams(List<MatchRow> missing, List<ParsedSheet> sheets, Integer periodYear) {
        // Состав восстанавливаем из тех же файлов: в отчёте лежит имя потока, а не список групп.
        Map<String, List<String>> compositions = ImportMatchingService.compositions(sheets).stream()
                .collect(Collectors.toMap(ImportMatchingService::streamName, Function.identity(),
                        (first, second) -> first));

        Map<String, Group> groupsByName = groupRepository.findAll().stream()
                .collect(Collectors.toMap(group -> group.getName().trim().toLowerCase(),
                        Function.identity(), (first, second) -> first));

        List<CreatedRow> rows = new ArrayList<>();
        for (MatchRow row : missing) {
            List<String> names = compositions.getOrDefault(row.source(), List.of());
            Set<Group> members = new LinkedHashSet<>();
            List<String> lost = new ArrayList<>();
            for (String name : names) {
                Group group = groupsByName.get(name.trim().toLowerCase());
                if (group == null) {
                    lost.add(name);
                } else {
                    members.add(group);
                }
            }

            if (members.isEmpty() || !lost.isEmpty()) {
                // Поток без всех своих групп — это не поток, а полупустая запись, на которую потом
                // сошлются занятия. Лучше пропустить и назвать причину.
                rows.add(new CreatedRow(row.source(), null, null,
                        "нет групп: " + String.join(", ", lost.isEmpty() ? names : lost)));
                continue;
            }

            StudyStream stream = new StudyStream();
            stream.setName(row.source());
            stream.setGroups(members);
            stream.setSemester(semesterOf(members, periodYear));

            StudyStream saved = streamRepository.save(stream);
            rows.add(new CreatedRow(row.source(), saved.getId(), saved.getName(),
                    members.size() == 1 ? "поток из одной группы" : "групп: " + members.size()));
        }
        return section("Потоки", rows);
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
            Discipline saved = disciplineRepository.save(new Discipline(name, row.source()));
            rows.add(new CreatedRow(row.source(), saved.getId(), saved.getName(), null));
        }
        return section("Дисциплины", rows);
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
        List<CreatedRow> rows = new ArrayList<>();
        if (settings.locationId() == null) {
            for (MatchRow row : missing) {
                rows.add(new CreatedRow(row.source(), null, null,
                        "локация не выбрана — в каком кампусе заводить комнату, неизвестно"));
            }
            return section("Аудитории", rows);
        }

        Optional<Location> location = locationRepository.findById(settings.locationId());
        if (location.isEmpty()) {
            for (MatchRow row : missing) {
                rows.add(new CreatedRow(row.source(), null, null, "выбранной локации больше нет"));
            }
            return section("Аудитории", rows);
        }

        for (MatchRow row : missing) {
            RoomNumber decoded = RoomNumberDecoder.decode(row.source());
            if (decoded == null) {
                continue;
            }
            if (decoded.buildingUnknown()) {
                // «Сп. зал» — законное имя без корпуса (И-16), но завести его вслепую нельзя:
                // корпус обязателен, а какой именно — файл не говорит.
                rows.add(new CreatedRow(row.source(), null, null,
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

            Auditorium saved = auditoriumRepository.save(room);
            rows.add(new CreatedRow(row.source(), saved.getId(),
                    saved.getName() + " (корпус " + building.getName() + ")",
                    groups > 0
                            ? "вместимость " + capacity + " — по факту до " + groups
                            + " групп(ы) одновременно × " + settings.defaultGroupSize() + "; уточнить"
                            : "вместимость по умолчанию (занятий в этой комнате в файлах нет) — уточнить"));
        }
        return section("Аудитории", rows);
    }

    // =======================================================================

    /**
     * Подразделение по краткому имени — и только если оно **одно**.
     *
     * <p>Несколько одноимённых значит, что выбирать должен человек: приписать группу не к той
     * кафедре — ошибка, которую потом не увидеть, расписание будет выглядеть нормально.</p>
     */
    private Optional<OrgUnit> findUnique(String shortName) {
        if (shortName == null || shortName.isBlank()) {
            return Optional.empty();
        }
        // Тот же ключ, что в сверке: «51» из номера группы и «51 кафедра» в базе — одно и то же.
        String wanted = ImportMatchingService.orgUnitKey(shortName);
        List<OrgUnit> found = orgUnitRepository.findAll().stream()
                .filter(unit -> wanted.equals(ImportMatchingService.orgUnitKey(unit.getShortName()))
                        || wanted.equals(ImportMatchingService.orgUnitKey(unit.getName())))
                .toList();
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
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
