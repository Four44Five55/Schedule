package ru.services.importing;

import ru.enums.TimeSlotPair;
import ru.services.importing.CellDialect.LessonEntry;
import ru.services.importing.DisciplineFooterParser.FooterRow;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ParsedSheet.CutKind;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сведение трёх разрезов в один список занятий.
 *
 * <h2>Зачем</h2>
 * <p>Ни один разрез не полон, и не полон он <b>по-своему</b>: в преподавательской ячейке нет вида
 * занятия, в аудиторной — темы, в групповой — преподавателя, а часть занятий групповой разрез
 * теряет вовсе (маркер «ЭкзС» печатается поверх занятия, и занятия за границей периода в полотно
 * группы не попадают — И-21). Пока файлы читаются порознь, ни одна строка не разрешается в
 * {@code Assignment}: у дисциплины назначений несколько, и без преподавателя выбрать не из чего.</p>
 *
 * <h2>Сведение в два шага: дата · пара · дисциплина, а внутри — по пересечению</h2>
 * <p>Первый шаг собирает <b>кластер</b>: всё, что в одну дату на одной паре сказано об одной
 * дисциплине. Ровно так §9 спецификации предписывает восстанавливать <b>потоки</b> — одна лекция,
 * увиденная из файлов трёх групп, это одно занятие.</p>
 *
 * <p><b>Но кластер — не занятие.</b> В то же время по той же дисциплине рядом законно идёт
 * <b>другое</b> занятие: у другой группы, с другим преподавателем, в другой комнате (полупотоки,
 * И-19; или просто две несвязанные группы, у которых предмет совпал по расписанию). Пока кластер
 * выдавался за одно занятие, такие занятия склеивались в поток, которого нет, — и шаг плана честно
 * упирался в «поток на состав „1+33“ не заведён», потому что заводить его было не за что.</p>
 *
 * <h2>Что разводит параллельные занятия — физика, а не догадка</h2>
 * <p><b>Группа и преподаватель в одно время могут быть только в одном занятии.</b> Значит две записи
 * кластера, у которых совпала хоть одна из этих опор, говорят об одном занятии, а записи, не
 * связанные ничем, — о разных. Кластер разбивается на компоненты связности по опорам, и каждая
 * компонента становится занятием.</p>
 *
 * <p><b>Комната — опора слабее двух других, и это не оговорка, а свойство помещений.</b> Большое
 * вмещает несколько занятий разом: в «Сп. зале» физподготовка идёт у нескольких потоков сразу, и у
 * каждого свой преподаватель. Поэтому комната работает опорой, только пока в этот час её не делят
 * <b>разные</b> ведущие; спорную комнату сведение не использует вовсе (см.
 * {@code Cluster#contestedRooms()}). Признак берётся из самих файлов, а не из списка «залов»:
 * перечень больших помещений пришлось бы вести руками, и устарел бы он молча.</p>
 *
 * <p>Случаи, ради которых правило и написано:</p>
 * <ul>
 *   <li><b>поток</b> — три групповых файла показывают одну комнату: связаны комнатой, занятие одно
 *       (§9 не нарушается);</li>
 *   <li><b>полупотоки</b> — разные группы, разные комнаты, разные преподаватели: не связаны ничем,
 *       занятий два. Именно это раньше слипалось;</li>
 *   <li><b>общий зал</b> — комната одна и та же, но преподаватели разные: занятий столько, сколько
 *       ведущих. Групповые файлы пристают к своему через <b>группу</b>, а не через зал;</li>
 *   <li><b>двое ведут вместе</b> — комната та же и <b>группа та же</b>: занятие одно. Разница с
 *       предыдущим случаем именно в составе слушателей, а не в комнате;</li>
 *   <li><b>занятие, поделённое по кабинетам</b> (§9) — комнаты разные, но преподавательский файл
 *       показывает в своей ячейке обе группы: связаны преподавателем, занятие одно. А если такого
 *       файла в комплекте нет, связи не видно вовсе — и разводить честнее, чем склеивать: выдуманный
 *       поток тише и вреднее, чем лишнее занятие.</li>
 * </ul>
 *
 * <p>Разведённые занятия считаются отдельным числом отчёта ({@link MergeReport#parallelSplit()}) —
 * это и есть ответ на вопрос И-19 «сколько у нас полупотоков», который до сих пор оценивался
 * подозрением. Подозрением остаётся только <b>двое ведущих на одном занятии</b>
 * ({@link MergedLesson#coTaught()}): их связывает общая комната или общая группа, то есть они
 * действительно об одном занятии, а ведут вместе или это ошибка комплекта — решает человек.</p>
 *
 * <h2>Преподаватель: разрез главнее подвала</h2>
 * <p><b>Ведёт занятие тот, у кого оно стоит поклеточно в собственном файле.</b> Подвал группового
 * файла — это перечень <b>кандидатов</b> по дисциплине, а не свидетельство о занятии: часть
 * перечисленных не ведёт вовсе (запасные, И-22), и «ФП» с тремя подписями на один вид занятия —
 * обычный случай, а не кривизна.</p>
 *
 * <p>Отсюда порядок: подвал применяется <b>только там, где преподавательского разреза не нашлось</b>,
 * и только пока кандидат один (правило И-14: {@code Л} к лектору, {@code ЛР}/{@code ПЗ} к практику).
 * Складывать разрез с подвалом нельзя — на занятии оказались бы двое, из которых второй его не ведёт
 * ({@link MergedLesson#coTaught()} насчитал бы несуществующую пару ведущих).</p>
 *
 * <p>Поэтому и находка о нескольких кандидатах предъявляется <b>по занятию, а не по ячейке</b>:
 * вопрос не «сколько подписей в подвале» (их сотни строк на дисциплину), а «у скольких занятий
 * ведущий так и не найден».</p>
 *
 * <p><b>Чистая функция</b>: ни Spring, ни базы, ни того, что у нас заведено. Сведение — про файлы;
 * разрешение в наши сущности — следующий слой.</p>
 */
public final class ScheduleMerger {

    private ScheduleMerger() {
    }

    /** Сколько занятий показать «на глаз». */
    private static final int SAMPLE_SIZE = 20;

    /** Сколько разных находок отдавать: остальное — хвост, который никто не читает. */
    private static final int FINDINGS_LIMIT = 50;

    /**
     * Границы периода импорта — то, чего в файле нет и что называет человек.
     *
     * @param studyYear первый год учебного года периода (2025 для 2025/2026) либо {@code null}
     * @param from      начало периода; {@code null} — период не выбран, выбросы не считаем
     * @param to        конец периода
     */
    public record PeriodBounds(Integer studyYear, LocalDate from, LocalDate to) {

        public boolean covers(LocalDate date) {
            return from == null || to == null || (!date.isBefore(from) && !date.isAfter(to));
        }
    }

    /**
     * Сведённое расписание: занятия и отчёт о том, чего им не хватает.
     *
     * @param lessons занятия целиком — вход следующего шага (в API уезжает только отчёт: на живом
     *                объёме занятий десятки тысяч)
     */
    public record MergedSchedule(List<MergedLesson> lessons, MergeReport report) {
    }

    /**
     * Сводит разобранные файлы.
     *
     * @param sheets разобранные файлы любых разрезов
     * @param style  написание номера группы для наших имён (И: «101/1» и «101-1» — одна группа)
     * @param period границы периода; {@code null} — период не выбран
     */
    public static MergedSchedule merge(List<ParsedSheet> sheets, SuffixStyle style, PeriodBounds period) {
        DisciplineDictionary dictionary = DisciplineDictionary.of(sheets);
        Findings findings = new Findings();
        Map<String, Cluster> clusters = new LinkedHashMap<>();

        int entries = 0;
        int undated = 0;

        for (ParsedSheet sheet : sheets) {
            checkPeriodYear(sheet, period, findings);
            // Подвал берётся у СВОЕГО файла, а не из словаря: лектор у одной дисциплины в разных
            // группах разный, и общий свод подвалов приписал бы занятию чужого человека.
            Map<String, FooterRow> footer = footerOf(sheet);

            for (LessonEntry entry : CellDialect.readAll(sheet)) {
                entries++;
                String discipline = dictionary.canonicalOf(entry.discipline());
                if (discipline == null) {
                    findings.add("в ячейке нет дисциплины — занятие не сводится", sheet.sourceName());
                    continue;
                }
                if (entry.date() == null) {
                    // Дату не восстановили — привязать занятие не к чему. Это находка разбора, а не
                    // сведения, но потерять её молча нельзя: занятие просто исчезнет.
                    undated++;
                    findings.add("дата ячейки не восстановлена — занятие не сводится", sheet.sourceName());
                    continue;
                }

                clusters.computeIfAbsent(
                                key(entry.date(), entry.slot(), discipline),
                                ignored -> new Cluster(entry.date(), entry.slot(), discipline,
                                        dictionary.nameOf(discipline)))
                        .add(new Occurrence(entry, sheet, footer.get(lower(entry.discipline()))));
            }
        }

        List<MergedLesson> collected = new ArrayList<>();
        int parallelSplit = 0;
        for (Cluster cluster : clusters.values()) {
            List<List<Occurrence>> parallel = cluster.split();
            if (parallel.size() > 1) {
                // Не «проблема», а состояние расписания (И-19), но названное: человек должен знать,
                // что под одной дисциплиной в одно время шло несколько занятий, а не поток.
                parallelSplit += parallel.size();
                findings.add("одна дисциплина в одно время идёт несколькими занятиями — разведены "
                        + "по преподавателю, комнате и составу групп (полупотоки, И-19)", cluster.anyFile());
            }
            parallel.forEach(part -> collected.add(cluster.draft(part, style).build(period, findings)));
        }

        List<MergedLesson> lessons = collected.stream()
                .sorted(Comparator.comparing(MergedLesson::date).thenComparing(MergedLesson::slot))
                .toList();

        return new MergedSchedule(lessons, report(lessons, entries, undated, parallelSplit, findings));
    }

    // =======================================================================

    /**
     * Копит преподавателей занятия: из разреза — сразу, из подвала — <b>про запас</b>.
     *
     * <p>Ничего не решается здесь: у одного занятия файлов несколько, и пока не прочитаны все, не
     * известно, назовёт ли ведущего преподавательский разрез. Выбор между разрезом и подвалом делает
     * {@link Draft#build}, когда занятие собрано целиком.</p>
     */
    private static void collectEducator(Draft draft, Occurrence occurrence) {
        LessonEntry entry = occurrence.entry();
        if (entry.educator() != null && !entry.educator().isBlank()) {
            draft.educators.add(entry.educator().trim());
            return;
        }
        if (occurrence.sheet().header().kind() != CutKind.GROUP || entry.kind() == null) {
            return;
        }

        FooterRow row = occurrence.footer();
        if (row == null) {
            return;
        }
        List<String> candidates = toLecturer(entry.kind()) ? row.lecturers() : row.practicians();
        if (candidates.size() == 1) {
            draft.footerEducators.add(candidates.get(0).trim());
        } else if (candidates.size() > 1) {
            // Подвал этих двоих (троих…) не различает. Запоминаем число, но находку откладываем:
            // если ведущего назовёт преподавательский файл, находки не будет вовсе.
            draft.footerCandidates = Math.max(draft.footerCandidates, candidates.size());
        }
    }

    /**
     * Из какой колонки подвала брать кандидата: лектора или практиков.
     *
     * <p><b>Лекцию и аттестацию ведёт лектор.</b> Первое — по определению, второе — по смыслу дела:
     * экзамен принимает тот, кто читал курс. Пока аттестация уходила к практикам, экзамены
     * оставались без преподавателя там, где практиков в подвале несколько.</p>
     *
     * <p>Своего правила по первой букве обозначения здесь больше нет: вид опознаёт
     * {@link LessonKindDictionary}, а «лекция это или аттестация» объявляет сам
     * {@link ru.enums.KindOfStudy.Category} — единственный владелец классификации. Прежнее «Л…, но
     * не ЛР…» было третьей копией того же знания и на «П» уже расходилось с расчётом плана.</p>
     */
    private static boolean toLecturer(String kind) {
        return LessonKindDictionary.isLecture(kind) || LessonKindDictionary.isAssessment(kind);
    }

    /**
     * Подвал одного файла: обозначение <b>и полное название</b> → строка.
     *
     * <p>Названием тоже, потому что в ячейке дисциплина иногда написана полностью — так выгрузка
     * печатает экзамены. Пока входом служило одно обозначение, у таких занятий подвал не находился,
     * а с ним и преподаватель: занятие уходило в блокеры «преподаватель не определён» (правило
     * заказчика 2026-08-17). Владелец признака «это название, а не индекс плана» один —
     * {@link DisciplineDictionary#namable(String)}.</p>
     */
    private static Map<String, FooterRow> footerOf(ParsedSheet sheet) {
        Map<String, FooterRow> rows = new HashMap<>();
        sheet.footer().forEach(row -> {
            rows.putIfAbsent(lower(row.code()), row);
            if (DisciplineDictionary.namable(row.name())) {
                rows.putIfAbsent(lower(row.name()), row);
            }
        });
        return rows;
    }

    /**
     * Тот ли период выбран.
     *
     * <p>Побочная выгода из И-2: запусти импорт не в тот период — расписание встало бы целиком
     * сдвинутым и выглядело правдоподобно. Учебный год напечатан в шапке каждого файла, и сверить
     * его с годом периода стоит одного сравнения.</p>
     */
    private static void checkPeriodYear(ParsedSheet sheet, PeriodBounds period, Findings findings) {
        if (period == null || period.studyYear() == null || sheet.header().startYear() == null) {
            return;
        }
        if (!period.studyYear().equals(sheet.header().startYear())) {
            findings.add("файл за " + sheet.header().startYear() + "/" + (sheet.header().startYear() + 1)
                    + " учебный год, а выбран период за " + period.studyYear() + "/" + (period.studyYear() + 1)
                    + " — тот ли период?", sheet.sourceName());
        }
    }

    private static MergeReport report(List<MergedLesson> lessons, int entries, int undated,
                                      int parallelSplit, Findings findings) {
        return new MergeReport(
                lessons.size(),
                entries,
                (int) lessons.stream().filter(lesson -> lesson.cuts().contains(CutKind.GROUP)).count(),
                (int) lessons.stream().filter(lesson -> lesson.cuts().contains(CutKind.AUDITORIUM)).count(),
                (int) lessons.stream().filter(lesson -> lesson.cuts().contains(CutKind.EDUCATOR)).count(),
                (int) lessons.stream().filter(MergedLesson::missedByGroupCut).count(),
                (int) lessons.stream().filter(lesson -> lesson.kind() == null).count(),
                (int) lessons.stream().filter(lesson -> lesson.theme() == null).count(),
                (int) lessons.stream().filter(lesson -> lesson.educators().isEmpty()).count(),
                (int) lessons.stream().filter(MergedLesson::outsidePeriod).count(),
                undated,
                parallelSplit,
                (int) lessons.stream().filter(MergedLesson::coTaught).count(),
                lessons.isEmpty() ? null : lessons.get(0).date(),
                lessons.isEmpty() ? null : lessons.get(lessons.size() - 1).date(),
                findings.top(),
                lessons.stream().limit(SAMPLE_SIZE).toList());
    }

    private static String key(LocalDate date, TimeSlotPair slot, String discipline) {
        return date + "|" + slot + "|" + lower(discipline);
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Одна запись о занятии: ячейка, файл, из которого она пришла, и строка подвала по её дисциплине.
     *
     * <p>Подвал разрешается здесь, у своего файла, а не при сборке занятия: у одного занятия файлов
     * несколько, и общий свод подвалов приписал бы ему чужого человека (лектор одной дисциплины в
     * разных группах разный).</p>
     */
    private record Occurrence(LessonEntry entry, ParsedSheet sheet, FooterRow footer) {

        /**
         * Опоры, по которым записи признаются одним занятием: группа, преподаватель, комната.
         *
         * <p><b>Первые две неделимы во времени</b> — группа не сидит на двух занятиях сразу,
         * преподаватель не ведёт двух. Совпадение любой из них не догадка о родстве записей, а факт.
         * <b>Комната — опора слабая</b>: большое помещение вмещает несколько занятий разом, и живой
         * случай ровно такой — в «Сп. зале» ФП идёт у нескольких потоков сразу, у каждого свой
         * преподаватель. Поэтому комнату {@link Cluster#split()} использует не всегда.</p>
         *
         * <p>Номер группы приводится к ключу ({@code «101/1»} и {@code «101-1»} — одна группа):
         * иначе одно и то же занятие, записанное в файле и в имени файла по-разному, разъехалось бы
         * на два.</p>
         */
        private Set<String> anchors() {
            Set<String> anchors = new LinkedHashSet<>();
            entry.groups().stream()
                    .map(GroupNumberDecoder::key)
                    .filter(group -> !group.isEmpty())
                    .forEach(group -> anchors.add(GROUP_ANCHOR + group));
            rooms().forEach(room -> anchors.add(ROOM_ANCHOR + room));
            if (educator() != null) {
                anchors.add("преп:" + educator());
            }
            return anchors;
        }

        /** Комнаты записи, приведённые к ключу. */
        private Set<String> rooms() {
            return entry.rooms().stream()
                    .map(ScheduleMerger::lower)
                    .filter(room -> !room.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        /** Подпись ведущего, приведённая к ключу; {@code null} — запись не из преподавательского разреза. */
        private String educator() {
            return entry.educator() == null || entry.educator().isBlank() ? null : lower(entry.educator());
        }
    }

    /** Префиксы опор: разделены, потому что комната работает не так, как две другие. */
    private static final String GROUP_ANCHOR = "гр:";
    private static final String ROOM_ANCHOR = "ауд:";

    /**
     * Всё, что сказано об одной дисциплине в одну дату на одной паре.
     *
     * <p><b>Кластер — ещё не занятие.</b> Внутри него законно лежат несколько параллельных занятий
     * (полупотоки, И-19), и разделяет их {@link #split()}.</p>
     */
    private static final class Cluster {

        private final LocalDate date;
        private final TimeSlotPair slot;
        private final String discipline;
        private final String disciplineName;
        private final List<Occurrence> occurrences = new ArrayList<>();

        private Cluster(LocalDate date, TimeSlotPair slot, String discipline, String disciplineName) {
            this.date = date;
            this.slot = slot;
            this.discipline = discipline;
            this.disciplineName = disciplineName;
        }

        private void add(Occurrence occurrence) {
            occurrences.add(occurrence);
        }

        private String anyFile() {
            return occurrences.isEmpty() ? null : occurrences.get(0).sheet().sourceName();
        }

        /**
         * Разводит кластер на параллельные занятия — компоненты связности по опорам записи.
         *
         * <p>Объединение непересекающихся множеств: каждая опора запоминает первую увидевшую её
         * запись, и всякая следующая с той же опорой сливается с ней. Транзитивность здесь по
         * существу дела, а не ради алгоритма: групповой файл связан с преподавательским своей
         * группой, тот с аудиторным — комнатой, и в итоге поток из трёх групп остаётся одним
         * занятием, даже если ни одна пара файлов не совпадает целиком.</p>
         *
         * <p><b>Спорная комната опорой не работает.</b> Если в кластере одну и ту же комнату
         * называют записи <b>разных</b> преподавателей, это большое помещение с несколькими
         * занятиями разом — живой случай «Сп. зал», где ФП идёт у нескольких потоков, и у каждого
         * свой ведущий. Склеивать по такой комнате значит выдать все потоки за один и потерять
         * разделение, которое преподавательский разрез показывает прямо.</p>
         *
         * <p>Заметим, чего это <b>не</b> ломает: двое ведущих на одном занятии остаются вместе, их
         * связывает <b>общая группа</b>. Разница между «ведут вместе» и «ведут параллельно» ровно в
         * этом — состав слушателей общий или разный, — а не в комнате.</p>
         *
         * @return по списку записей на каждое занятие; порядок — как в файлах
         */
        private List<List<Occurrence>> split() {
            int size = occurrences.size();
            if (size < 2) {
                return List.of(List.copyOf(occurrences));
            }
            Set<String> contested = contestedRooms();
            int[] parent = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
            Map<String, Integer> owners = new HashMap<>();
            for (int i = 0; i < size; i++) {
                for (String anchor : occurrences.get(i).anchors()) {
                    if (anchor.startsWith(ROOM_ANCHOR) && contested.contains(anchor.substring(ROOM_ANCHOR.length()))) {
                        continue;
                    }
                    Integer first = owners.putIfAbsent(anchor, i);
                    if (first != null) {
                        union(parent, first, i);
                    }
                }
            }
            Map<Integer, List<Occurrence>> parts = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                parts.computeIfAbsent(find(parent, i), ignored -> new ArrayList<>()).add(occurrences.get(i));
            }
            return List.copyOf(parts.values());
        }

        /**
         * Комнаты, которые в этот час делят разные преподаватели, — опорой они не работают.
         *
         * <p>Признак берётся из данных, а не из списка «залов»: перечень больших помещений
         * пришлось бы вести руками, он устарел бы молча, и «Сп. зал» в нём был бы, а второй такой
         * зал — нет. А «в этой комнате в этот час двое разных ведущих» видно прямо в файлах.</p>
         */
        private Set<String> contestedRooms() {
            Map<String, Set<String>> educatorsByRoom = new LinkedHashMap<>();
            for (Occurrence occurrence : occurrences) {
                String educator = occurrence.educator();
                if (educator == null) {
                    continue;
                }
                occurrence.rooms().forEach(room -> educatorsByRoom
                        .computeIfAbsent(room, ignored -> new LinkedHashSet<>()).add(educator));
            }
            return educatorsByRoom.entrySet().stream()
                    .filter(room -> room.getValue().size() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        /** Собирает занятие из записей одной компоненты. */
        private Draft draft(List<Occurrence> part, SuffixStyle style) {
            Draft draft = new Draft(date, slot, discipline, disciplineName);
            for (Occurrence occurrence : part) {
                LessonEntry entry = occurrence.entry();
                draft.cuts.add(occurrence.sheet().header().kind());
                draft.files.add(occurrence.sheet().sourceName());
                entry.groups().forEach(group -> draft.groups.add(GroupNumberDecoder.render(group, style)));
                entry.rooms().stream().map(String::trim).filter(room -> !room.isEmpty())
                        .forEach(draft.rooms::add);
                if (entry.kind() != null && !entry.kind().isBlank()) {
                    draft.kinds.add(entry.kind().trim());
                }
                if (entry.theme() != null && !entry.theme().isBlank()) {
                    draft.themes.add(entry.theme().trim());
                }
                collectEducator(draft, occurrence);
            }
            return draft;
        }

        private static int find(int[] parent, int node) {
            int root = node;
            while (parent[root] != root) {
                root = parent[root];
            }
            for (int current = node; parent[current] != root; ) {
                int next = parent[current];
                parent[current] = root;
                current = next;
            }
            return root;
        }

        private static void union(int[] parent, int left, int right) {
            int leftRoot = find(parent, left);
            int rightRoot = find(parent, right);
            if (leftRoot != rightRoot) {
                parent[Math.max(leftRoot, rightRoot)] = Math.min(leftRoot, rightRoot);
            }
        }
    }

    /** Накопитель одного занятия: множества, потому что один и тот же факт приезжает из трёх файлов. */
    private static final class Draft {
        private final LocalDate date;
        private final TimeSlotPair slot;
        private final String discipline;
        private final String disciplineName;
        private final Set<String> groups = new LinkedHashSet<>();
        private final Set<String> rooms = new LinkedHashSet<>();
        private final Set<String> kinds = new LinkedHashSet<>();
        private final Set<String> themes = new LinkedHashSet<>();
        private final Set<String> educators = new LinkedHashSet<>();
        /** Кандидаты из подвалов групповых файлов — запасной путь, если разрез никого не назвал. */
        private final Set<String> footerEducators = new LinkedHashSet<>();
        /** Сколько подписей подвал давал на этот вид занятия, когда не различал их (0 — различал). */
        private int footerCandidates;
        private final Set<CutKind> cuts = new LinkedHashSet<>();
        private final Set<String> files = new LinkedHashSet<>();

        private Draft(LocalDate date, TimeSlotPair slot, String discipline, String disciplineName) {
            this.date = date;
            this.slot = slot;
            this.discipline = discipline;
            this.disciplineName = disciplineName;
        }

        /**
         * Кто ведёт занятие: разрез, а если его не было — подвал.
         *
         * <p>Разрез главнее по существу дела: в подвале перечислены все, кто числится за дисциплиной,
         * и ведёт из них тот, у кого занятие стоит в собственном расписании. Поэтому подвал сюда
         * <b>не добавляется</b>, а подставляется вместо: сложение дало бы занятию второго
         * преподавателя, который его не ведёт (запасной, И-22), и заодно тихо пометило бы занятие
         * полупотоком.</p>
         *
         * <p>Находка тоже здесь, а не на ячейке: «подвал даёт троих» само по себе не новость — новость
         * в том, что <b>преподавательского файла не нашлось</b> и привязку взять неоткуда.</p>
         */
        private void resolveEducator(Findings findings) {
            if (!educators.isEmpty()) {
                return;
            }
            if (footerEducators.size() == 1) {
                educators.add(footerEducators.iterator().next());
            } else if (footerEducators.size() > 1) {
                findings.add("преподаватель не определён: подвалы групповых файлов называют разных, "
                        + "а преподавательского файла нет", files.iterator().next());
            } else if (footerCandidates > 1) {
                findings.add("по дисциплине «" + discipline + "» подвал даёт " + footerCandidates
                        + " преподавателей на этот вид занятия, а преподавательского файла нет — "
                        + "привязку даёт только он", files.iterator().next());
            }
        }

        private MergedLesson build(PeriodBounds period, Findings findings) {
            // Расхождение между разрезами не сглаживается: берём первое значение, но говорим о споре.
            if (kinds.size() > 1) {
                findings.add("вид занятия расходится между разрезами: " + String.join(" / ", kinds),
                        files.iterator().next());
            }
            if (themes.size() > 1) {
                findings.add("тема расходится между файлами: " + String.join(" / ", themes),
                        files.iterator().next());
            }
            resolveEducator(findings);

            List<String> sortedGroups = groups.stream().sorted().toList();
            return new MergedLesson(
                    date, slot, discipline, disciplineName,
                    sortedGroups,
                    List.copyOf(rooms),
                    kinds.isEmpty() ? null : kinds.iterator().next(),
                    themes.isEmpty() ? null : themes.iterator().next(),
                    List.copyOf(educators),
                    Set.copyOf(cuts),
                    files.stream().limit(ImportMatchReport.MatchRow.FILES_SHOWN).toList(),
                    period != null && !period.covers(date));
        }
    }

    /** Находки, схлопнутые по тексту: текст → число повторов и файл-образец. */
    private static final class Findings {
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, String> examples = new HashMap<>();

        private void add(String message, String file) {
            counts.merge(message, 1, Integer::sum);
            examples.putIfAbsent(message, file);
        }

        private List<MergeReport.Finding> top() {
            List<MergeReport.Finding> found = new ArrayList<>();
            counts.forEach((message, count) ->
                    found.add(new MergeReport.Finding(message, count, examples.get(message))));
            found.sort(Comparator.comparingInt(MergeReport.Finding::count).reversed()
                    .thenComparing(MergeReport.Finding::message));
            return found.stream().limit(FINDINGS_LIMIT).toList();
        }
    }
}
