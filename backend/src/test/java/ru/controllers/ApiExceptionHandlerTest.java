package ru.controllers;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.exceptions.DomainException;
import ru.exceptions.DuplicateException;
import ru.exceptions.InUseException;
import ru.exceptions.LessonMoveConflictException;
import ru.exceptions.RuleViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import ru.exceptions.NotFoundException;

/**
 * Граница «домен → HTTP».
 *
 * <p><b>Что здесь закрепляется.</b> Не раскладка полей RFC 9457 — её держит Spring, — а два
 * решения, которые легко потерять следующей правкой и которые дорого стоят:</p>
 *
 * <ol>
 *   <li><b>Текст ожидаемой ситуации доходит до клиента, текст непредвиденного сбоя — нет.</b>
 *       Первое написано человеком для человека; второе несёт внутренности (имя нарушенного
 *       ограничения БД, кусок SQL, путь на диске) и наружу не адресовано. Стереть эту границу
 *       можно одной строчкой «а давайте покажем причину», и снаружи это выглядит как улучшение.</li>
 *   <li><b>Машинный {@code code} присутствует всегда.</b> Он существует ровно затем, чтобы клиент
 *       не ветвился разбором русского текста: сообщение переписывается свободно, код — контракт.</li>
 * </ol>
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    // ── ведро 1: ожидаемые ситуации ──

    @Test
    @DisplayName("дубль → 409, текст домена доходит до клиента как есть")
    void duplicateKeepsItsMessage() {
        ProblemDetail p = handler.onDomain(new DuplicateException("Дисциплина «Физика» уже существует."));

        assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(p.getDetail()).isEqualTo("Дисциплина «Физика» уже существует.");
        assertThat(p.getProperties()).containsEntry("code", "DUPLICATE");
    }

    @Test
    @DisplayName("«удалить нельзя» → 409, число ссылок отдельным полем, а не только внутри фразы")
    void inUseExposesUsageCountAsField() {
        ProblemDetail p = handler.onDomain(new InUseException("Ссылаются 3 курса", 3));

        assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(p.getProperties())
                .containsEntry("code", "IN_USE")
                .containsEntry("usageCount", 3);
    }

    @Test
    @DisplayName("не посчитанное число ссылок не превращается в null-поле")
    void inUseWithoutCountOmitsTheField() {
        ProblemDetail p = handler.onDomain(new InUseException("Ссылаются"));

        assertThat(p.getProperties()).doesNotContainKey("usageCount");
    }

    @Test
    @DisplayName("нарушено правило домена → 400 (не 422): уборка try/catch станет чистым удалением")
    void ruleViolationIsBadRequest() {
        ProblemDetail p = handler.onDomain(new RuleViolationException("Нельзя связать слот сам с собой."));

        assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(p.getProperties()).containsEntry("code", "RULE_VIOLATION");
    }

    @Test
    @DisplayName("конфликт переноса → 409 своим кодом, а не общим")
    void moveConflictKeepsItsOwnCode() {
        ProblemDetail p = handler.onDomain(new LessonMoveConflictException("Аудитория занята"));

        assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(p.getProperties()).containsEntry("code", "MOVE_CONFLICT");
    }

    @Test
    @DisplayName("новый наследник без правки обработчика получает 400, а не 500")
    void unknownDomainSubtypeDegradesToBadRequest() {
        ProblemDetail p = handler.onDomain(new DomainException("Что-то предусмотренное") {
            @Override
            public String code() {
                return "SOMETHING_NEW";
            }
        });

        assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(p.getProperties()).containsEntry("code", "SOMETHING_NEW");
    }

    @Test
    @DisplayName("своё поле наследника доезжает клиенту БЕЗ правки обработчика")
    void subtypeDetailsReachTheClientWithoutTouchingTheBoundary() {
        // Проверяется открытость словаря: раньше число ссылок доставалось приведением к подтипу,
        // и каждый следующий наследник со своим полем требовал ещё одной такой строки на границе.
        // Этот наследник обработчику неизвестен — и всё равно рассказывает о себе.
        ProblemDetail p = handler.onDomain(new DomainException("Окно аттестации занято") {
            @Override
            public String code() {
                return "WINDOW_TAKEN";
            }

            @Override
            public java.util.Map<String, Object> details() {
                return java.util.Map.of("windowId", 17);
            }
        });

        assertThat(p.getProperties()).containsEntry("code", "WINDOW_TAKEN");
        assertThat(p.getProperties()).containsEntry("windowId", 17);
    }

    @Test
    @DisplayName("объекта нет → 404 через тот же словарь, а не через исключение JPA")
    void notFoundIsPartOfTheDictionary() {
        ProblemDetail p = handler.onDomain(new NotFoundException("Дисциплина с id=7 не найдена."));

        assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(p.getDetail()).isEqualTo("Дисциплина с id=7 не найдена.");
        assertThat(p.getProperties()).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("EntityNotFoundException от Hibernate — это битая ссылка, а не вежливый 404")
    void jpaEntityNotFoundIsTreatedAsFailure() {
        // Своих бросков этого типа в проекте не осталось: «не найдено» выражает NotFoundException.
        // Значит сюда доезжает только неразрешённый ленивый прокси — дефект ДАННЫХ, и наружу он
        // обязан уйти пятисоткой с идентификатором, а не сообщением «объект не найден».
        ProblemDetail p = handler.onUnexpected(new EntityNotFoundException("Unable to find ru.entity.Group with id 7"));

        assertThat(p.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(p.getDetail()).doesNotContain("ru.entity.Group");
        assertThat(p.getProperties()).containsEntry("code", "INTERNAL");
    }

    // ── ведро 2: непредвиденный сбой ──

    @Test
    @DisplayName("технический сбой → 500, и его текст НАРУЖУ НЕ ВЫХОДИТ")
    void unexpectedFailureNeverLeaksItsMessage() {
        String internals = "duplicate key value violates unique constraint \"uq_assignment_slot_stream\"";

        ProblemDetail p = handler.onUnexpected(new IllegalStateException(internals));

        assertThat(p.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(p.getDetail()).doesNotContain(internals);
        assertThat(p.getDetail()).doesNotContain("constraint");
        assertThat(p.getProperties()).containsEntry("code", "INTERNAL");
    }

    @Test
    @DisplayName("вместо причины наружу уходит идентификатор, и он же назван в тексте")
    void unexpectedFailureCarriesCorrelationId() {
        ProblemDetail p = handler.onUnexpected(new RuntimeException("что угодно"));

        Object id = p.getProperties().get("correlationId");
        assertThat(id).asString().isNotBlank();
        // Идентификатор бесполезен, если его нет в том, что человек видит на экране: без этого
        // «сообщите код» превращается в «сообщите, что была ошибка».
        assertThat(p.getDetail()).contains(String.valueOf(id));
    }

    @Test
    @DisplayName("два сбоя — два разных идентификатора, иначе в журнале их не различить")
    void correlationIdIsUniquePerFailure() {
        Object first = handler.onUnexpected(new RuntimeException("раз")).getProperties().get("correlationId");
        Object second = handler.onUnexpected(new RuntimeException("два")).getProperties().get("correlationId");

        assertThat(first).isNotEqualTo(second);
    }

    /**
     * Тот же обработчик, но через настоящий Spring MVC.
     *
     * <p>Тесты выше зовут методы напрямую и потому доказывают только содержимое {@link ProblemDetail}.
     * Они <b>не</b> доказывают главного: что возврат {@code ProblemDetail} из
     * {@code @ExceptionHandler} действительно превращается в нужный HTTP-статус и в тело нужной
     * формы. Между «объект собран правильно» и «клиент получил правильный ответ» лежит разрешение
     * исключений, согласование типа содержимого и сериализация — и сломать это можно, не тронув
     * ни строчки в самом обработчике.</p>
     *
     * <p>Поднимается автономный MVC без контекста Spring: ни базы, ни бинов, ни профилей.
     * ⚠️ Поэтому запуск <b>всего приложения</b> этим не проверяется — интеграционные тесты в
     * проекте в карантине (нужна изолированная БД), и регистрация advice в живом контексте
     * подтверждается только настоящим стартом.</p>
     */
    @Nested
    @DisplayName("через настоящий MVC")
    class ThroughMvc {

        @RestController
        static class Stub {
            @GetMapping("/boom/duplicate")
            String duplicate() {
                throw new DuplicateException("Дисциплина «Физика» уже существует.");
            }

            @GetMapping("/boom/internal")
            String internal() {
                throw new IllegalStateException("violates unique constraint \"uq_secret\"");
            }
        }

        private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Stub())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        @Test
        @DisplayName("дубль доезжает клиенту как 409 problem+json с кодом и текстом")
        void duplicateReachesClientAsProblemJson() throws Exception {
            mvc.perform(get("/boom/duplicate"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("DUPLICATE"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Дисциплина «Физика» уже существует."));
        }

        @Test
        @DisplayName("технический сбой доезжает как 500 и НЕ несёт внутренностей в теле")
        void internalFailureLeaksNothingOverTheWire() throws Exception {
            mvc.perform(get("/boom/internal"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("uq_secret"))));
        }
    }
}
