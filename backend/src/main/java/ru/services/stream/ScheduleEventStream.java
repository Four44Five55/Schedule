package ru.services.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Живой канал «сервер → браузер» по сессиям расписания (Server-Sent Events).
 *
 * <h3>Зачем</h3>
 * <p>До него приложение вообще не умело рассказывать клиенту о чужих правках: вкладка жила со
 * снимком, снятым при загрузке, и узнавала об изменениях только получив 409 на собственной команде.
 * Двое диспетчеров видели разные расписания, пока кто-нибудь не нажимал F5.</p>
 *
 * <h3>Почему SSE, а не WebSocket</h3>
 * <p>Нужен ровно односторонний поток «сервер уведомляет». SSE — это обычный HTTP: работает через тот
 * же Spring MVC, не требует отдельного протокола и сам переподключается при обрыве. WebSocket дал бы
 * двусторонний канал, который здесь некуда применить. CRDT/OT (Google Docs) — другой класс задач:
 * там люди правят один объект одновременно, здесь — разные занятия.</p>
 *
 * <h3>Что передаём</h3>
 * <p><b>Звонок, а не данные</b> ({@link ScheduleChangedDto}: сессия + новое поколение). Клиент сам
 * решает, что перечитать, обычными REST-эндпоинтами. Слать содержимое занятий по каналу — соблазн,
 * который заводит второй путь доставки данных, неизбежно расходящийся с первым.</p>
 *
 * <h3>Границы</h3>
 * <p>Реестр — <b>в памяти процесса</b>. Пока инстанс один, этого достаточно. Когда инстансов станет
 * несколько, мутация на узле A не дойдёт до подписчиков узла B — понадобится межпроцессная шина, и
 * брать её надо у PostgreSQL ({@code LISTEN}/{@code NOTIFY}): он уже стоит, транзакционен, и
 * уведомление доставляется только при коммите. Redis для этого заводить незачем.</p>
 */
@Slf4j
@Component
public class ScheduleEventStream {

    /** Сколько эмиттер живёт до таймаута. По истечении браузер (EventSource) переподключается сам. */
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    /** sessionId → живые подписчики (вкладки). */
    private final Map<UUID, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * Подписать вкладку на изменения сессии.
     *
     * @param sessionId сессия расписания
     * @return эмиттер, который контроллер отдаёт клиенту
     */
    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        List<SseEmitter> list = subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        // Снимаем эмиттер при любом исходе — иначе «мёртвые» вкладки копятся и утекают.
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(e -> remove(sessionId, emitter));

        log.debug("📡 Подписка на сессию {} (всего подписчиков: {})", sessionId, list.size());
        return emitter;
    }

    /**
     * Разослать «звонок» всем подписчикам сессии.
     *
     * <p>Отправляется <b>после</b> записи read-модели (см. {@link ru.events.ScheduleProjectedEvent}),
     * поэтому означает буквально «данные готовы, можно перечитывать».</p>
     *
     * @param payload сессия + актуальное поколение
     */
    public void publish(ScheduleChangedDto payload) {
        List<SseEmitter> list = subscribers.get(payload.sessionId());
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("schedule-changed").data(payload));
            } catch (IOException | IllegalStateException e) {
                // Вкладку закрыли/соединение оборвалось — это норма, не ошибка.
                remove(payload.sessionId(), emitter);
            }
        }
        log.debug("📡 Разослано schedule-changed: сессия={}, версия={}, подписчиков={}",
                payload.sessionId(), payload.version(), list.size());
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(sessionId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) subscribers.remove(sessionId);
    }
}
