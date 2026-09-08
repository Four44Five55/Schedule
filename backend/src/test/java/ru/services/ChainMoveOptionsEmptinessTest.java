package ru.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.Lesson;
import ru.services.WorkspaceRecreationService.RecreatedWorkspace;
import ru.services.constraints.AllConstraints;
import ru.services.solver.ScheduleWorkspace;
import ru.services.workspace.RebuildingWorkspaceProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Когда «вариантов нет» — ответ, а когда — враньё.
 *
 * <p><b>Что здесь закрепляется.</b> Пустой список ячеек не молчит: он <b>утверждает</b>, что
 * переносить некуда, и человек по нему принимает решение — ищет место в другом периоде, отменяет
 * перенос, идёт править план. Поэтому отвечать пустотой можно только на настоящее «некуда».</p>
 *
 * <p>Различаются два состояния, которые до 2026-08-26 давали один и тот же пустой ответ:</p>
 * <ul>
 *   <li><b>размещения уже нет</b> — сняли, пока клиент спрашивал; восстанавливать нечего, и пустой
 *       список тут правдив;</li>
 *   <li><b>размещение есть, а занятие не восстановилось</b> — сессия развернулась, но звено в ней
 *       потерялось. Это испорченное состояние, и выдавать его за «некуда» нельзя: человек будет
 *       искать место, которого ему просто не показали.</li>
 * </ul>
 *
 * <p>Тот же разбор и в {@code moveChain} — там условие всегда бросало. Расхождение между чтением
 * и записью на одном и том же условии и было дефектом.</p>
 */
class ChainMoveOptionsEmptinessTest {

    private final WorkspaceRecreationService recreation = mock(WorkspaceRecreationService.class);

    private LessonChainMoveService service() {
        // Провайдер — настоящий «строит заново» поверх подменённого восстановления:
        // так тест проверяет и то, что декоратор не изменил смысл базового пути.
        return new LessonChainMoveService(recreation, new RebuildingWorkspaceProvider(recreation),
                null, null, null, null);
    }

    /** Пустое пространство планирования: содержимое неважно — важна карта размещений рядом с ним. */
    private static ScheduleWorkspace emptyWorkspace() {
        return new ScheduleWorkspace(
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30),
                List.of(), List.of(), List.of(),
                new AllConstraints(Map.of(), Map.of(), Map.of()));
    }

    @Test
    @DisplayName("Размещения уже нет — пустой список это правда")
    void goneePlacementAnswersWithEmptyList() {
        UUID gone = UUID.randomUUID();
        when(recreation.recreateWorkspaceForPlacement(any()))
                .thenReturn(new RecreatedWorkspace(emptyWorkspace(), Map.of()));

        assertThat(service().findChainMoveOptions(List.of(gone))).isEmpty();
    }

    @Test
    @DisplayName("Занятие потерялось при восстановлении — это сбой, а не «переносить некуда»")
    void lostLessonIsAFailureNotAnAnswer() {
        UUID lost = UUID.randomUUID();
        // Карта НЕ пуста: сессия развернулась, но нужного звена в ней нет.
        when(recreation.recreateWorkspaceForPlacement(any()))
                .thenReturn(new RecreatedWorkspace(emptyWorkspace(), Map.of(UUID.randomUUID(), new Lesson())));

        assertThatThrownBy(() -> service().findChainMoveOptions(List.of(lost)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Не удалось восстановить цепочку");
    }
}
