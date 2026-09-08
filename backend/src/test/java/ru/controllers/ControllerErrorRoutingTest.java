package ru.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.dto.constraint.ConstraintKindFormDto;
import ru.dto.orgUnit.OrgUnitUpdateDto;
import ru.enums.OrgUnitType;
import ru.exceptions.DuplicateException;
import ru.exceptions.InUseException;
import ru.exceptions.NotFoundException;
import ru.services.LessonChainMoveService;
import ru.services.MoveLessonSuggestionService;
import ru.services.WorkspaceRecreationService;
import ru.services.workspace.RebuildingWorkspaceProvider;
import ru.services.constraints.ConstraintKindService;
import ru.services.orgunit.OrgUnitScopeResolver;
import ru.services.orgunit.OrgUnitService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Что контроллеры отдают клиенту ПОСЛЕ подметания (2026-08-26).
 *
 * <p><b>Зачем эти тесты именно сейчас.</b> {@code ApiExceptionHandlerTest} доказывает, что advice
 * собирает правильный {@code ProblemDetail} — но на заглушке. Он не заметит, если контроллер
 * поймает доменное исключение сам и ответит по-своему: снаружи это выглядит как работающий код,
 * а разъезжается контракт. Ровно так и было до подметания — четыре формы тела ошибки на
 * приложение. Здесь подняты <b>настоящие</b> контроллеры с подменёнными сервисами, и закрепляется
 * то, что легко потерять обратной правкой «а поймаю-ка я тут».</p>
 *
 * <p>⚠️ Автономный MVC: ни контекста, ни базы. Регистрацию advice в живом приложении это не
 * проверяет (интеграционные тесты в карантине) — проверяется маршрут «исключение сервиса →
 * ответ клиенту» на реальном коде контроллера.</p>
 */
class ControllerErrorRoutingTest {

    private final ObjectMapper json = new ObjectMapper();

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("справочник видов: дубль → 409 problem+json с кодом (а не 400 голой строкой)")
    void duplicateKindGoesThroughAdvice() throws Exception {
        ConstraintKindService service = mock(ConstraintKindService.class);
        when(service.create(any())).thenThrow(new DuplicateException("Вид «Наряд» уже существует."));

        mvc(new ConstraintKindController(service))
                .perform(post("/api/constraint-kinds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new ConstraintKindFormDto("Наряд", "Н", "amber", 1, true, null))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.detail").value("Вид «Наряд» уже существует."));
    }

    @Test
    @DisplayName("справочник видов: занятый вид → 409 и число ссылающихся отдельным полем")
    void inUseKindExposesUsageCount() throws Exception {
        ConstraintKindService service = mock(ConstraintKindService.class);
        doThrow(new InUseException("Видом размечено 12 ограничений", 12)).when(service).delete(anyString());

        mvc(new ConstraintKindController(service))
                .perform(delete("/api/constraint-kinds/USER_1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"))
                .andExpect(jsonPath("$.usageCount").value(12));
    }

    @Test
    @DisplayName("подразделения: объекта нет → 404 С ТЕКСТОМ (раньше приходило пустое тело)")
    void missingOrgUnitAnswersWithReason() throws Exception {
        OrgUnitService service = mock(OrgUnitService.class);
        when(service.update(anyInt(), any()))
                .thenThrow(new NotFoundException("Подразделение с id=7 не найдено"));

        mvc(new OrgUnitController(service, mock(OrgUnitScopeResolver.class)))
                .perform(put("/api/org-units/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new OrgUnitUpdateDto("Кафедра", "К", OrgUnitType.DEPARTMENT, null, true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Подразделение с id=7 не найдено"));
    }

    /**
     * Самый дорогой из снятых глушителей: {@code catch (Exception) → 200 []}.
     *
     * <p>Пустой список ячеек — это утверждение «переносить некуда», по которому человек принимает
     * решение. Отвечать им на сбой значит не промолчать, а <b>соврать</b>: подсветка гаснет, и
     * занятие уходят искать в другой период. Пятисотка честнее — клиент покажет отказ и предложит
     * повторить.</p>
     */
    @Test
    @DisplayName("подбор ячеек: сбой → 500, а НЕ «вариантов нет»")
    void moveOptionsFailureIsNotAnEmptyAnswer() throws Exception {
        WorkspaceRecreationService recreation = mock(WorkspaceRecreationService.class);
        when(recreation.recreateWorkspaceForPlacement(any()))
                .thenThrow(new IllegalStateException("не удалось восстановить занятие для размещения 42"));

        mvc(new ScheduleMoveController(
                mock(MoveLessonSuggestionService.class), mock(LessonChainMoveService.class),
                new RebuildingWorkspaceProvider(recreation)))
                .perform(post("/api/schedule/find-move-options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"11111111-1111-1111-1111-111111111111",
                                 "placementId":"22222222-2222-2222-2222-222222222222",
                                 "rootEntityId":1,"rootEntityType":"GROUP"}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                // Внутренности технического сбоя наружу по-прежнему не выходят.
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("восстановить"))));
    }
}
