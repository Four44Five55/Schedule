package ru.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import ru.services.importing.FolderInspectionReport;
import ru.services.importing.ImportCreationReport;
import ru.services.importing.ImportCreationService;
import ru.services.importing.ImportFolderService;
import ru.services.importing.ImportInspectionService;
import ru.services.importing.ImportMatchingService;
import ru.services.importing.ImportReport;
import ru.services.importing.ParsedSheet;
import ru.services.importing.ScheduleSheetParser;
import ru.services.importing.SheetInspection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Импорт расписания из сторонней программы.
 *
 * <p><b>Сейчас здесь только разбор без записи.</b> Эндпоинт отвечает на вопрос «что программа
 * поняла из файла» и ничего не создаёт: по решению И-10 первый прогон импорта не заводит сущностей,
 * потому что завести преподавателя легко, а убрать — уже нет.</p>
 *
 * <p>Поэтому и путь отдельный, а не {@code /api/schedule/command/...}: команда меняет расписание, а
 * это чтение чужого файла. Когда дойдёт до записи, она пойдёт через существующую дверь
 * ({@code ScheduleSessionGate}) — путей записи в {@code lesson_placement} было восемь, импортёр не
 * должен стать девятым.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportInspectionService inspectionService;
    private final ImportMatchingService matchingService;
    private final ImportCreationService creationService;
    private final ImportFolderService folderService;

    /**
     * Пробный разбор файлов выгрузки.
     *
     * <p>POST {@code /api/import/inspect}, multipart, поле {@code files} — один или несколько
     * HTML-файлов любого разреза (группы, преподавателя, аудитории): разрез определяется по шапке,
     * а не по имени файла.</p>
     *
     * <p>Нечитаемый файл <b>не отменяет остальные</b>: он возвращается со своими замечаниями, как
     * и все прочие. Пачка файлов — обычный режим работы, и падать на одном из тридцати нельзя.</p>
     *
     * <p>Вместе со сводками возвращается <b>сверка со справочниками</b> по всей пачке: она одна на
     * загрузку, потому что дисциплина из подвала одного файла встречается в ячейках другого.</p>
     *
     * @param files      файлы выгрузки
     * @param locationId локация, в которой искать аудитории; без неё одноимённые комнаты разных
     *                   кампусов дадут «одноимённых несколько» вместо тихого выбора первой (И-17)
     * @return сводки по файлам и сверка по пачке
     */
    @PostMapping(value = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport inspect(@RequestPart("files") List<MultipartFile> files,
                                @RequestParam(value = "locationId", required = false) Integer locationId) {
        List<SheetInspection> summaries = new ArrayList<>();
        List<ParsedSheet> sheets = new ArrayList<>();

        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "(без имени)" : file.getOriginalFilename();
            try {
                // Разбор один на оба отчёта: сводка считает по нему цифры, сверка ищет по нему
                // сущности. Второй проход jsoup по четверти мегабайта не нужен никому.
                ParsedSheet sheet = ScheduleSheetParser.parse(file.getBytes());
                sheets.add(sheet);
                summaries.add(inspectionService.inspect(name, sheet));
            } catch (IOException e) {
                log.warn("Файл {} не прочитан: {}", name, e.getMessage());
                summaries.add(unreadable(name, e));
            }
        }

        return new ImportReport(summaries, matchingService.match(sheets, locationId));
    }

    /**
     * Завести в справочниках то, чего сверка не нашла. <b>Плана и расписания не касается.</b>
     *
     * <p>POST {@code /api/import/create-missing}, тот же multipart. Файлы присылаются заново, а не
     * берутся из состояния сервера: серверной жизни у отчёта нет и быть не должно — иначе появилась
     * бы «висящая заявка», которую надо протухать и синхронизировать.</p>
     *
     * <p>Заводится только то, что сверка пометила как отсутствующее. Неоднозначное («одноимённых
     * несколько») и непрочитанное пропускаются с причиной — см. {@link ImportCreationService}.</p>
     *
     * @param files        те же файлы выгрузки
     * @param locationId   локация для аудиторий; без неё комнаты не заводятся
     * @param groupSize    размер заводимой группы — в файле его нет, а колонка {@code NOT NULL}
     * @param roomCapacity вместимость заводимой аудитории — тоже нет в файле
     */
    @PostMapping(value = "/create-missing", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportCreationReport createMissing(@RequestPart("files") List<MultipartFile> files,
                                              @RequestParam(value = "locationId", required = false) Integer locationId,
                                              @RequestParam(value = "groupSize", defaultValue = "25") int groupSize,
                                              @RequestParam(value = "roomCapacity", defaultValue = "30") int roomCapacity) {
        List<ParsedSheet> sheets = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                sheets.add(ScheduleSheetParser.parse(file.getBytes()));
            } catch (IOException e) {
                log.warn("Файл {} не прочитан при заведении: {}", file.getOriginalFilename(), e.getMessage());
            }
        }
        return creationService.createMissing(sheets,
                new ImportCreationService.Settings(locationId, groupSize, roomCapacity));
    }

    /**
     * Разбор каталога выгрузки на диске — путь для полного объёма.
     *
     * <p>POST {@code /api/import/inspect-folder?path=...}. Файлы не загружаются вовсе: бэкенд
     * локальный, выгрузка лежит на той же машине. В живом комплекте 1792 файла на 390 МБ — через
     * multipart это упирается в лимит запроса, а поднимать его дальше нельзя (разбор держит по
     * объекту на файл, отказ переехал бы на {@code OutOfMemory}).</p>
     *
     * <p>Ответ — <b>сводка по пачке</b>, а не карточка на файл: полторы тысячи карточек весили бы
     * десятки мегабайт JSON и всё равно никем не читались бы. Подробности по одному файлу — обычной
     * загрузкой через {@code /inspect}.</p>
     *
     * <p>Каталог обязан лежать внутри {@code import.source-root}; свойство не задано — чтение с
     * диска выключено целиком.</p>
     *
     * @param path       каталог с выгрузкой (можно вложенный — обход рекурсивный)
     * @param locationId локация для сверки аудиторий
     */
    @PostMapping("/inspect-folder")
    public FolderInspectionReport inspectFolder(@RequestParam("path") String path,
                                                @RequestParam(value = "locationId", required = false) Integer locationId) {
        return folderService.inspectFolder(path, locationId);
    }

    /**
     * Завести недостающее по каталогу на диске — то же, что {@code /create-missing}, но без загрузки.
     *
     * <p>Оба пути ведут в один сервис и подчиняются одним правилам: заводится только то, что сверка
     * назвала отсутствующим, неоднозначное пропускается. Разница ровно в том, откуда приехали файлы.</p>
     */
    @PostMapping("/create-missing-folder")
    public ImportCreationReport createMissingFromFolder(@RequestParam("path") String path,
                                                        @RequestParam(value = "locationId", required = false) Integer locationId,
                                                        @RequestParam(value = "groupSize", defaultValue = "25") int groupSize,
                                                        @RequestParam(value = "roomCapacity", defaultValue = "30") int roomCapacity) {
        return creationService.createMissing(folderService.parseFolder(path).sheets(),
                new ImportCreationService.Settings(locationId, groupSize, roomCapacity));
    }

    /** Каталог не разрешён, не существует или чтение выключено — это ошибка запроса, а не сбой. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        log.warn("Отказ разбора каталога: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /**
     * Пачка не влезла в лимит — отвечаем понятной строкой, а не общей 500-й.
     *
     * <p>Умолчание Spring отдаёт исключение без единой цифры, и на экране это выглядело как «файлы
     * не разобрались» — то есть как дефект разбора, хотя запрос до разбора даже не дошёл. Здесь
     * ошибка называет себя сама и подсказывает единственное верное действие: делить пачку. Поднимать
     * потолок бесконечно нельзя — разбор держит в памяти по объекту на файл.</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        log.warn("Пачка файлов не влезла в лимит: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Пачка файлов слишком велика для одного запроса. "
                        + "Загрузите её частями — например, по одному разрезу за раз."));
    }

    private static SheetInspection unreadable(String name, IOException cause) {
        return new SheetInspection(
                name, ru.services.importing.ParsedSheet.CutKind.UNKNOWN, null, null, null, null, null,
                0, 0, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of("файл не прочитан: " + cause.getMessage()),
                List.of());
    }
}
