package ru.services.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.entity.write.ScheduleSession;
import ru.events.ScheduleProjectedEvent;
import ru.repository.write.ScheduleSessionRepository;

/**
 * Мост «проекция обновлена → вкладки узнали»: слушает {@link ScheduleProjectedEvent} и рассылает
 * звонок в {@link ScheduleEventStream}.
 *
 * <h2>Почему отдельный класс</h2>
 * <p>SRP: {@code ScheduleSynchronizer} владеет read-моделью и ничего не должен знать про браузеры,
 * эмиттеры и HTTP; {@code ScheduleEventStream} владеет соединениями и ничего не должен знать про
 * доменные события. Этот класс — единственное место, где они встречаются. Новый повод уведомить
 * клиента = новый слушатель здесь, а не правка синхронизатора (OCP).</p>
 *
 * <h2>Момент отправки</h2>
 * <p>{@link TransactionPhase#AFTER_COMMIT} транзакции, в которой синхронизатор писал
 * {@code schedule_view}. Раньше отправлять нельзя: клиент прибежит перечитывать и получит старую
 * картинку — та самая гонка, из-за которой во фронте жили паузы {@code setTimeout(1000)}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleChangeNotifier {

    private final ScheduleEventStream stream;
    private final ScheduleSessionRepository sessionRepo;

    /**
     * Проекция сессии записана — сообщаем вкладкам актуальное поколение.
     *
     * <p>Версию читаем из БД, а не берём из события: событие приходит из асинхронного слушателя
     * проекции, и «поколение на момент публикации» к этому времени уже могло уйти вперёд. Клиенту
     * нужно последнее известное — иначе он подхватит устаревшую версию и получит 409 на следующей
     * же команде, то есть мы починили бы одну гонку и завели другую.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleProjected(ScheduleProjectedEvent event) {
        Long version = sessionRepo.findById(event.getSessionId())
                .map(ScheduleSession::getVersion)
                .orElse(null);

        stream.publish(new ScheduleChangedDto(event.getSessionId(), version));
    }
}
