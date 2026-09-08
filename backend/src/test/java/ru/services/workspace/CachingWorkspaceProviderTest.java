package ru.services.workspace;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.events.ProjectionStaleEvent;
import ru.repository.write.ScheduleSessionRepository;
import ru.services.WorkspaceRecreationService;
import ru.services.WorkspaceRecreationService.RecreatedWorkspace;
import ru.services.constraints.AllConstraints;
import ru.services.projection.ProjectionSource;
import ru.services.solver.ScheduleWorkspace;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Кэш workspace: когда снимок переиспользуется, а когда обязан собраться заново.
 *
 * <p>Предмет проверки — <b>правила годности</b>, потому что каждое из них закрывает то, чего не
 * видят остальные, и ошибка любого молчалива: подсветка покажет ячейку свободной, хотя она занята.
 * Версия сессии не замечает правок ограничений (они не привязаны к сессии) и каскадов БД (мутация
 * идёт мимо Hibernate); срок годности страхует от второго инстанса, чьи события до нас не долетают.
 * Поэтому здесь каждый признак проверяется отдельным тестом, а не «в целом».</p>
 *
 * <p>Считается именно число сборок: смысл кэша в том, что дорогой сборки <b>не произошло</b>.</p>
 */
class CachingWorkspaceProviderTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID OTHER_SESSION = UUID.randomUUID();

    private final ScheduleSessionRepository sessions = mock(ScheduleSessionRepository.class);
    private final WorkspaceRecreationService recreation = mock(WorkspaceRecreationService.class);
    private final CountingProvider delegate = new CountingProvider();
    private final AtomicLong clock = new AtomicLong();

    private final CachingWorkspaceProvider provider =
            new CachingWorkspaceProvider(delegate, sessions, recreation, clock::get);

    @Test
    @DisplayName("Версия не менялась — снимок берётся из кэша, сборки не было")
    void reusesSnapshotWhileVersionIsUnchanged() {
        givenVersion(SESSION, 7L);

        provider.withWorkspaceOfSession(SESSION, workspace -> "первый");
        provider.withWorkspaceOfSession(SESSION, workspace -> "второй");

        assertThat(delegate.builds(SESSION)).isEqualTo(1);
    }

    @Test
    @DisplayName("Версия выросла (кто-то передвинул занятие) — снимок пересобирается")
    void rebuildsWhenVersionGrows() {
        givenVersion(SESSION, 7L);
        provider.withWorkspaceOfSession(SESSION, workspace -> "первый");

        givenVersion(SESSION, 8L);
        provider.withWorkspaceOfSession(SESSION, workspace -> "второй");

        assertThat(delegate.builds(SESSION)).isEqualTo(2);
    }

    @Test
    @DisplayName("Правка ограничений сбрасывает снимок — версия сессии её не замечает")
    void rebuildsWhenConstraintsChange() {
        givenVersion(SESSION, 7L);
        provider.withWorkspaceOfSession(SESSION, workspace -> "первый");

        new ConstraintChangeListener().onConstraintChanged(new Object()); // командировку завели

        provider.withWorkspaceOfSession(SESSION, workspace -> "второй");

        assertThat(delegate.builds(SESSION))
                .as("иначе подсветка покажет свободной ячейку, которую только что закрыли")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Просроченный снимок пересобирается — страховка от того, чего не видит версия")
    void rebuildsWhenSnapshotExpires() {
        givenVersion(SESSION, 7L);
        provider.withWorkspaceOfSession(SESSION, workspace -> "первый");

        clock.addAndGet(CachingWorkspaceProvider.TTL.plus(Duration.ofSeconds(1)).toNanos());
        provider.withWorkspaceOfSession(SESSION, workspace -> "второй");

        assertThat(delegate.builds(SESSION)).isEqualTo(2);
    }

    @Test
    @DisplayName("Неполный снимок не кэшируется — каждый запрос собирает заново")
    void doesNotCacheIncompleteSnapshot() {
        givenVersion(SESSION, 7L);
        delegate.seedFailures = 1; // одна битая ссылка: занятие в снимок не попало

        provider.withWorkspaceOfSession(SESSION, workspace -> "первый");
        provider.withWorkspaceOfSession(SESSION, workspace -> "второй");

        assertThat(delegate.builds(SESSION))
                .as("снимок без занятия отвечает «свободно» там, где занято; в кэше эта ошибка "
                        + "жила бы минуту и обслуживала всех читателей сессии")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Посев починился — снимок снова кэшируется")
    void cachesAgainOnceSeedingSucceeds() {
        givenVersion(SESSION, 7L);
        delegate.seedFailures = 1;
        provider.withWorkspaceOfSession(SESSION, workspace -> "первый");

        delegate.seedFailures = 0; // битую строку починили в данных
        provider.withWorkspaceOfSession(SESSION, workspace -> "второй");
        provider.withWorkspaceOfSession(SESSION, workspace -> "третий");

        assertThat(delegate.builds(SESSION))
                .as("отказ кэшировать неполный снимок не должен выключать кэш навсегда")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Устаревшая проекция (каскад БД мимо Hibernate) сбрасывает всё")
    void projectionStaleEventEvictsEverything() {
        givenVersion(SESSION, 7L);
        givenVersion(OTHER_SESSION, 3L);
        provider.withWorkspaceOfSession(SESSION, workspace -> "первый");
        provider.withWorkspaceOfSession(OTHER_SESSION, workspace -> "первый");

        provider.onProjectionStale(new ProjectionStaleEvent(ProjectionSource.EDUCATOR, List.of(UUID.randomUUID())));

        provider.withWorkspaceOfSession(SESSION, workspace -> "второй");
        provider.withWorkspaceOfSession(OTHER_SESSION, workspace -> "второй");

        assertThat(delegate.builds(SESSION)).isEqualTo(2);
        assertThat(delegate.builds(OTHER_SESSION))
                .as("сброс общий: событие несёт размещения, а не сессии, и разрешать их — лишний запрос")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Снимки разных сессий не мешают друг другу")
    void sessionsAreIndependent() {
        givenVersion(SESSION, 7L);
        givenVersion(OTHER_SESSION, 3L);

        provider.withWorkspaceOfSession(SESSION, workspace -> "с");
        provider.withWorkspaceOfSession(OTHER_SESSION, workspace -> "д");
        provider.withWorkspaceOfSession(SESSION, workspace -> "с");
        provider.withWorkspaceOfSession(OTHER_SESSION, workspace -> "д");

        assertThat(delegate.builds(SESSION)).isEqualTo(1);
        assertThat(delegate.builds(OTHER_SESSION)).isEqualTo(1);
    }

    @Test
    @DisplayName("Сессий больше вместимости — вытесняется самая давняя, а не случайная")
    void evictsLeastRecentlyUsed() {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i <= CachingWorkspaceProvider.MAX_SESSIONS; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            givenVersion(id, 1L);
            provider.withWorkspaceOfSession(id, workspace -> "прогрев");
        }

        // Первая сессия вытеснена — к ней обращались раньше всех.
        provider.withWorkspaceOfSession(ids.get(0), workspace -> "снова");
        assertThat(delegate.builds(ids.get(0))).isEqualTo(2);

        // Последняя — ещё в кэше.
        provider.withWorkspaceOfSession(ids.get(ids.size() - 1), workspace -> "снова");
        assertThat(delegate.builds(ids.get(ids.size() - 1))).isEqualTo(1);
    }

    @Test
    @DisplayName("Снимок не пережил транзакцию — пересобирается и чтение повторяется, а не падает")
    void detachedSnapshotIsRebuiltAndRetried() {
        givenVersion(SESSION, 7L);
        provider.withWorkspaceOfSession(SESSION, workspace -> "прогрев");

        AtomicInteger attempts = new AtomicInteger();
        String result = provider.withWorkspaceOfSession(SESSION, workspace -> {
            if (attempts.incrementAndGet() == 1) {
                throw new LazyInitializationException("could not initialize proxy — no Session");
            }
            return "посчитано на свежем снимке";
        });

        assertThat(result).isEqualTo("посчитано на свежем снимке");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(delegate.builds(SESSION)).isEqualTo(2);
    }

    @Test
    @DisplayName("Свежий снимок повторно не пересобирается — иначе настоящий баг ушёл бы в бесконечность")
    void freshSnapshotFailureIsNotRetried() {
        givenVersion(SESSION, 7L);

        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> provider.withWorkspaceOfSession(SESSION, workspace -> {
            attempts.incrementAndGet();
            throw new LazyInitializationException("проблема не в свежести снимка");
        })).isInstanceOf(LazyInitializationException.class);

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(delegate.builds(SESSION)).isEqualTo(1);
    }

    @Test
    @DisplayName("Сессии у размещения нет (сняли, пока клиент спрашивал) — идём мимо кэша")
    void unknownPlacementGoesStraightToRebuild() {
        UUID placement = UUID.randomUUID();
        when(recreation.sessionIdOfPlacement(placement)).thenReturn(null);

        String answer = provider.withWorkspaceOfPlacement(placement, workspace -> "пусто");

        assertThat(answer).isEqualTo("пусто");
        assertThat(delegate.placementBuilds()).isEqualTo(1);
    }

    @Test
    @DisplayName("Подбор по размещению попадает в снимок его сессии")
    void placementUsesItsSessionSnapshot() {
        UUID placement = UUID.randomUUID();
        when(recreation.sessionIdOfPlacement(placement)).thenReturn(SESSION);
        givenVersion(SESSION, 7L);

        provider.withWorkspaceOfSession(SESSION, workspace -> "прогрев");
        provider.withWorkspaceOfPlacement(placement, workspace -> "подбор");

        assertThat(delegate.builds(SESSION))
                .as("подбор по размещению и палитра по сессии делят один снимок")
                .isEqualTo(1);
    }

    private void givenVersion(UUID sessionId, long version) {
        when(sessions.findVersionById(sessionId)).thenReturn(java.util.Optional.of(version));
    }

    /** Базовый провайдер, который считает, сколько раз его просили построить снимок. */
    private static class CountingProvider implements WorkspaceProvider {

        private final Map<UUID, Integer> buildsBySession = new HashMap<>();
        private int placementBuilds;

        @Override
        public <T> T withWorkspaceOfSession(UUID sessionId, Function<RecreatedWorkspace, T> body) {
            buildsBySession.merge(sessionId, 1, Integer::sum);
            return body.apply(workspace());
        }

        @Override
        public <T> T withWorkspaceOfPlacement(UUID placementId, Function<RecreatedWorkspace, T> body) {
            placementBuilds++;
            return body.apply(workspace());
        }

        int builds(UUID sessionId) {
            return buildsBySession.getOrDefault(sessionId, 0);
        }

        int placementBuilds() {
            return placementBuilds;
        }

        /** Сколько размещений «не восстановилось» в собираемых снимках; 0 — снимок полон. */
        int seedFailures = 0;

        private RecreatedWorkspace workspace() {
            ScheduleWorkspace workspace = new ScheduleWorkspace(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    List.of(), List.of(), List.of(), new AllConstraints(Map.of(), Map.of(), Map.of()));
            return new RecreatedWorkspace(workspace, Map.of(), seedFailures);
        }
    }
}
