package ru.controllers.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.entity.write.ScheduleSession;
import ru.repository.write.ScheduleSessionRepository;

import java.util.UUID;

/**
 * Перевод конфликта optimistic lock в HTTP 409 для всех команд расписания.
 *
 * <p><b>Зачем отдельный обработчик.</b> {@code OPTIMISTIC_FORCE_INCREMENT}
 * ({@link ru.services.session.ScheduleSessionGate}) срабатывает не в момент вызова сервиса, а
 * <b>на коммите</b> транзакции — то есть при выходе из {@code @Transactional}-метода. Ловить это
 * в каждом эндпоинте нечестно и не выйдет: у {@code remove}/{@code lock}/{@code reorder}/
 * {@code clear} никаких {@code try/catch} нет, оттуда полетел бы сырой 500. А там, где они есть
 * ({@code move-lesson}, {@code move-chain}, {@code placements}), маппинг «конфликт → 409» уже
 * скопирован четырежды.</p>
 *
 * <p>Поэтому трансляция исключений уезжает из контроллера сюда (SRP): контроллер занимается
 * маршрутизацией, а не переводом доменных сбоев в коды ответа.</p>
 *
 * <p>Ловится {@link OptimisticLockingFailureException} — родитель
 * {@code ObjectOptimisticLockingFailureException}, чтобы не зависеть от того, какой именно подтип
 * подставит трансляция исключений Spring на конкретной версии Hibernate.</p>
 */
@Slf4j
@RestControllerAdvice(basePackageClasses = ScheduleCommandController.class)
// Порядок задан явно: с появлением общего ru.controllers.ApiExceptionHandler оба advice имели бы
// умолчание LOWEST_PRECEDENCE и спорили бы за конфликт версий недетерминированно. Этот
// специфичнее — он отдаёт актуальную версию сессии, которую клиент подхватывает для повтора.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CommandExceptionHandler {

    private final ScheduleSessionRepository sessionRepo;

    /**
     * Версия клиента устарела: расписание изменил кто-то другой (или соседняя вкладка).
     *
     * <p>В теле — актуальная версия, чтобы клиент мог подхватить её и повторить действие, а не
     * «обновлять страницу». Расписание при этом не пострадало: транзакция откачена целиком.</p>
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ConflictResponse> onOptimisticLock(OptimisticLockingFailureException e) {
        Long currentVersion = currentVersionOf(e);
        log.warn("⚠️ Optimistic lock: расписание изменено параллельно (актуальная версия={})", currentVersion);

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ConflictResponse(
                        "CONFLICT",
                        "Расписание было изменено параллельно. Данные обновлены — повторите действие.",
                        currentVersion));
    }

    /**
     * Актуальная версия сессии, о которую споткнулись. Транзакция команды уже откачена, поэтому
     * читаем заново; идентификатор берём из самого исключения.
     *
     * @return версия или {@code null}, если сессию не опознать (тогда клиент просто перечитает всё)
     */
    private Long currentVersionOf(OptimisticLockingFailureException e) {
        if (!(e instanceof org.springframework.orm.ObjectOptimisticLockingFailureException ole)
                || !(ole.getIdentifier() instanceof UUID sessionId)) {
            return null;
        }
        return sessionRepo.findById(sessionId)
                .map(ScheduleSession::getVersion)
                .orElse(null);
    }
}
