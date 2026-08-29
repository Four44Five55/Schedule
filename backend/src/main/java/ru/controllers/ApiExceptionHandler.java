package ru.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.exceptions.DomainException;
import ru.exceptions.DuplicateException;
import ru.exceptions.InUseException;
import ru.exceptions.LessonMoveConflictException;
import ru.exceptions.NotFoundException;
import ru.exceptions.RuleViolationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Единственное место, где предметная область встречается с HTTP.
 *
 * <p><b>Формат ответа — RFC 9457 Problem Details</b> ({@link ProblemDetail}, в Spring Framework 6
 * встроен, зависимостей не нужно). До него в проекте было <b>четыре</b> формы тела ошибки —
 * {@code ConflictResponse}, {@code Map.of("message", …)}, голая строка {@code e.getMessage()} и
 * умолчание Spring {@code {timestamp, status, error, path}} — и, как следствие, три разных способа
 * достать текст на фронте.</p>
 *
 * <h2>Почему это заработало только сейчас</h2>
 * Обработчик бесполезен без словаря. Пока ожидаемые ситуации бросались типами
 * {@code IllegalStateException}/{@code IllegalArgumentException}, разделить их с настоящими багами
 * было нечем: поймаешь — и дефекты поедут пользователю как «вы ввели не то»; не поймаешь — и живая
 * ситуация улетит пятисоткой. Словарь {@link DomainException} снял эту развилку.
 *
 * <h2>Два ведра</h2>
 * <ul>
 *   <li><b>{@link DomainException} → 4xx, текст для человека.</b> Ситуация предусмотрена, сообщение
 *       писал человек для человека, показывать его можно как есть.</li>
 *   <li><b>Всё остальное → 500, текст НЕ для человека.</b> Наружу уходит только
 *       {@code correlationId}: подробности непредвиденного сбоя — это внутренности (имена
 *       ограничений БД, пути, куски SQL), и пользователю они не адресованы. В лог при этом уходит
 *       всё вместе с тем же идентификатором, так что скриншот превращается в строку лога.</li>
 * </ul>
 *
 * <h2>Расширения сверх RFC</h2>
 * Поле {@code code} — машинный код ({@code DUPLICATE}, {@code IN_USE}…), чтобы клиент мог ветвиться,
 * <b>не разбирая русский текст</b>: сообщение переписывается свободно, код — контракт. Ровно то же
 * деление, что у видов ограничений (см. {@code CONVENTIONS.md}). Поле {@code type} остаётся
 * {@code about:blank}: по RFC это правильное значение, пока за типом ошибки не стоит страница
 * документации, а смысл несут {@code title} и {@code code}.
 *
 * <h2>Границы</h2>
 * <ul>
 *   <li>{@code @Order(LOWEST_PRECEDENCE)} — уступает {@link ru.controllers.command.CommandExceptionHandler}:
 *       тот специфичнее (409 с актуальной версией сессии, которую клиент подхватывает для повтора)
 *       и обязан выигрывать. Без явного порядка оба имели бы умолчание и спорили бы.</li>
 *   <li>Локальные {@code @ExceptionHandler} внутри контроллера (как в {@code ImportController})
 *       выигрывают у любого advice по правилу Spring — их этот класс не задевает.</li>
 *   <li><b>Контроллеры подметены 2026-08-26:</b> восемь из них перестали ловить доменные
 *       исключения сами и отдают их сюда, попутно вернув типизированный {@code ResponseEntity}
 *       вместо {@code ResponseEntity<?>}. Осталось три места, и каждое — по причине, а не по
 *       забывчивости: {@code ScheduleCommandController} (ответ несёт актуальную версию сессии),
 *       {@code ScheduleQueryController#getPeriodReadiness} (период без курсов — это ответ «ноль из
 *       нуля», а не отказ) и {@code ImportController} (пакет {@code ru.services.importing} ещё не
 *       переведён на словарь). Пометки стоят на самих местах.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Ожидаемая ситуация: статус по типу, текст — как написан в домене.
     *
     * <p><b>Почему {@code RULE_VIOLATION} отвечает 400, а не 422.</b> Семантически 422 точнее
     * («разобрано, но по смыслу нельзя»), но ровно эти ситуации приложение уже отдаёт четырёхсотой
     * из контроллеров. Выбор 400 делает будущую уборку {@code try/catch} чистым удалением без
     * изменения контракта, а тонкую разницу и без того несёт {@code code} — по нему клиент и должен
     * ветвиться, а не по оттенку 4xx.</p>
     *
     * <p>Развилка по типу здесь законна и намеренна: это <b>единственное</b> место, где домен
     * встречается с транспортом. Новый наследник без правки этого метода получит 400 — честное
     * умолчание для «ожидаемой ситуации»; свой статус ему добавляется одной строкой.</p>
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail onDomain(DomainException e) {
        HttpStatus status = switch (e) {
            case DuplicateException ignored -> HttpStatus.CONFLICT;
            case InUseException ignored -> HttpStatus.CONFLICT;
            case LessonMoveConflictException ignored -> HttpStatus.CONFLICT;
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case RuleViolationException ignored -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };

        log.warn("Отказ {} ({}): {}", e.code(), status.value(), e.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(title(e.code()));
        problem.setProperty("code", e.code());
        // Свои поля исключение раскладывает само (например, число ссылок у IN_USE): клиенту они
        // бывают нужны сами по себе — счётчик, подсветка, — и вынимать их разбором русской фразы
        // означало бы склеивать смысл из презентации. Здесь только раскладка: приведения к
        // подтипу нет, поэтому следующий наследник со своим полем эту границу не трогает.
        e.details().forEach(problem::setProperty);
        return problem;
    }

    /**
     * Непредвиденный сбой: наружу — только идентификатор, внутрь лога — всё.
     *
     * <p>Сообщение исключения сюда не попадает <b>намеренно</b>. У технического сбоя в тексте лежат
     * внутренности — имя нарушенного ограничения БД, кусок SQL, путь на диске; они не адресованы
     * пользователю и в интерфейсе читаются как бессмыслица. Показывать здесь есть смысл ровно одно:
     * короткий идентификатор, по которому строка лога находится за секунду.</p>
     *
     * <p>⚠️ Более специфичные обработчики — и в этом классе, и в родительском
     * {@link ResponseEntityExceptionHandler} (ошибки самого Spring MVC: разбор тела, неверный метод,
     * отсутствующий параметр) — выигрывают у этого метода. Иначе он проглотил бы их и превратил
     * законные 4xx в 500.</p>
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception e) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Непредвиденный сбой [{}]", correlationId, e);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Внутренняя ошибка сервера. Сообщите код " + correlationId + " — по нему сбой найдут в журнале.");
        problem.setTitle("Внутренняя ошибка");
        problem.setProperty("code", "INTERNAL");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    /**
     * Отказ Bean Validation: разбор по полям, а не одной фразой.
     *
     * <p>Умолчание Spring отдаёт {@code "Invalid request content."} — то есть не отдаёт ничего:
     * какое поле не устроило, приходится угадывать. Здесь список {@code errors} с именем поля и
     * сообщением из аннотации, а сообщения в проекте написаны по-русски и для человека.</p>
     *
     * <p><b>Не путать с {@code RULE_VIOLATION}:</b> аннотации проверяют форму запроса и не знают о
     * данных, а правило домена проверяется только по состоянию базы. Разные проверки, разные
     * сообщения, разные коды — и здесь их <b>несколько сразу</b>, потому что при заполнении формы
     * человеку нужны все замечания, а не первое.</p>
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(FieldError::getDefaultMessage)
                .orElse("Запрос не прошёл проверку");

        log.warn("Проверка запроса не пройдена: {}", errors);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Запрос не принят");
        problem.setProperty("code", "VALIDATION");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** Заголовок для человека; подробность лежит в {@code detail}. */
    private static String title(String code) {
        return switch (code) {
            case "DUPLICATE" -> "Уже существует";
            case "IN_USE" -> "Удаление невозможно";
            case "RULE_VIOLATION" -> "Правило не выполнено";
            case "MOVE_CONFLICT" -> "Перенос невозможен";
            case "NOT_FOUND" -> "Не найдено";
            default -> "Действие отклонено";
        };
    }
}
