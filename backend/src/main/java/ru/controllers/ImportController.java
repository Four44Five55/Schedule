package ru.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import ru.services.importing.FolderInspectionReport;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ImportCreationReport;
import ru.services.importing.ImportCreationService;
import ru.services.importing.ImportFolderService;
import ru.services.importing.ImportInspectionService;
import ru.services.importing.ImportMergeService;
import ru.services.importing.ImportMatchingService;
import ru.services.importing.ImportReport;
import ru.services.importing.ImportPlanService;
import ru.services.importing.ImportRollbackService;
import ru.services.importing.ImportScheduleService;
import ru.services.importing.ParsedSheet;
import ru.services.importing.PlanReport;
import ru.services.importing.ScheduleSheetParser;
import ru.services.importing.ScheduleWriteReport;
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
    private final ImportMergeService mergeService;
    private final ImportPlanService planService;
    private final ImportScheduleService scheduleService;
    private final ImportRollbackService rollbackService;
    private final ObjectMapper objectMapper;

    /**
     * Автор импортированных размещений в аудите.
     *
     * <p>Константа, а не имя пользователя: аутентификации в проекте нет, а «кто это поставил» —
     * вопрос, на который у импорта есть честный ответ. Он же отличит импортированное от ручного в
     * {@code created_by}, когда {@code source} будет не под рукой.</p>
     */
    private static final String IMPORT_USER = "import";

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
     * @param periodId   период импорта: по нему считаются занятия за его границами (И-8) и
     *                   проверяется, тот ли период выбран (учебный год напечатан в шапке файла)
     * @return сводки по файлам, сверка и сведение разрезов по пачке
     */
    @PostMapping(value = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport inspect(@RequestPart("files") List<MultipartFile> files,
                                @RequestParam(value = "locationId", required = false) Integer locationId,
                                @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle,
                                @RequestParam(value = "periodId", required = false) Integer periodId) {
        List<SheetInspection> summaries = new ArrayList<>();
        List<ParsedSheet> sheets = new ArrayList<>();

        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "(без имени)" : file.getOriginalFilename();
            try {
                // Разбор один на оба отчёта: сводка считает по нему цифры, сверка ищет по нему
                // сущности. Второй проход jsoup по четверти мегабайта не нужен никому.
                ParsedSheet sheet = ScheduleSheetParser.parse(file.getBytes(), name);
                sheets.add(sheet);
                summaries.add(inspectionService.inspect(name, sheet));
            } catch (IOException e) {
                log.warn("Файл {} не прочитан: {}", name, e.getMessage());
                summaries.add(unreadable(name, e));
            }
        }

        return new ImportReport(summaries,
                matchingService.match(sheets, locationId, groupNameStyle),
                mergeService.merge(sheets, periodId, groupNameStyle));
    }

    /**
     * Завести <b>только подразделения</b> — шаг, который идёт раньше всех остальных.
     *
     * <p>POST {@code /api/import/create-org-units}, тот же multipart. Отдельно от
     * {@code /create-missing} потому, что на подразделения ссылаются и преподаватель, и группа, и
     * комната: незаведённая кафедра не отменяет заведение зависимых, а <b>тихо оставляет их без
     * привязки</b>. Разобрать спорное дерево дешевле до того, как появились сотни ссылающихся строк,
     * чем чинить их потом по всему справочнику.</p>
     *
     * <p>Повторный запуск безопасен: уже заведённое пропускается по ключу.</p>
     */
    @PostMapping(value = "/create-org-units", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportCreationReport createOrgUnits(@RequestPart("files") List<MultipartFile> files,
                                               @RequestParam(value = "orgUnitParents", required = false) String parents) {
        return creationService.createOrgUnitsOnly(parse(files, "заведении подразделений"), orgUnitSettings(parents));
    }

    /** То же по каталогу на диске — правила и сервис те же, отличается только вход. */
    @PostMapping("/create-org-units-folder")
    public ImportCreationReport createOrgUnitsFromFolder(@RequestParam("path") String path,
                                                         @RequestParam(value = "orgUnitParents", required = false) String parents) {
        return creationService.createOrgUnitsOnly(folderService.parseFolder(path).sheets(), orgUnitSettings(parents));
    }

    /**
     * Настройки для шага подразделений: из всего нужны только ручные привязки.
     *
     * <p>Размер группы и вместимость комнаты сюда не относятся вовсе — шаг их не касается, и
     * подставлять «лишь бы что» было бы враньём в сигнатуре. Берутся значения по умолчанию, которые
     * этот шаг не читает.</p>
     */
    private ImportCreationService.Settings orgUnitSettings(String parents) {
        return new ImportCreationService.Settings(null, 0, 0, SuffixStyle.SLASH, Map.of(), parents(parents));
    }

    /**
     * Выбранные человеком родители подразделений: JSON «имя строки → имя родителя».
     *
     * <p><b>Именем, а не id</b>: спорный факультет может быть ещё не заведён — его создаёт этот же
     * прогон, — и id у него попросту нет. Выбирать из одного лишь справочника значило бы задать
     * вопрос, на который нечем ответить.</p>
     */
    private Map<String, String> parents(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            log.warn("Выбранные родители подразделений не разобраны ({}): {}", e.getMessage(), json);
            return Map.of();
        }
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
     * @param orgUnits ручные привязки к подразделению, JSON «раздел → значение строки → id»;
     *                 см. {@link #orgUnits(String)}
     */
    @PostMapping(value = "/create-missing", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportCreationReport createMissing(@RequestPart("files") List<MultipartFile> files,
                                              @RequestParam(value = "locationId", required = false) Integer locationId,
                                              @RequestParam(value = "groupSize", defaultValue = "25") int groupSize,
                                              @RequestParam(value = "roomCapacity", defaultValue = "30") int roomCapacity,
                                              @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle,
                                              @RequestParam(value = "orgUnits", required = false) String orgUnits,
                                              @RequestParam(value = "orgUnitParents", required = false) String orgUnitParents) {
        return creationService.createMissing(parse(files, "заведении"),
                new ImportCreationService.Settings(locationId, groupSize, roomCapacity, groupNameStyle,
                        orgUnits(orgUnits), parents(orgUnitParents)));
    }

    /**
     * Ручные привязки к подразделению: JSON «раздел отчёта → значение строки → id подразделения».
     *
     * <p><b>Почему JSON-строкой, а не парой списков.</b> Ключ здесь — значение как оно показано в
     * отчёте: «п/п-к Астахов С.В.», «10073/19». В нём законно встречаются и пробелы, и точки, и
     * косая черта, поэтому любой разделитель («ключ=значение», «ключ:значение») рано или поздно
     * попал бы внутрь ключа и разъехался бы молча. JSON границы значений держит сам.</p>
     *
     * <p><b>Почему одна карта с разделом снаружи, а не поле на каждую сущность.</b> Иначе фронт
     * обязан знать, что «Преподаватели» кладутся в одно поле, а «Группы» в другое, — то есть
     * повторить у себя классификацию разделов. Здесь он просто передаёт то, что показал бэк.</p>
     *
     * <p>Пусто или не разобралось — <b>пустая карта, а не отказ</b>: ручные привязки это уточнение
     * поверх вывода из файлов, и терять из-за них весь прогон незачем. Кривой ввод виден в логе.</p>
     */
    private Map<String, Map<String, Integer>> orgUnits(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Integer>>>() {});
        } catch (IOException e) {
            log.warn("Ручные привязки к подразделению не разобраны ({}): {}", e.getMessage(), json);
            return Map.of();
        }
    }

    /**
     * Завести учебный план по расчёту {@code plan-preview}. <b>Расписания не касается.</b>
     *
     * <p>POST {@code /api/import/create-plan}. Тот же вход и тот же отчёт, что у расчёта, — и это
     * не совпадение: расчёт и запись идут одним проходом, иначе подтверждают одно, а получают
     * другое.</p>
     *
     * <p>Шаг идёт <b>после</b> {@code create-missing}: без заведённых дисциплины, группы и потока
     * занятие в план не разрешается, и весь отчёт состоял бы из блокеров.</p>
     *
     * <p>Повторный запуск безопасен — заведённое находится по ключам и считается «уже есть».</p>
     */
    @PostMapping(value = "/create-plan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlanReport createPlan(@RequestPart("files") List<MultipartFile> files,
                                 @RequestParam("periodId") Integer periodId,
                                 @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle) {
        return planService.create(parse(files, "заведении плана"), periodId, groupNameStyle);
    }

    /** То же по каталогу на диске. */
    @PostMapping("/create-plan-folder")
    public PlanReport createPlanFromFolder(@RequestParam("path") String path,
                                           @RequestParam("periodId") Integer periodId,
                                           @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle) {
        return planService.create(folderService.parseFolder(path).sheets(), periodId, groupNameStyle);
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
                                                @RequestParam(value = "locationId", required = false) Integer locationId,
                                                @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle,
                                                @RequestParam(value = "periodId", required = false) Integer periodId) {
        return folderService.inspectFolder(path, locationId, groupNameStyle, periodId);
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
                                                        @RequestParam(value = "roomCapacity", defaultValue = "30") int roomCapacity,
                                                        @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle,
                                                        @RequestParam(value = "orgUnits", required = false) String orgUnits,
                                                        @RequestParam(value = "orgUnitParents", required = false) String orgUnitParents) {
        return creationService.createMissing(folderService.parseFolder(path).sheets(),
                new ImportCreationService.Settings(locationId, groupSize, roomCapacity, groupNameStyle,
                        orgUnits(orgUnits), parents(orgUnitParents)));
    }

    /**
     * Что импорт сделает с учебным планом. <b>Ничего не создаёт.</b>
     *
     * <p>POST {@code /api/import/plan-preview}. Отдельным шагом, а не вместе с разбором: план
     * считается уже <b>после</b> заведения справочников (без заведённой дисциплины и потока занятие
     * не разрешается), и период здесь обязателен — от него зависит семестр курса.</p>
     */
    @PostMapping(value = "/plan-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlanReport planPreview(@RequestPart("files") List<MultipartFile> files,
                                  @RequestParam("periodId") Integer periodId,
                                  @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle) {
        return planService.preview(parse(files, "расчёте плана"), periodId, groupNameStyle);
    }

    /** То же по каталогу на диске — правила и сервис те же, отличается только вход. */
    @PostMapping("/plan-preview-folder")
    public PlanReport planPreviewFromFolder(@RequestParam("path") String path,
                                            @RequestParam("periodId") Integer periodId,
                                            @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle) {
        return planService.preview(folderService.parseFolder(path).sheets(), periodId, groupNameStyle);
    }

    /**
     * Записать расписание: план и размещения в <b>новую</b> сессию.
     *
     * <p>POST {@code /api/import/create-schedule}. Последний шаг импорта и единственный, который
     * трогает расписание. Идёт после {@code create-missing}: без заведённых справочников занятие не
     * разрешается в назначение, и писать было бы нечего.</p>
     *
     * <p><b>Сессия всегда новая</b> — живое расписание не двигается, а неудачный прогон сносится
     * одной командой (И-9). Размещения пишутся {@code locked = true} и {@code source = IMPORTED};
     * замок обязателен: перегенерация сохраняет по {@code locked}, а не по {@code source}.</p>
     *
     * <p>Проверок занятости <b>нет намеренно</b>: импорт фиксирует факт. Двойные бронирования и
     * перебор вместимости приедут как есть и будут видны датчиками.</p>
     *
     * @param locationId локация прогона: без неё комнату не разрешить, и всё встанет без аудиторий
     * @param project    писать ли read-модель. В отдельном (экспериментальном) периоде — можно и
     *                   полезно: импорт видно в обычной сетке. В живом периоде <b>нельзя</b>, пока
     *                   {@code schedule_view} не несёт {@code session_id} (И-11): две сессии одного
     *                   периода смешаются в сетке, а генерация снесёт строки импорта
     */
    @PostMapping(value = "/create-schedule", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScheduleWriteReport createSchedule(@RequestPart("files") List<MultipartFile> files,
                                              @RequestParam("periodId") Integer periodId,
                                              @RequestParam(value = "locationId", required = false) Integer locationId,
                                              @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle,
                                              @RequestParam(value = "project", defaultValue = "false") boolean project) {
        return scheduleService.write(parse(files, "записи расписания"), periodId, locationId,
                groupNameStyle, project, IMPORT_USER);
    }

    /** То же по каталогу на диске — путь для полного объёма. */
    @PostMapping("/create-schedule-folder")
    public ScheduleWriteReport createScheduleFromFolder(@RequestParam("path") String path,
                                                        @RequestParam("periodId") Integer periodId,
                                                        @RequestParam(value = "locationId", required = false) Integer locationId,
                                                        @RequestParam(value = "groupNameStyle", defaultValue = "SLASH") SuffixStyle groupNameStyle,
                                                        @RequestParam(value = "project", defaultValue = "false") boolean project) {
        return scheduleService.write(folderService.parseFolder(path).sheets(), periodId, locationId,
                groupNameStyle, project, IMPORT_USER);
    }

    /**
     * Импортные сессии периода — чтобы было что удалять после перезагрузки вкладки.
     *
     * <p>GET {@code /api/import/sessions?periodId=...}. Признак импортной сессии — её размещения с
     * {@code source = IMPORTED}, а не отдельная колонка: колонка может соврать, строки нет.</p>
     */
    @GetMapping("/sessions")
    public List<ImportRollbackService.ImportedSession> importedSessions(@RequestParam("periodId") Integer periodId) {
        return rollbackService.importedSessions(periodId);
    }

    /**
     * Цена отката плана периода — <b>до</b> нажатия, а не по факту исчезнувшего расписания.
     *
     * <p>GET {@code /api/import/rollback-impact?periodId=...}. То же правило, что у удаления
     * аудитории и подразделения: каскад без названной заранее цены — это тихо снесённые данные.</p>
     */
    @GetMapping("/rollback-impact")
    public ImportRollbackService.RollbackImpact rollbackImpact(@RequestParam("periodId") Integer periodId) {
        return rollbackService.impact(periodId);
    }

    /**
     * Снести учебный план периода: курсы → слоты → назначения → размещения, плюс осиротевшие потоки.
     *
     * <p>DELETE {@code /api/import/plan?periodId=...}. Нужен, когда кривым оказался разбор плана.
     * Если кривой оказалась только раскладка, достаточно снести сессию
     * ({@code DELETE /api/schedule/command/sessions/{id}}) — план переживёт.</p>
     *
     * <p>Уборка потоков — не мелочь: имя потока уникально <b>глобально</b>, и без неё второй прогон
     * упрётся в занятое имя, то есть откат окажется неполным ровно там, где он и нужен.</p>
     *
     * <p><b>Справочники не трогаются</b>: дисциплины, преподаватели, группы, комнаты и подразделения
     * заводил другой шаг, и убирать их должен тоже он.</p>
     */
    @DeleteMapping("/plan")
    public ImportRollbackService.RollbackImpact rollbackPlan(@RequestParam("periodId") Integer periodId) {
        return rollbackService.rollbackPlan(periodId);
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
    /**
     * Разбирает пачку файлов, пропуская нечитаемые.
     *
     * <p>Нечитаемый файл <b>не отменяет остальные</b>: пачка — обычный режим работы, и падать на
     * одном из тысячи нельзя. Сводку по такому файлу отдаёт только {@code /inspect} (там у неё есть
     * куда лечь); командам заведения и расчёта достаточно строки в логе.</p>
     *
     * @param step чем занимались — чтобы строка лога называла шаг, а не только файл
     */
    private List<ParsedSheet> parse(List<MultipartFile> files, String step) {
        List<ParsedSheet> sheets = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                sheets.add(ScheduleSheetParser.parse(file.getBytes(), file.getOriginalFilename()));
            } catch (IOException e) {
                log.warn("Файл {} не прочитан при {}: {}", file.getOriginalFilename(), step, e.getMessage());
            }
        }
        return sheets;
    }

    /**
     * Названной сущности больше нет — 404 с текстом, а не сырая 500-я.
     *
     * <p>Сюда приходит ручная привязка к подразделению, которое удалили между сверкой и заведением.
     * Молча её проигнорировать нельзя: выбор сделан осознанно, и тихо завести человека «куда
     * вывелось» — это ошибка, которую в расписании потом не увидеть.</p>
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(EntityNotFoundException e) {
        log.warn("Заведение прервано: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

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
