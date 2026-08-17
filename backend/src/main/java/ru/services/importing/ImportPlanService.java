package ru.services.importing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Assignment;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.StudyPeriod;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.StudyStream;
import ru.entity.logicSchema.ThemeLesson;
import ru.enums.KindOfStudy;
import ru.enums.PeriodType;
import ru.repository.AssignmentRepository;
import ru.repository.CurriculumSlotRepository;
import ru.repository.DisciplineCourseRepository;
import ru.repository.DisciplineRepository;
import ru.repository.EducatorRepository;
import ru.repository.GroupRepository;
import ru.repository.StudyPeriodRepository;
import ru.repository.SpecialRankRepository;
import ru.repository.StudyStreamRepository;
import ru.repository.ThemeLessonRepository;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.PlanReport.PlanRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Разрешение сведённых занятий в цепочку учебного плана: расчёт и запись <b>одним проходом</b>.
 *
 * <h2>Что значит «разрешить занятие»</h2>
 * <p>Размещение ссылается не на дисциплину и не на группу, а на {@code assignment} — то есть на пару
 * «слот плана × поток». Значит строка файла становится расписанием только пройдя четыре уровня:</p>
 * <pre>
 * дисциплина + период + семестр → discipline_course
 * номер темы                    → theme_lesson
 * вид занятия + позиция         → curriculum_slot
 * слот + поток + преподаватели  → assignment
 * </pre>
 *
 * <p>Чего в базе нет — импорт заведёт (И-18), но <b>сначала отчёт</b>: правило И-10 тут жёстче, чем
 * со справочниками, потому что каждая заведённая строка окажется под ссылкой размещения и станет
 * неудаляемой.</p>
 *
 * <h2>Почему расчёт и запись — один код, а не два</h2>
 * <p>{@link #preview} и {@link #create} идут <b>одним и тем же проходом</b>, отличаясь единственным
 * флагом. Иначе отчёт «будет заведено N курсов» и фактическое заведение — два описания одного
 * правила, и они разойдутся при первой же правке: человек подтвердит одно, а получит другое. Ровно
 * так уже расходились «та же дисциплина» в сверке и склейке, пока у правила не появился один
 * владелец ({@code DisciplineDictionary}).</p>
 *
 * <h2>Три места, где мы считаем, а не читаем</h2>
 * <ul>
 *   <li><b>Семестр</b> — {@code 2 × (год периода − год набора) + (осенний ? 1 : 2)} (И-18). Выгрузка
 *       семестра не несёт, а ключ курса без него неоднозначен. Год набора берётся у групп потока;
 *       разные наборы в сводном потоке — законно, берём наименьший семестр и не делаем вид, что
 *       знаем точнее.</li>
 *   <li><b>Позиция слота</b> — из <b>номера темы</b> (И-20), а не из хронологии: практика уезжает
 *       вперёд или отстаёт, и порядок в календаре плану не равен. Где темы в ячейке нет (занятие
 *       пришло из аудиторного или преподавательского разреза), остаётся запасной путь — хронология
 *       внутри своего вида, и такие позиции считаются отдельно.</li>
 *   <li><b>Вид занятия</b> — через {@link LessonKindDictionary}: сначала наша аббревиатура
 *       ({@code «Л»} → лекция), затем сообщённые заказчиком расхождения ({@code «П»} →
 *       практическое занятие). Неизвестный код остаётся строкой отчёта, а не поводом подставить
 *       «самый вероятный» вид.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportPlanService {

    private final StudyPeriodRepository periodRepository;
    private final DisciplineRepository disciplineRepository;
    private final EducatorRepository educatorRepository;
    private final GroupRepository groupRepository;
    private final StudyStreamRepository streamRepository;
    private final DisciplineCourseRepository courseRepository;
    private final ThemeLessonRepository themeRepository;
    private final CurriculumSlotRepository slotRepository;
    private final AssignmentRepository assignmentRepository;
    private final SpecialRankRepository specialRankRepository;
    private final ImportMergeService mergeService;

    /** Сколько разрешённых занятий показать «на глаз». */
    private static final int SAMPLE_SIZE = 20;

    /** Семестр коротких курсов 11 факультета: обучение короче семестра, дальше первого не заходит. */
    private static final int SHORT_COURSE_SEMESTER = 1;

    /** Метка «семестр по этой группе не выводится»: {@code int}-поток {@code null} не носит. */
    private static final int UNKNOWN = Integer.MIN_VALUE;

    /**
     * Метка «однофамильцев несколько» в индексе преподавателей.
     *
     * <p>Пустая сущность, а не {@code null}: отсутствие ключа значит «такого человека нет вовсе», и
     * это другая новость, чем «их двое». Обе ведут в блокеры, но с разным текстом.</p>
     */
    private static final Educator AMBIGUOUS_EDUCATOR = new Educator();

    /**
     * Считает, во что превратится выгрузка. <b>Ничего не создаёт.</b>
     *
     * @param sheets разобранные файлы
     * @param periodId период импорта — <b>обязателен</b>: без него не вычислить семестр, а значит и
     *                 курс, к которому крепится слот
     * @param style    написание номера группы
     */
    @Transactional(readOnly = true)
    public PlanReport preview(List<ParsedSheet> sheets, Integer periodId, SuffixStyle style) {
        return run(sheets, periodId, style, false).report();
    }

    /**
     * Заводит план по тому же расчёту. <b>Расписания не касается</b> — размещения отдельным шагом.
     *
     * <p>Порядок внутри прохода — по зависимостям: курс → тема → слот → назначение. Каждый уровень
     * кладётся в тот же индекс, из которого читает следующее занятие, поэтому сотня лекций одного
     * курса заводит один курс, а не сотню.</p>
     *
     * <p><b>Повторный запуск безопасен:</b> заведённое находится по тем же ключам и считается
     * «уже есть». Это не оптимизация, а условие работы: первый прогон заведомо неполон, и второй
     * должен дозаводить, а не дублировать.</p>
     *
     * <p>⚠️ Что <b>не</b> заводится: занятия из списка блокеров. Занятие без преподавателя, без вида
     * или без года набора не разрешается в {@code assignment}, и подставлять «самое вероятное»
     * нельзя — ошибка станет неотличима от данных.</p>
     */
    @Transactional
    public PlanReport create(List<ParsedSheet> sheets, Integer periodId, SuffixStyle style) {
        return run(sheets, periodId, style, true).report();
    }

    /**
     * Тот же проход, но наружу отдаются ещё и <b>разрешённые занятия</b> — вход шага размещений.
     *
     * <p>Отдельного разрешения «занятие → назначение» для записи расписания не заводим: оно бы
     * повторяло это и разошлось с ним при первой правке. Размещение обязано встать на то самое
     * назначение, которое посчитал и завёл план, — иначе отчёт показывает одно, а в расписании
     * оказывается другое.</p>
     *
     * <p>Транзакция общая с вызывающим: назначения создаются здесь, а размещения ссылаются на них
     * там. Половина такой пары, доехавшая до базы без второй, — это план без расписания либо
     * ссылка в никуда.</p>
     */
    @Transactional
    public PlanResolution createForSchedule(List<ParsedSheet> sheets, Integer periodId, SuffixStyle style) {
        return run(sheets, periodId, style, true);
    }

    /**
     * Итог прохода: отчёт и разрешённые занятия.
     *
     * @param report   то же, что видит человек в предпросмотре
     * @param resolved занятия, доведённые до назначения; пусто при расчёте без записи — назначения
     *                 ещё нет, и ссылаться размещению не на что
     */
    public record PlanResolution(PlanReport report, List<ResolvedLesson> resolved) {
    }

    /** Сведённое занятие и назначение, на которое оно встанет. */
    public record ResolvedLesson(MergedLesson lesson, Assignment assignment) {
    }

    private PlanResolution run(List<ParsedSheet> sheets, Integer periodId, SuffixStyle style, boolean apply) {
        if (periodId == null) {
            // Не NPE: для того, кто запускает импорт, это обычная ошибка запроса — период забыли
            // выбрать. Семестр курса считается от него, и подставить «активный» было бы догадкой.
            throw new IllegalArgumentException("Период обязателен: без него не вычислить семестр курса");
        }
        StudyPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Периода с id " + periodId + " нет"));

        List<MergedLesson> lessons = ScheduleMerger
                .merge(sheets, style, mergeService.bounds(periodId))
                .lessons();

        Resolution resolution = new Resolution(period, index(), apply);
        lessons.forEach(resolution::resolve);
        PlanReport report = resolution.report(lessons.size());

        log.info("План ({}): разрешено {} из {}; курсов {}, тем {}, слотов {}, назначений {}",
                apply ? "заведено" : "расчёт", report.resolved(), report.lessons(),
                report.coursesToCreate(), report.themesToCreate(),
                report.slotsToCreate(), report.assignmentsToCreate());
        return new PlanResolution(report, List.copyOf(resolution.resolvedLessons));
    }

    // =======================================================================

    /** Снимок базы: всё, что нужно для разрешения, читается один раз. */
    private Index index() {
        Index index = new Index();
        index.ranks = specialRankRepository.findAll().stream()
                .map(rank -> rank.getShortName()).filter(Objects::nonNull).toList();
        for (Educator educator : educatorRepository.findAll()) {
            String key = EducatorNameDecoder.keyOfStoredName(educator.getName(), index.ranks);
            if (key != null) {
                // Однофамильцы с теми же инициалами: первого попавшегося не берём — при разрешении
                // такое занятие уйдёт в блокеры, потому что выбрать за человека нельзя (И-10).
                index.educators.merge(key, educator, (first, second) -> AMBIGUOUS_EDUCATOR);
            }
        }
        for (Discipline discipline : disciplineRepository.findAll()) {
            index.disciplinesByAbbreviation.putIfAbsent(key(discipline.getAbbreviation()), discipline);
            index.disciplinesByName.putIfAbsent(key(discipline.getName()), discipline);
        }
        for (Group group : groupRepository.findAll()) {
            index.groups.putIfAbsent(GroupNumberDecoder.key(group.getName()), group);
        }
        for (StudyStream stream : streamRepository.findAll()) {
            index.streams.putIfAbsent(compositionKey(stream.getGroups()), stream);
        }
        for (DisciplineCourse course : courseRepository.findAll()) {
            index.courses.putIfAbsent(course.getDiscipline().getId() + "|" + course.getStudyPeriod().getId()
                    + "|" + course.getSemester(), course);
        }
        for (ThemeLesson theme : themeRepository.findAll()) {
            // Ключ по НОМЕРУ, а не по написанию: в ячейке тема стоит как «Т.4», а у нас хранится «4».
            // Пока ключи не сходились, существующая тема каждый раз считалась новой.
            index.themes.putIfAbsent(theme.getDiscipline().getId() + "|" + themeKey(theme.getThemeNumber()), theme);
        }
        for (CurriculumSlot slot : slotRepository.findAll()) {
            // Идентичность слота — «курс · вид · тема», а НЕ позиция: позиция это порядковый номер
            // в курсе, и по нему нельзя узнать, тот ли это слот. Тема у слота может отсутствовать
            // (занятие пришло без неё) — тогда опорой остаётся позиция.
            index.slots.putIfAbsent(slotKey(slot.getDisciplineCourse().getId(), slot.getKindOfStudy(),
                    slot.getThemeLesson() == null ? null : themeKey(slot.getThemeLesson().getThemeNumber()),
                    slot.getPosition()), slot);
            DisciplineCourse owner = slot.getDisciplineCourse();
            index.lastPosition.merge(owner.getDiscipline().getId() + "|" + owner.getStudyPeriod().getId()
                    + "|" + owner.getSemester(), slot.getPosition(), Math::max);
        }
        for (Assignment assignment : assignmentRepository.findAll()) {
            index.assignments.putIfAbsent(
                    assignment.getCurriculumSlot().getId() + "|" + assignment.getStudyStream().getId(), assignment);
        }
        return index;
    }

    /** Снимок справочников и плана, по которому идёт разрешение. */
    private static final class Index {
        private List<String> ranks = List.of();
        private final Map<String, Educator> educators = new HashMap<>();
        private final Map<String, Discipline> disciplinesByAbbreviation = new HashMap<>();
        private final Map<String, Discipline> disciplinesByName = new HashMap<>();
        private final Map<String, Group> groups = new HashMap<>();
        private final Map<String, StudyStream> streams = new HashMap<>();
        private final Map<String, DisciplineCourse> courses = new HashMap<>();
        private final Map<String, ThemeLesson> themes = new HashMap<>();
        private final Map<String, CurriculumSlot> slots = new HashMap<>();
        /**
         * Последняя занятая позиция в курсе: с неё продолжается нумерация новых слотов.
         *
         * <p>Ключ — <b>ключ курса</b>, а не его id: в расчёте курса ещё нет, а позицию отчёт обязан
         * показать. Иначе «посчитать» и «завести» разойдутся в числах.</p>
         */
        private final Map<String, Integer> lastPosition = new HashMap<>();
        /**
         * «слот|поток» → назначение. Именно сущность, а не признак наличия: на неё сошлётся
         * размещение шага записи, и брать её вторым запросом значило бы завести второе разрешение.
         */
        private final Map<String, Assignment> assignments = new LinkedHashMap<>();
    }

    /**
     * Проход по сведённым занятиям: что уже есть, что заведём, что не разрешилось.
     *
     * <p>Считает <b>различные</b> сущности, а не занятия: сотня лекций одного курса даёт один курс.
     * Поэтому всё, что «будет заведено», копится множествами ключей.</p>
     */
    private final class Resolution {

        private final StudyPeriod period;
        private final Index index;
        /** {@code false} — только считаем; {@code true} — тем же проходом заводим. */
        private final boolean apply;

        private final Set<String> newCourses = new LinkedHashSet<>();
        private final Set<String> newThemes = new LinkedHashSet<>();
        private final Set<String> newSlots = new LinkedHashSet<>();
        private final Set<String> newAssignments = new LinkedHashSet<>();
        private final Set<String> usedCourses = new LinkedHashSet<>();
        private final Set<String> usedSlots = new LinkedHashSet<>();
        private final Set<String> usedAssignments = new LinkedHashSet<>();

        /** Позиции, выданные в этом прогоне: (курс · вид · тема) → позиция. Нужны для повторов. */
        private final Map<String, Integer> positionsWithoutTheme = new HashMap<>();
        private final Findings blockers = new Findings();
        private final List<PlanRow> sample = new ArrayList<>();
        /** Занятия, доведённые до назначения, — вход шага размещений. */
        private final List<ResolvedLesson> resolvedLessons = new ArrayList<>();

        private int resolved;
        private int byTheme;
        private int byOrder;

        private Resolution(StudyPeriod period, Index index, boolean apply) {
            this.period = period;
            this.index = index;
            this.apply = apply;
        }

        private void resolve(MergedLesson lesson) {
            Discipline discipline = discipline(lesson);
            if (discipline == null) {
                blockers.add("дисциплина «" + lesson.discipline() + "» не заведена — сначала «Завести»", lesson);
                return;
            }
            KindOfStudy kind = LessonKindDictionary.of(lesson.kind());
            if (kind == null) {
                blockers.add(lesson.kind() == null
                        ? "вид занятия неизвестен: занятие видно только у преподавателя"
                        : "вид «" + lesson.kind() + "» не сопоставлен ни с одним нашим видом", lesson);
                return;
            }
            StudyStream stream = index.streams.get(compositionKeyOf(lesson.groups()));
            if (stream == null) {
                blockers.add("поток на состав «" + String.join("+", lesson.groups()) + "» не заведён", lesson);
                return;
            }
            Integer semester = semester(stream);
            if (semester == null) {
                blockers.add("год набора групп неизвестен — семестр курса не вычислить", lesson);
                return;
            }
            if (lesson.educators().isEmpty()) {
                blockers.add("преподаватель не определён — назначение не к кому привязать", lesson);
                return;
            }
            // Назначение ссылается на СУЩНОСТИ преподавателей, а не на подписи из файла: без
            // разрешения строку не записать, поэтому проверяем это до всякого создания.
            List<Educator> educators = educators(lesson);
            if (educators == null) {
                return;
            }

            String courseKey = discipline.getId() + "|" + period.getId() + "|" + semester;
            DisciplineCourse course = index.courses.get(courseKey);
            boolean newCourse = course == null;
            if (newCourse) {
                newCourses.add(courseKey);
                if (apply) {
                    course = writeCourse(discipline, semester);
                    index.courses.put(courseKey, course);
                }
            } else {
                usedCourses.add(courseKey);
            }

            ThemeLesson theme = theme(discipline, lesson);
            String themeNumber = themeNumber(lesson.theme()) == null
                    ? null : String.valueOf(themeNumber(lesson.theme()));
            countPositionSource(themeNumber != null);

            // Слот ищем по теме, а не по позиции: позиция уникальна в курсе БЕЗ вида занятия, и
            // «тема 4» у лекции и у лабораторной — два разных слота с разными позициями.
            String plannedKey = courseKey + "|" + kind + "|" + (themeNumber == null ? "?" : themeNumber);
            CurriculumSlot slot = course == null ? null
                    : index.slots.get(slotKey(course.getId(), kind, themeNumber,
                            positionsWithoutTheme.get(plannedKey)));
            boolean newSlot = slot == null;
            int position = newSlot
                    // Позиция выделяется и в расчёте, и в записи — одинаково: иначе отчёт покажет
                    // одно, а заведение сделает другое.
                    ? positionsWithoutTheme.computeIfAbsent(plannedKey, ignored -> nextPosition(courseKey))
                    : slot.getPosition();
            if (newSlot) {
                newSlots.add(plannedKey);
                if (apply) {
                    slot = writeSlot(course, position, kind, theme);
                    index.slots.put(slotKey(course.getId(), kind, themeNumber, position), slot);
                }
            } else {
                usedSlots.add(plannedKey);
            }

            String assignmentKey = (slot == null ? plannedKey : String.valueOf(slot.getId())) + "|" + stream.getId();
            Assignment assignment = slot == null ? null : index.assignments.get(slot.getId() + "|" + stream.getId());
            if (assignment != null) {
                usedAssignments.add(assignmentKey);
            } else {
                newAssignments.add(assignmentKey);
                if (apply && slot != null) {
                    assignment = writeAssignment(slot, stream, educators);
                    index.assignments.put(slot.getId() + "|" + stream.getId(), assignment);
                }
            }
            if (assignment != null) {
                // Вход шага размещений. Одно назначение может собрать НЕСКОЛЬКО занятий — сдвоенная
                // пара по одной теме это два размещения на одном назначении, и схема их допускает
                // (уникальность «одно назначение — одно размещение в сессии» снята миграцией 005).
                resolvedLessons.add(new ResolvedLesson(lesson, assignment));
            }

            resolved++;
            if (sample.size() < SAMPLE_SIZE) {
                sample.add(new PlanRow(lesson.date(), lesson.slot(), discipline.getName(), semester, kind,
                        position, lesson.theme(), stream.getName(),
                        lesson.educators(), newCourse, newSlot));
            }
        }

        /**
         * Преподаватели занятия как сущности; {@code null} — не разрешились, занятие в блокеры.
         *
         * <p>Подпись из файла («к-н Ветров Р.И. ктн») приводится к тому же ключу «фамилия +
         * инициалы», по которому сверка искала человека. Не нашли или нашли нескольких — <b>не
         * выбираем</b>: приписать занятие не тому человеку значит разрезать надвое два расписания
         * сразу, и увидеть это нечем.</p>
         */
        private List<Educator> educators(MergedLesson lesson) {
            List<Educator> found = new ArrayList<>();
            for (String signature : lesson.educators()) {
                String educatorKey = EducatorNameDecoder.decode(signature, index.ranks).key();
                Educator educator = educatorKey == null ? null : index.educators.get(educatorKey);
                if (educator == null) {
                    blockers.add("преподаватель «" + signature + "» не заведён — сначала «Завести»", lesson);
                    return null;
                }
                if (educator == AMBIGUOUS_EDUCATOR) {
                    blockers.add("однофамильцев с такими инициалами несколько («" + signature
                            + "») — выбрать должен человек", lesson);
                    return null;
                }
                found.add(educator);
            }
            return found;
        }

        /**
         * Тема занятия: найденная либо заведённая.
         *
         * <p>Заголовка темы в выгрузке нет вовсе — есть только номер, и он же становится позицией
         * слота (И-20). Поэтому {@code title} остаётся пустым: выдумывать название темы неоткуда,
         * а пустое поле честно говорит «не заполнено».</p>
         */
        private ThemeLesson theme(Discipline discipline, MergedLesson lesson) {
            Integer number = themeNumber(lesson.theme());
            if (number == null) {
                return null;
            }
            String themeIndexKey = discipline.getId() + "|" + number;
            ThemeLesson existing = index.themes.get(themeIndexKey);
            if (existing != null) {
                return existing;
            }
            newThemes.add(themeIndexKey);
            if (!apply) {
                return null;
            }
            ThemeLesson theme = new ThemeLesson();
            theme.setDiscipline(discipline);
            theme.setThemeNumber(String.valueOf(number));
            ThemeLesson saved = themeRepository.save(theme);
            index.themes.put(themeIndexKey, saved);
            return saved;
        }

        private DisciplineCourse writeCourse(Discipline discipline, int semester) {
            DisciplineCourse course = new DisciplineCourse();
            course.setDiscipline(discipline);
            course.setStudyPeriod(period);
            course.setSemester(semester);
            return courseRepository.save(course);
        }

        /**
         * Слот плана.
         *
         * <p>{@code assessmentWindow} остаётся значением по умолчанию ({@code STUDY_TIME}):
         * «Отчёт = ЭКЗ → сессия» напрашивается, но зачёт тоже бывает в сессию, и до подтверждения
         * заказчиком подставлять окно значило бы записать в план догадку. Вопрос открыт.</p>
         */
        private CurriculumSlot writeSlot(DisciplineCourse course, int position, KindOfStudy kind, ThemeLesson theme) {
            CurriculumSlot slot = new CurriculumSlot();
            slot.setDisciplineCourse(course);
            slot.setPosition(position);
            slot.setKindOfStudy(kind);
            slot.setThemeLesson(theme);
            return slotRepository.save(slot);
        }

        private Assignment writeAssignment(CurriculumSlot slot, StudyStream stream, List<Educator> educators) {
            Assignment assignment = new Assignment();
            assignment.setCurriculumSlot(slot);
            assignment.setStudyStream(stream);
            assignment.getEducators().addAll(educators);
            return assignmentRepository.save(assignment);
        }

        /**
         * Следующая свободная позиция в курсе.
         *
         * <p><b>Позиция — порядковый номер слота в курсе, а не номер темы.</b> Так требует схема:
         * {@code UNIQUE (discipline_course_id, position)} <b>без вида занятия</b>, а тема 4 законно
         * читается лекцией и отрабатывается лабораторной — это два слота, и номер темы у них общий.
         * И-20 при этом не отменяется: тема задаёт <b>порядок</b> (занятия приходят от склейки по
         * возрастанию даты, а слот заводится при первой встрече темы), а не само число. Куда именно
         * села тема, видно по ссылке слота на неё.</p>
         */
        private int nextPosition(String courseKey) {
            return index.lastPosition.merge(courseKey, 1, (current, ignored) -> current + 1);
        }

        /** Откуда взялась опора для слота: тема (правило И-20) или её отсутствие. */
        private void countPositionSource(boolean fromTheme) {
            if (fromTheme) {
                byTheme++;
            } else {
                byOrder++;
            }
        }

        /** «Т.4» и «4» → 4; всё остальное — не номер темы. */
        private Integer themeNumber(String theme) {
            if (theme == null) {
                return null;
            }
            String digits = theme.replaceAll("\\D", "");
            return digits.isEmpty() ? null : Integer.parseInt(digits);
        }

        /**
         * Семестр курса по году набора групп потока.
         *
         * <p>Наименьший из возможных: в сводном потоке законно встречаются разные наборы, и
         * выдумывать «правильный» год не за что.</p>
         *
         * <p><b>Короткие курсы считаются первым семестром</b> (11 факультет, правило заказчика
         * 2026-08-17). Года набора в их номере нет вовсе, и это не пробел в данных, а свойство
         * обучения: оно длится от двух недель до трёх месяцев, то есть дальше первого семестра не
         * заходит. Раньше такие занятия целиком уходили в блокеры.</p>
         */
        private Integer semester(StudyStream stream) {
            int shift = period.getPeriodType() == PeriodType.FALL_SEMESTER
                    || period.getPeriodType() == PeriodType.FALL_EXAM_SESSION ? 1 : 2;
            return stream.getGroups().stream()
                    .mapToInt(group -> group.getEnrollmentYear() != null
                            ? Math.max(1, (period.getStudyYear() - group.getEnrollmentYear()) * 2 + shift)
                            : GroupNumberDecoder.isShortCourse(group.getName()) ? SHORT_COURSE_SEMESTER : UNKNOWN)
                    .filter(semester -> semester != UNKNOWN)
                    .min()
                    .stream().boxed().findFirst().orElse(null);
        }

        private Discipline discipline(MergedLesson lesson) {
            Discipline found = index.disciplinesByAbbreviation.get(key(lesson.discipline()));
            if (found == null && lesson.disciplineName() != null) {
                found = index.disciplinesByName.get(key(lesson.disciplineName()));
            }
            return found;
        }

        private String compositionKeyOf(List<String> groups) {
            return groups.stream().map(GroupNumberDecoder::key).distinct().sorted()
                    .reduce((left, right) -> left + "+" + right).orElse("");
        }

        private PlanReport report(int lessons) {
            return new PlanReport(lessons, resolved,
                    newCourses.size(), newThemes.size(), newSlots.size(), newAssignments.size(),
                    usedCourses.size(), usedSlots.size(), usedAssignments.size(),
                    byTheme, byOrder, blockers.top(), List.copyOf(sample));
        }
    }

    private static String compositionKey(Set<Group> groups) {
        return groups.stream().map(group -> GroupNumberDecoder.key(group.getName())).distinct().sorted()
                .reduce((left, right) -> left + "+" + right).orElse("");
    }

    /**
     * Ключ слота: курс · вид · <b>тема</b>, а где темы нет — курс · вид · позиция.
     *
     * <p>Позиция в ключ обычно не входит намеренно: она порядковый номер в курсе и уникальна по
     * схеме <b>без</b> вида занятия ({@code UNIQUE (discipline_course_id, position)}), поэтому
     * «тема 4» у лекции и у лабораторной — это два разных слота с разными позициями. Опознать их
     * можно только по теме.</p>
     */
    private static String slotKey(Integer courseId, KindOfStudy kind, String theme, Integer position) {
        return courseId + "|" + kind + "|" + (theme == null ? "поз." + position : "тема " + theme);
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Ключ темы — её НОМЕР, а не написание.
     *
     * <p>В ячейке тема стоит как «Т.4», у нас хранится «4». Пока обе стороны не сводились к номеру,
     * существующая тема каждый раз считалась новой — и отчёт обещал завести то, что уже есть.</p>
     */
    private static String themeKey(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        return digits.isEmpty() ? key(value) : digits;
    }

    /** Причины, по которым занятие не разрешилось: текст → число и занятие-образец. */
    private static final class Findings {
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, String> examples = new LinkedHashMap<>();

        private void add(String message, MergedLesson lesson) {
            counts.merge(message, 1, Integer::sum);
            examples.putIfAbsent(message, lesson.date() + " " + lesson.slot() + " · "
                    + lesson.discipline() + " · " + String.join(", ", lesson.groups())
                    + (lesson.files().isEmpty() ? "" : " · " + lesson.files().get(0)));
        }

        private List<MergeReport.Finding> top() {
            List<MergeReport.Finding> found = new ArrayList<>();
            counts.forEach((message, count) ->
                    found.add(new MergeReport.Finding(message, count, examples.get(message))));
            found.sort(Comparator.comparingInt(MergeReport.Finding::count).reversed());
            return List.copyOf(found);
        }
    }
}
