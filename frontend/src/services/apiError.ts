/**
 * Единственная дверь к ошибке ответа: любое тело с бэка → одна форма для интерфейса.
 *
 * Зачем. Тел у ошибки исторически четыре, и каждое место разбирало их само:
 *   1. `ProblemDetail` (RFC 9457) — `{status, title, detail, code, …}`, новый общий контракт
 *      (`ru.controllers.ApiExceptionHandler`);
 *   2. `ConflictResponse` — `{error, message, currentVersion}`, конфликт версии/ресурса
 *      (`ru.controllers.command.CommandExceptionHandler` и команды расписания);
 *   3. `{message}` — локальные обработчики `ImportController`;
 *   4. голая строка — двенадцать контроллеров, которые пока ловят доменные исключения сами.
 *
 * Текст в них лежит в РАЗНЫХ полях (`detail` против `message`), поэтому прежняя формула
 * `typeof data === 'string' ? data : data.message` на новом контракте молча промахивалась: статус
 * и журнал уже верные, а человек видел общее «Не удалось сохранить». Здесь порядок разбора один и
 * терпим ко всем четырём формам — формы 2–4 уходят по мере уборки контроллеров, и удалять их
 * поддержку надо будет здесь, а не в тридцати местах.
 *
 * Граница: этот модуль отвечает на вопрос «что случилось и что показать», но НЕ решает, где
 * показывать. Место выбирает компонент — он один знает, было ли действие переднего плана.
 */

/** Замечание Bean Validation по конкретному полю формы (`code === 'VALIDATION'`). */
export interface ApiFieldError {
    field: string;
    message: string;
}

/** Ошибка ответа в одной форме — независимо от того, каким телом её прислал бэк. */
export interface ApiError {
    /** HTTP-статус; `null` — ответа не было вовсе (сеть, таймаут). */
    status: number | null;
    /**
     * Машинный код для ветвления: `DUPLICATE`, `IN_USE`, `RULE_VIOLATION`, `MOVE_CONFLICT`,
     * `NOT_FOUND`, `VALIDATION`, `INTERNAL` (словарь домена) либо `CONFLICT` /
     * `RESOURCE_CONFLICT` (команды расписания). Ветвиться нужно по нему, а не по русскому
     * тексту: сообщение переписывается свободно, код — контракт.
     */
    code: string | null;
    /** Текст для человека: с сервера, если он его прислал, иначе запасной. */
    message: string;
    /** Разбор отказа `@Valid` по полям; пусто, если это не он. */
    fields: ApiFieldError[];
    /** Сколько объектов ссылается — приходит с `IN_USE`, чтобы не вынимать число из фразы. */
    usageCount: number | null;
    /** Актуальная версия сессии из конфликта — её подхватывают, чтобы вкладка не залипла. */
    currentVersion: number | null;
    /** Код строки журнала при непредвиденном сбое (`INTERNAL`). */
    correlationId: string | null;
    /** Ответа не было: сеть, таймаут, упавший сервер. */
    network: boolean;
    /** Исходное исключение — для журнала, не для показа. */
    raw: unknown;
}

const DEFAULT_FALLBACK = 'Не удалось выполнить операцию. Попробуйте ещё раз.';
const NETWORK_MESSAGE = 'Сервер не ответил. Проверьте соединение и повторите.';

/** Разбирает ответ любой из четырёх форм. Никогда не бросает: ошибка в обработке ошибки — худшее. */
export function apiError(err: unknown, fallback: string = DEFAULT_FALLBACK): ApiError {
    const response = record(pick(err, 'response'));
    const status = numberOrNull(pick(response, 'status'));
    const body = response ? pick(response, 'data') : undefined;

    if (!response) {
        // Запроса не случилось или ответ не доехал. Текст axios («Network Error», «timeout of
        // 0ms exceeded») человеку ничего не объясняет, поэтому наружу идёт своя фраза.
        return {
            status: null, code: null, message: isRequestFailure(err) ? NETWORK_MESSAGE : fallback,
            fields: [], usageCount: null, currentVersion: null, correlationId: null,
            network: isRequestFailure(err), raw: err,
        };
    }

    const data = record(body);
    return {
        status,
        code: readCode(data),
        message: readMessage(body, data) ?? fallback,
        fields: readFields(data),
        usageCount: numberOrNull(pick(data, 'usageCount')),
        currentVersion: numberOrNull(pick(data, 'currentVersion')),
        correlationId: stringOrNull(pick(data, 'correlationId')),
        network: false,
        raw: err,
    };
}

/**
 * Текст для показа. Запасной вариант обязателен по смыслу: у сетевого отказа и у 500-й своего
 * текста для человека нет, а «что-то пошло не так» без действия — не сообщение.
 */
export function errorMessage(err: unknown, fallback: string = DEFAULT_FALLBACK): string {
    return apiError(err, fallback).message;
}

/** Машинный код отказа или `null`. */
export function errorCode(err: unknown): string | null {
    return apiError(err).code;
}

/**
 * Конфликт ВЕРСИИ: расписание изменили параллельно (соседняя вкладка, другой пользователь).
 *
 * Отличать от `RESOURCE_CONFLICT` (слот/аудитория заняты) обязательно: там версия ни при чём и
 * перечитывать данные не нужно — нужно выбрать другую ячейку.
 */
export function isStaleVersion(err: unknown): boolean {
    const e = apiError(err);
    return e.status === 409 && e.code === 'CONFLICT';
}

// ── Разбор тела ──────────────────────────────────────────────────────────────

/**
 * Текст: `detail` (ProblemDetail) → `message` (ConflictResponse и локальные обработчики) →
 * голая строка. Порядок именно такой: на общем контракте текст лежит в `detail`, и он должен
 * побеждать, если вдруг придут оба поля сразу.
 */
function readMessage(body: unknown, data: Record<string, unknown> | null): string | null {
    return stringOrNull(pick(data, 'detail'))
        ?? stringOrNull(pick(data, 'message'))
        ?? plainTextOrNull(body);
}

/**
 * Код: расширение `code` у ProblemDetail, иначе `error` у ConflictResponse.
 *
 * `error` берётся только в форме кода (заглавные буквы и подчёркивания): у умолчания Spring
 * (`{timestamp, status, error, path}`) в том же поле лежит человеческая фраза «Not Found», и
 * принимать её за код нельзя — по ней потом начнут ветвиться.
 */
function readCode(data: Record<string, unknown> | null): string | null {
    const code = stringOrNull(pick(data, 'code'));
    if (code) return code;

    const error = stringOrNull(pick(data, 'error'));
    return error && /^[A-Z][A-Z0-9_]*$/.test(error) ? error : null;
}

function readFields(data: Record<string, unknown> | null): ApiFieldError[] {
    const errors = pick(data, 'errors');
    if (!Array.isArray(errors)) return [];

    return errors.flatMap((item) => {
        const entry = record(item);
        const field = stringOrNull(pick(entry, 'field'));
        const message = stringOrNull(pick(entry, 'message'));
        return field && message ? [{ field, message }] : [];
    });
}

// ── Мелочи ───────────────────────────────────────────────────────────────────

function record(value: unknown): Record<string, unknown> | null {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
        ? (value as Record<string, unknown>)
        : null;
}

function pick(value: unknown, key: string): unknown {
    return record(value)?.[key];
}

function stringOrNull(value: unknown): string | null {
    return typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
}

/**
 * Голая строка тела. Страницу ошибки сервера (HTML) отбрасываем: показать её целиком — то же
 * самое, что не показать ничего, только длиннее.
 */
function plainTextOrNull(value: unknown): string | null {
    const text = stringOrNull(value);
    return text && !text.startsWith('<') ? text : null;
}

function numberOrNull(value: unknown): number | null {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/** Отличает «запрос ушёл, ответа нет» от произвольного исключения фронта. */
function isRequestFailure(err: unknown): boolean {
    return pick(err, 'isAxiosError') === true || pick(err, 'request') != null;
}
