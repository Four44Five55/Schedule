package ru.services.session;

import ru.entity.write.ScheduleSession;

import java.util.List;
import java.util.Optional;

/**
 * Какую сессию показывать и править на экране расписания.
 *
 * <h2>Правило</h2>
 * <ol>
 *   <li>сессия <b>этого</b> периода — если есть;</li>
 *   <li>иначе сессия <b>без периода</b>: такие сессии глобальны и держат расписание, заведённое до
 *       разделения по периодам, — терять их нельзя;</li>
 *   <li>сессия <b>чужого</b> периода не берётся никогда.</li>
 * </ol>
 *
 * <p><b>Почему третий пункт важен.</b> Сессий в базе много, и самой свежей запросто оказывается
 * импортная (импорт создаёт новую на каждый прогон). Взять её на экран другого периода значит
 * считать находки по чужому расписанию, а перегенерацию, замок и перенос направить в чужую сессию —
 * и всё это молча.</p>
 *
 * <p>Отсюда и отдельная чистая функция: <b>сессия принадлежит периоду</b> — правило предметной
 * области, а не деталь выборки. По образцу {@code OrgUnitHierarchyRule} и {@code AuditoriumUsageRule}
 * тестируется без базы, а сервис остаётся сборочным слоем.</p>
 */
public final class EditableSessionChoice {

    private EditableSessionChoice() {
    }

    /**
     * Выбирает сессию экрана.
     *
     * @param candidates сессии с размещениями, <b>отсортированные по свежести</b> (свежая первой):
     *                   порядок задаёт вызывающий, правило его только уважает
     * @param periodId   период экрана; {@code null} — период не выбран, и берётся самая свежая:
     *                   отбирать не по чему, а отказ был бы хуже
     * @return сессия либо пусто — расписания для этого периода ещё нет
     */
    public static Optional<ScheduleSession> pick(List<ScheduleSession> candidates, Integer periodId) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        if (periodId == null) {
            return Optional.of(candidates.get(0));
        }
        return candidates.stream()
                .filter(session -> session.getStudyPeriod() != null
                        && periodId.equals(session.getStudyPeriod().getId()))
                .findFirst()
                // Глобальная сессия (без периода) — единственный законный запасной вариант.
                .or(() -> candidates.stream()
                        .filter(session -> session.getStudyPeriod() == null)
                        .findFirst());
    }
}
