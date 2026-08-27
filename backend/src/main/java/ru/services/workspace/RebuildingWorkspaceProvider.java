package ru.services.workspace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.services.WorkspaceRecreationService;
import ru.services.WorkspaceRecreationService.RecreatedWorkspace;

import java.util.UUID;
import java.util.function.Function;

/**
 * Базовая реализация: workspace строится заново на каждый запрос.
 *
 * <p>Ровно то, что делали читающие пути до появления кэша, — вынесено за интерфейс, чтобы кэш стал
 * декоратором, а не веткой внутри сервиса. Она же остаётся рабочей, если кэш когда-нибудь выключат:
 * убрать декоратор — и поведение вернётся к прежнему, без правки читателей.</p>
 */
@Component
@RequiredArgsConstructor
public class RebuildingWorkspaceProvider implements WorkspaceProvider {

    private final WorkspaceRecreationService recreationService;

    @Override
    public <T> T withWorkspaceOfSession(UUID sessionId, Function<RecreatedWorkspace, T> body) {
        return body.apply(recreationService.recreateWorkspaceFromSession(sessionId));
    }

    @Override
    public <T> T withWorkspaceOfPlacement(UUID placementId, Function<RecreatedWorkspace, T> body) {
        return body.apply(recreationService.recreateWorkspaceForPlacement(placementId));
    }
}
