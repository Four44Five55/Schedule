package ru.controllers.read;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.services.stream.ScheduleEventStream;

import java.util.UUID;

/**
 * Подписка вкладки на изменения расписания (Server-Sent Events).
 *
 * <p>Живёт на Query Side: это чтение — поток уведомлений «что-то изменилось, перечитай».
 * Никаких команд отсюда не принимается.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule/query")
@RequiredArgsConstructor
public class ScheduleStreamController {

    private final ScheduleEventStream stream;

    /**
     * Поток изменений сессии.
     *
     * <p>GET /api/schedule/query/stream/{sessionId}</p>
     *
     * <p>Клиент (браузерный {@code EventSource}) получает события {@code schedule-changed} с телом
     * {@code {sessionId, version}} — «звонок», а не данные: что именно перечитать, решает он сам.
     * Событие приходит ПОСЛЕ записи {@code schedule_view}, поэтому перечитывать безопасно сразу.</p>
     *
     * <p>Соединение живёт до таймаута, после чего {@code EventSource} переподключается сам —
     * поэтому планировщик и heartbeat-пинги не нужны.</p>
     */
    @GetMapping(path = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID sessionId) {
        log.debug("📡 Подписка на поток изменений: сессия={}", sessionId);
        return stream.subscribe(sessionId);
    }
}
