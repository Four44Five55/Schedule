package ru.services.workspace;

import ru.services.WorkspaceRecreationService.RecreatedWorkspace;

import java.util.UUID;
import java.util.function.Function;

/**
 * Откуда читающий путь берёт {@code ScheduleWorkspace} — «одолжить на время операции».
 *
 * <p><b>Одалживание, а не выдача.</b> Метод принимает тело работы, а не возвращает workspace:
 * реализация, которая держит общий экземпляр, обязана знать, когда работа кончилась (снять замок,
 * убедиться, что читатель вернул изъятое). Отдай она объект наружу — момент «кончилось» пропал бы,
 * и договорённость «верни как было» снова стала бы устной.</p>
 *
 * <p><b>Зачем абстракция.</b> Кэш — это Decorator над «строит заново» ({@link RebuildingWorkspaceProvider}
 * ← {@link CachingWorkspaceProvider}): читатели зависят от интерфейса (DIP), выключение кэша —
 * это не флаг внутри сервиса, а неподключённый декоратор (OCP). Класть кэш внутрь
 * {@code WorkspaceRecreationService} нельзя — смешает построение с хранением.</p>
 *
 * <p><b>Только чтение.</b> Мутирующие пути (перенос, ручная установка, генерация) строят workspace
 * сами: им нужен свежий авторитетный снимок, который они необратимо меняют, и кэш их не ускорит —
 * они всё равно поднимают версию сессии и сбрасывают ключ.</p>
 */
public interface WorkspaceProvider {

    /**
     * Выполнить чтение на workspace сессии.
     *
     * @param sessionId сессия расписания
     * @param body      что посчитать; workspace действителен только на время вызова
     */
    <T> T withWorkspaceOfSession(UUID sessionId, Function<RecreatedWorkspace, T> body);

    /**
     * То же, но сессия определяется по размещению — так приходят запросы подбора вариантов
     * переноса ({@code placementId} надёжнее пришедшего с фронта {@code sessionId}: тот может
     * указывать на другую сессию).
     *
     * @param placementId размещение; его уже может не быть — тогда тело получит пустой workspace
     */
    <T> T withWorkspaceOfPlacement(UUID placementId, Function<RecreatedWorkspace, T> body);
}
