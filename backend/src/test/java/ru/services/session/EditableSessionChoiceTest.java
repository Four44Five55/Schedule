package ru.services.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.StudyPeriod;
import ru.entity.write.ScheduleSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты выбора сессии для экрана расписания.
 *
 * <p>Зафиксировано главное: <b>сессия чужого периода не берётся никогда</b>. Именно это и было
 * ошибкой — импорт создаёт новую сессию на каждый прогон, она становится самой свежей, и экран
 * любого периода связывался с ней.</p>
 */
class EditableSessionChoiceTest {

    private static ScheduleSession session(String name, Integer periodId) {
        ScheduleSession session = new ScheduleSession(name, "user");
        if (periodId != null) {
            StudyPeriod period = new StudyPeriod();
            period.setId(periodId);
            session.setStudyPeriod(period);
        }
        return session;
    }

    @Test
    @DisplayName("Берётся сессия своего периода, а не самая свежая")
    void sessionOfTheRequestedPeriodWins() {
        // Импортная сессия чужого периода стоит первой (она свежее) — и всё равно не берётся.
        List<ScheduleSession> candidates = List.of(
                session("Импорт: Осень 2024/2025", 9),
                session("Расписание: Осень 2025/2026", 5));

        assertThat(EditableSessionChoice.pick(candidates, 5))
                .get().extracting(ScheduleSession::getName).isEqualTo("Расписание: Осень 2025/2026");
    }

    @Test
    @DisplayName("Своего периода нет — берём легаси-сессию без периода, а не чужую")
    void legacySessionWithoutPeriodIsTheFallback() {
        // До сессий per-period расписание жило в глобальной сессии, и терять её нельзя.
        List<ScheduleSession> candidates = List.of(
                session("Импорт: Осень 2024/2025", 9),
                session("Легаси-расписание", null));

        assertThat(EditableSessionChoice.pick(candidates, 5))
                .get().extracting(ScheduleSession::getName).isEqualTo("Легаси-расписание");
    }

    @Test
    @DisplayName("Есть только чужая сессия — не берём ничего: пусто честнее подмены")
    void foreignSessionIsNeverTaken() {
        List<ScheduleSession> candidates = List.of(session("Импорт: Осень 2024/2025", 9));

        assertThat(EditableSessionChoice.pick(candidates, 5)).isEmpty();
    }

    @Test
    @DisplayName("Период не выбран — прежнее поведение: самая свежая")
    void withoutPeriodTheFreshestWins() {
        List<ScheduleSession> candidates = List.of(session("Свежая", 9), session("Старая", 5));

        assertThat(EditableSessionChoice.pick(candidates, null))
                .get().extracting(ScheduleSession::getName).isEqualTo("Свежая");
    }

    @Test
    @DisplayName("Кандидатов нет — пусто, а не исключение: расписания просто ещё нет")
    void noCandidatesGiveEmpty() {
        assertThat(EditableSessionChoice.pick(List.of(), 5)).isEmpty();
        assertThat(EditableSessionChoice.pick(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("Своего периода несколько — берётся первая, то есть самая свежая")
    void freshestOfTheOwnPeriodWins() {
        List<ScheduleSession> candidates = List.of(
                session("Импорт: свежий", 5), session("Импорт: прошлый", 5));

        assertThat(EditableSessionChoice.pick(candidates, 5))
                .get().extracting(ScheduleSession::getName).isEqualTo("Импорт: свежий");
    }
}
