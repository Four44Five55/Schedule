package ru.services.workspace;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.events.ProjectionStaleEvent;
import ru.repository.write.ScheduleSessionRepository;
import ru.services.WorkspaceRecreationService;
import ru.services.WorkspaceRecreationService.RecreatedWorkspace;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Кэш workspace: Decorator над {@link RebuildingWorkspaceProvider}.
 *
 * <p><b>Зачем.</b> Замер (живая сессия, 1075 размещений): пересоздание workspace — 125–165 мс и
 * ~70 SQL-запросов, сам подбор вариантов — <b>1 мс</b>. То есть каждый клик по расписанию платил
 * стократ за построение снимка, который между кликами не менялся.</p>
 *
 * <h2>Когда снимок считается годным</h2>
 * Три признака, и все три обязательны — каждый закрывает то, чего не видят остальные:
 * <ul>
 *   <li><b>Версия сессии</b> ({@code @Version}) — любая мутация размещений через
 *       {@code ScheduleSessionGate} поднимает её на единицу. Основной ключ.</li>
 *   <li><b>Поколение ограничений</b> ({@link ConstraintChangeListener}) — ограничения входят в
 *       снимок, но версию сессии не двигают: они вообще не привязаны к сессии.</li>
 *   <li><b>Срок годности</b> ({@link #TTL}) — страховка от того, чего не видит ни версия, ни
 *       поколение: каскадов БД (см. {@link #onProjectionStale}) и второго инстанса приложения,
 *       чьи Spring-события до нас не долетают. Ошибка ограничена по времени, а не бессрочна.</li>
 * </ul>
 *
 * <h2>Почему замок, а не просто карта</h2>
 * Workspace <b>изменяемый</b>: читатели временно изымают из него занятие
 * ({@code ScheduleWorkspace.withoutPlacements}). Пока он жил один запрос, это было личное дело
 * запроса; общий экземпляр обязан отдаваться по одному. Отсюда и форма интерфейса: не «дай
 * workspace», а «выполни это на workspace» — только так замок снимается там же, где взят.
 * Замки распределены по 16 полосам (striping): карта замков «по сессии» росла бы вместе с числом
 * сессий, а сессий за жизнь процесса много больше, чем одновременных читателей.
 *
 * <h2>Чего кэш не умеет и как это не становится ошибкой</h2>
 * Снимок держит <b>отсоединённые</b> JPA-сущности: занятия, ресурсы, ограничения загружены в той
 * транзакции, где снимок собирался. Связь, которую при сборке никто не тронул, во второй раз уже не
 * догрузится — {@link LazyInitializationException}. Это не лечится «повтором»: на отсоединённом
 * объекте отложенная загрузка невозможна в принципе. Поэтому такой снимок считается негодным —
 * выбрасывается, собирается заново (уже в текущей транзакции), и чтение повторяется один раз. Цена
 * — те же 130 мс, что были до кэша; в логе остаётся предупреждение с названием связи, по которому
 * видно, что именно стоит подгружать при сборке.
 */
@Slf4j
@Primary
@Component
public class CachingWorkspaceProvider implements WorkspaceProvider {

    /** Сколько сессий держим. Один снимок — единицы мегабайт, поэтому граница обязательна. */
    static final int MAX_SESSIONS = 4;

    /** Срок годности снимка — верхняя граница расхождения с БД для того, чего не видит версия. */
    static final Duration TTL = Duration.ofMinutes(1);

    /** Полос замков; сессий за жизнь процесса много, одновременных читателей — единицы. */
    private static final int LOCK_STRIPES = 16;

    private final WorkspaceProvider delegate;
    private final ScheduleSessionRepository sessions;
    private final WorkspaceRecreationService recreationService;
    private final LongSupplier nanoTime;

    private final ReentrantLock[] stripes = new ReentrantLock[LOCK_STRIPES];

    /** LRU по обращению: самый давно не спрошенный снимок вытесняется первым. */
    private final Map<UUID, Entry> cache = new LinkedHashMap<>(MAX_SESSIONS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Entry> eldest) {
            return size() > MAX_SESSIONS;
        }
    };

    // Конструкторов два (второй — тестовый), поэтому выбор обязан быть явным: при нескольких
    // конструкторах без @Autowired Spring ищет безаргументный и падает «No default constructor».
    @Autowired
    public CachingWorkspaceProvider(RebuildingWorkspaceProvider delegate,
                                    ScheduleSessionRepository sessions,
                                    WorkspaceRecreationService recreationService) {
        this(delegate, sessions, recreationService, System::nanoTime);
    }

    /** Для тестов: время — параметр, иначе срок годности проверялся бы ожиданием в минуту. */
    CachingWorkspaceProvider(WorkspaceProvider delegate,
                             ScheduleSessionRepository sessions,
                             WorkspaceRecreationService recreationService,
                             LongSupplier nanoTime) {
        this.delegate = delegate;
        this.sessions = sessions;
        this.recreationService = recreationService;
        this.nanoTime = nanoTime;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    @Override
    public <T> T withWorkspaceOfSession(UUID sessionId, Function<RecreatedWorkspace, T> body) {
        if (sessionId == null) {
            return delegate.withWorkspaceOfSession(null, body);
        }
        ReentrantLock lock = lockFor(sessionId);
        lock.lock();
        try {
            RecreatedWorkspace cached = valid(sessionId);
            if (cached == null) {
                return body.apply(build(sessionId));
            }
            try {
                return body.apply(cached);
            } catch (LazyInitializationException stale) {
                // Снимок пережил свою транзакцию, а внутри него — сущности с ленивыми связями,
                // которые при сборке никто не тронул. Отложенная загрузка на отсоединённом объекте
                // невозможна в принципе, поэтому снимок негоден: выбрасываем и считаем честно.
                // Ловим узко и делаем ровно одну пересборку — «повторим и авось» здесь не работает,
                // на свежем снимке та же связь загрузится в своей транзакции.
                log.warn("Снимок сессии {} не пережил транзакцию ({}) — пересобираем и повторяем",
                        sessionId, stale.getMessage());
                evictAll();
                return body.apply(build(sessionId));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T withWorkspaceOfPlacement(UUID placementId, Function<RecreatedWorkspace, T> body) {
        UUID sessionId = placementId == null ? null : recreationService.sessionIdOfPlacement(placementId);
        if (sessionId == null) {
            // Размещения уже нет: пусть базовый путь отдаст ровно тот пустой workspace, который
            // читатели умеют отличать от «нашлось, но пусто». Кэшировать тут нечего.
            return delegate.withWorkspaceOfPlacement(placementId, body);
        }
        return withWorkspaceOfSession(sessionId, body);
    }

    /**
     * Снимок из кэша, если он ещё годен; {@code null} — надо строить заново.
     *
     * <p>Вызывается под замком сессии, поэтому обращения к карте не требуют своей синхронизации от
     * читателя — но карта общая для всех полос, поэтому доступ к ней всё равно синхронизирован.</p>
     */
    private RecreatedWorkspace valid(UUID sessionId) {
        Entry entry = get(sessionId);
        if (entry == null) {
            return null;
        }
        if (nanoTime.getAsLong() - entry.builtAtNanos() > TTL.toNanos()) {
            log.debug("Снимок сессии {} просрочен — пересобираем", sessionId);
            return null;
        }
        if (entry.constraintsGeneration() != ConstraintChangeListener.generation()) {
            log.debug("Ограничения правились — снимок сессии {} пересобираем", sessionId);
            return null;
        }
        Long current = sessions.findVersionById(sessionId).orElse(null);
        if (current == null || !current.equals(entry.version())) {
            log.debug("Версия сессии {} изменилась ({} → {}) — снимок пересобираем",
                    sessionId, entry.version(), current);
            return null;
        }
        return entry.workspace();
    }

    /** Строит снимок и запоминает его вместе с признаками годности. */
    private RecreatedWorkspace build(UUID sessionId) {
        long startedAt = nanoTime.getAsLong();
        // Признаки читаются ДО построения: если ограничения или версия изменятся во время сборки,
        // снимок окажется устаревшим по своему же ключу и пересоберётся на следующем обращении.
        // Обратный порядок дал бы снимок, который «выглядит свежим», не будучи им.
        long constraintsGeneration = ConstraintChangeListener.generation();
        Long version = sessions.findVersionById(sessionId).orElse(null);

        RecreatedWorkspace workspace = delegate.withWorkspaceOfSession(sessionId, Function.identity());

        if (!workspace.complete()) {
            // Посев потерял размещения (битые ссылки в данных). Ответить на текущий вопрос таким
            // снимком можно — это лучше, чем уронить весь экран из-за одной строки, — но положить
            // его в кэш нельзя: ошибка одного запроса стала бы ответом на все запросы следующей
            // минуты, причём для всех читателей сессии. Пересборка стоит 130 мс, ошибка дороже —
            // тот же размен, что у сброса по ProjectionStaleEvent.
            log.warn("Снимок сессии {} неполон ({} размещений не восстановлено) — не кэшируем",
                    sessionId, workspace.seedFailures());
        } else if (version != null) {
            put(sessionId, new Entry(version, constraintsGeneration, nanoTime.getAsLong(), workspace));
        }
        log.info("⏱ Снимок сессии {} собран за {} мс (версия {})",
                sessionId, (nanoTime.getAsLong() - startedAt) / 1_000_000, version);
        return workspace;
    }

    /**
     * Каскады БД сносят размещения мимо приложения ({@code discipline → course → slot → assignment
     * → lesson_placement}): Hibernate такой мутации не видит, и версия сессии не растёт. Единственный
     * след, который эти пути обязаны оставить, — {@link ProjectionStaleEvent} (иначе поедет
     * проекция), поэтому он и служит вторым сигналом.
     *
     * <p>Сбрасываем <b>всё</b>, а не затронутые сессии: событие несёт id размещений, и превращать их
     * в сессии значило бы сходить в БД на пути, который случается редко и всегда следует за
     * изменением master-данных. Лишняя пересборка стоит 130 мс — дешевле, чем ошибка.</p>
     */
    @EventListener
    public void onProjectionStale(ProjectionStaleEvent event) {
        int dropped = evictAll();
        if (dropped > 0) {
            log.info("Проекция устарела ({}) — сброшено снимков: {}", event.getSource(), dropped);
        }
    }

    /** Сбросить всё; возвращает число выброшенных снимков. */
    public int evictAll() {
        synchronized (cache) {
            int size = cache.size();
            cache.clear();
            return size;
        }
    }

    private Entry get(UUID sessionId) {
        synchronized (cache) {
            return cache.get(sessionId);
        }
    }

    private void put(UUID sessionId, Entry entry) {
        synchronized (cache) {
            cache.put(sessionId, entry);
        }
    }

    private ReentrantLock lockFor(UUID sessionId) {
        return stripes[Math.floorMod(sessionId.hashCode(), LOCK_STRIPES)];
    }

    /** Снимок сессии вместе с признаками, по которым решается его годность. */
    private record Entry(long version, long constraintsGeneration, long builtAtNanos,
                         RecreatedWorkspace workspace) {}
}
