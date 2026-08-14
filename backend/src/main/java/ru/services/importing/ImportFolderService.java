package ru.services.importing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.services.importing.FolderInspectionReport.ProblemCount;
import ru.services.importing.ParsedSheet.CutKind;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Разбор целого каталога выгрузки — путь для полного объёма.
 *
 * <p><b>Зачем мимо браузера.</b> В живой выгрузке 1792 файла на 390 МБ. Загрузка такого объёма через
 * multipart упирается в лимит запроса, а поднимать его дальше нельзя: разбор держит по объекту на
 * файл, и отказ переехал бы с понятного «слишком большой запрос» на {@code OutOfMemory} посреди
 * работы. Бэкенд у пользователя локальный, файлы лежат на той же машине — передавать их через HTTP
 * незачем вовсе.</p>
 *
 * <p><b>Файлы читаются по одному и сразу отпускаются.</b> В памяти остаётся только
 * {@link ParsedSheet} — непустые ячейки и подвал, то есть проценты от исходного HTML. DOM jsoup
 * живёт ровно на время разбора одного файла.</p>
 *
 * <h2>Доступ к файловой системе ограничен намеренно</h2>
 * <p>Эндпоинт читает то, что ему назовут, а приложение не имеет аутентификации вовсе. Поэтому
 * каталог обязан лежать <b>внутри разрешённого корня</b> ({@code import.source-root}). Не задан —
 * чтение выключено целиком: доступ к диску включается явным действием, а не оказывается включённым
 * по умолчанию.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportFolderService {

    private final ImportMatchingService matchingService;

    /** Корень, внутри которого разрешено читать. Пусто — чтение с диска выключено. */
    @Value("${import.source-root:}")
    private String sourceRoot;

    /** Сколько разных текстов замечаний показывать: остальное — хвост, который никто не читает. */
    private static final int PROBLEM_KINDS_LIMIT = 100;

    /**
     * Разбирает все файлы выгрузки в каталоге (включая вложенные — выгрузка разложена по папкам
     * подразделений) и сверяет результат со справочниками.
     *
     * @param folder     каталог; должен лежать внутри {@code import.source-root}
     * @param locationId локация для сверки аудиторий (И-17)
     * @throws IllegalArgumentException если чтение выключено или каталог вне разрешённого корня
     */
    public FolderInspectionReport inspectFolder(String folder, Integer locationId) {
        Parsed parsed = parseFolder(folder);

        Map<CutKind, Integer> byCut = new EnumMap<>(CutKind.class);
        int lessons = 0;
        int markers = 0;
        LocalDate first = null;
        LocalDate last = null;

        for (ParsedSheet sheet : parsed.sheets()) {
            byCut.merge(sheet.header().kind(), 1, Integer::sum);
            markers += (int) sheet.cells().stream().filter(ParsedSheet.SheetCell::isMarker).count();

            for (CellDialect.LessonEntry lesson : CellDialect.readAll(sheet)) {
                lessons++;
                first = earliest(first, lesson.date());
                last = latest(last, lesson.date());
            }
        }

        log.info("Каталог {}: файлов {}, разобрано {}, занятий {}, разных замечаний {}",
                parsed.path(), parsed.files(), parsed.sheets().size(), lessons, parsed.problems().size());

        return new FolderInspectionReport(
                parsed.path(),
                parsed.files(),
                parsed.sheets().size(),
                parsed.unreadable(),
                byCut,
                lessons,
                markers,
                first,
                last,
                topProblems(parsed),
                parsed.problems().values().stream().mapToInt(Integer::intValue).sum(),
                matchingService.match(parsed.sheets(), locationId)
        );
    }

    /**
     * Разобранный каталог.
     *
     * @param path       каталог, приведённый к абсолютному виду
     * @param files      сколько файлов найдено
     * @param unreadable сколько файлов не прочиталось вовсе
     * @param sheets     что удалось разобрать
     * @param problems   текст замечания → в скольких файлах встретился
     * @param examples   текст замечания → имя одного из таких файлов
     */
    public record Parsed(
            String path,
            int files,
            int unreadable,
            List<ParsedSheet> sheets,
            Map<String, Integer> problems,
            Map<String, String> examples
    ) {
    }

    /**
     * Читает и разбирает все файлы каталога. Публично, потому что вход нужен двоим — сводке и
     * заведению справочников: разбирать одно и то же дважды ради двух отчётов не за чем.
     *
     * <p><b>Замечания собираются здесь, а не выше по стеку</b> — только здесь ещё известно, из
     * какого файла пришло каждое. Текст замечания описывает ячейку («Вт 1 колонка 5»), и без имени
     * файла в прогоне на 1792 файла его нечем проверить: искать такую ячейку пришлось бы вручную
     * по всему каталогу.</p>
     */
    public Parsed parseFolder(String folder) {
        Path target = resolveInside(allowedRoot(), folder);
        List<Path> files = listSheets(target);

        List<ParsedSheet> sheets = new ArrayList<>(files.size());
        Map<String, Integer> problems = new HashMap<>();
        Map<String, String> examples = new HashMap<>();
        int unreadable = 0;

        for (Path file : files) {
            String name = target.relativize(file).toString();
            ParsedSheet sheet;
            try {
                // Байты живут ровно до разбора: в памяти остаётся ParsedSheet — непустые ячейки и
                // подвал, то есть проценты от исходного HTML. Иначе 390 МБ приехали бы целиком.
                sheet = ScheduleSheetParser.parse(Files.readAllBytes(file));
            } catch (IOException e) {
                unreadable++;
                remember(problems, examples, "файл не прочитан: " + e.getMessage(), name);
                continue;
            }

            sheets.add(sheet);
            sheet.problems().forEach(problem -> remember(problems, examples, problem, name));
        }
        return new Parsed(target.toString(), files.size(), unreadable, sheets, problems, examples);
    }

    /** Первый файл с таким замечанием и запоминается: он и есть образец для разбирательства. */
    private static void remember(Map<String, Integer> problems, Map<String, String> examples,
                                 String problem, String file) {
        problems.merge(problem, 1, Integer::sum);
        examples.putIfAbsent(problem, file);
    }

    // =======================================================================

    private Path allowedRoot() {
        if (sourceRoot == null || sourceRoot.isBlank()) {
            throw new IllegalArgumentException(
                    "Чтение выгрузки с диска выключено. Задайте import.source-root — каталог, "
                            + "внутри которого разрешено читать файлы.");
        }
        return Path.of(sourceRoot).toAbsolutePath().normalize();
    }

    /**
     * Каталог внутри разрешённого корня.
     *
     * <p>{@code normalize()} до сравнения — обязателен: без него «корень + ../../» прошло бы
     * проверку буквой в букву и увело бы чтение куда угодно.</p>
     */
    private static Path resolveInside(Path root, String folder) {
        Path target = (folder == null || folder.isBlank() ? root : Path.of(folder))
                .toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Каталог вне разрешённого корня: " + root);
        }
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("Каталога нет: " + target);
        }
        return target;
    }

    /** Выгрузка разложена по папкам подразделений, поэтому обход рекурсивный. */
    private static List<Path> listSheets(Path target) {
        try (Stream<Path> walk = Files.walk(target)) {
            return walk.filter(Files::isRegularFile)
                    .filter(ImportFolderService::isSheet)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isSheet(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    private static List<ProblemCount> topProblems(Parsed parsed) {
        return parsed.problems().entrySet().stream()
                .map(entry -> new ProblemCount(entry.getKey(), entry.getValue(),
                        parsed.examples().get(entry.getKey())))
                .sorted(Comparator.comparingInt(ProblemCount::count).reversed()
                        .thenComparing(ProblemCount::message))
                .limit(PROBLEM_KINDS_LIMIT)
                .toList();
    }

    private static LocalDate earliest(LocalDate current, LocalDate candidate) {
        if (candidate == null) return current;
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static LocalDate latest(LocalDate current, LocalDate candidate) {
        if (candidate == null) return current;
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
