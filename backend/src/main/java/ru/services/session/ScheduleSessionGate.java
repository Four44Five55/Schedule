package ru.services.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.repository.write.ScheduleSessionRepository;

import java.util.UUID;

/**
 * Единая дверь «взять сессию расписания НА ЗАПИСЬ».
 *
 * <h3>Какой инвариант держит</h3>
 * <p><b>Мутация размещения = новое поколение агрегата.</b> {@link ScheduleSession} — корень
 * агрегата, {@link LessonPlacement} живёт внутри его границы. Но {@code @Version} стоит на строке
 * сессии, а мутаторы пишут в {@code lesson_placement} — Hibernate такую строку грязной не считает
 * и версию не поднимает. Итог до появления этого класса: версия росла ТОЛЬКО на генерации (там есть
 * {@code updateStatus}), а перенос, ручная раскладка, пин, пересортировка и очистка её не двигали.
 * Optimistic lock не срабатывал никогда, а версия как ключ кэша workspace была бы прямо опасна:
 * молчала бы там, где сброс обязателен.</p>
 *
 * <h3>Почему дверь, а не {@code touch()} в каждом сервисе</h3>
 * <ul>
 *   <li><b>OCP.</b> Путей записи в {@code lesson_placement} восемь. Разложи «не забудь поднять
 *       версию» по ним — и девятый про это забудет. Ровно так в проекте родились занятия-призраки
 *       (знание «у размещения есть проекция» было размазано по сервисам удаления) и лжеснимки.
 *       Лечили это дважды — инвариантом с одним владельцем, а не дисциплиной. Прецедент двери —
 *       {@link ru.services.projection.ProjectionMaintenance}.</li>
 *   <li><b>SRP.</b> Поднимать версию через {@code updatedAt}/{@code updatedBy} нельзя: это поля
 *       АУДИТА («кто последний правил»), а версия — счётчик поколений для конкуренции и кэша.
 *       Склей их — и правка аудита начнёт молча инвалидировать кэш, а забытый аудит — отключать
 *       optimistic lock. Поэтому версию двигает родной механизм JPA
 *       ({@code OPTIMISTIC_FORCE_INCREMENT}), выражающий намерение напрямую.</li>
 * </ul>
 *
 * <h3>Контракт</h3>
 * <p>Сверка версии и её подъём <b>неразделимы</b>: мутатор не может получить сессию, минуя их.
 * Дальше он свободно меняет размещения — версия поднимется на коммите транзакции.</p>
 *
 * <p>⚠️ Исключение прилетает <b>на коммите</b>, а не из этого метода: {@code FORCE_INCREMENT}
 * проверяется, когда Hibernate пишет UPDATE строки сессии. Перевод в HTTP 409 — в
 * {@link ru.controllers.command.CommandExceptionHandler}.</p>
 *
 * <h3>Чего дверь НЕ закрывает</h3>
 * <p>Каскады БД ({@code discipline → course → slot → assignment → lesson_placement}) сносят
 * размещения мимо Hibernate и мимо приложения — версия этого не заметит. Поэтому будущий кэш
 * workspace обязан слушать ещё и {@code ProjectionStaleEvent} (те пути и так обязаны звать
 * {@code ProjectionMaintenance}). Версия — необходимое, но не достаточное условие свежести.</p>
 *
 * @see ScheduleSessionRepository#findByIdForWrite(UUID)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleSessionGate {

    private final ScheduleSessionRepository sessionRepo;

    /**
     * Взять сессию на запись: сверить версию и запланировать подъём поколения.
     *
     * @param sessionId       сессия, чьи размещения будут изменены
     * @param expectedVersion версия, которую держит клиент; {@code null} — сверку пропустить
     *                        (команда ещё не носит версию в контракте), но поколение всё равно
     *                        поднять — иначе снимок кэша и соседние вкладки протухнут молча
     * @return сессия, версия которой будет поднята при коммите
     * @throws ObjectOptimisticLockingFailureException если версия клиента устарела
     * @throws IllegalArgumentException                если сессии нет
     */
    public ScheduleSession forWrite(UUID sessionId, Long expectedVersion) {
        ScheduleSession session = sessionRepo.findByIdForWrite(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена: " + sessionId));

        if (expectedVersion != null && !expectedVersion.equals(session.getVersion())) {
            log.warn("⚠️ Конфликт версий сессии {}: клиент={}, БД={}",
                    sessionId, expectedVersion, session.getVersion());
            throw new ObjectOptimisticLockingFailureException(ScheduleSession.class, sessionId);
        }
        return session;
    }

    /**
     * То же, но якорь — размещение: сессию берём из него, а не из {@code sessionId} с фронта.
     *
     * <p>Размещение — единственный надёжный якорь сессии: {@code schedule_view} грузится без
     * привязки к сессии, поэтому фронтовый {@code sessionId} может указывать на другую.</p>
     *
     * @param placement       изменяемое размещение (его сессия и будет взята на запись)
     * @param expectedVersion версия, которую держит клиент; {@code null} — сверку пропустить
     * @return сессия-владелец размещения
     */
    public ScheduleSession forWriteOf(LessonPlacement placement, Long expectedVersion) {
        return forWrite(placement.getSession().getId(), expectedVersion);
    }
}
