package ru.services.session;

import ru.entity.write.ScheduleSession;

import java.util.List;
import java.util.Optional;

/**
 * Какую сессию показывать и править на экране расписания.
 *
 * <h2>Почему это правило, а не строчка внутри сервиса</h2>
 * <p>Раньше выбор был «самая свежая сессия с размещениями» — по <b>всем</b> периодам сразу. Пока
 * сессия в базе была фактически одна, это работало. Импорт создаёт новую сессию на каждый прогон,
 * и самой свежей стала <b>импортная</b>: экран расписания любого периода связывался с ней. Следствия
 * тихие и все вредные — находки по аудиториям считались по чужой сессии, а перегенерация с
 * сохранением закреплённых, замок и перенос целились в неё же.</p>
 *
 * <p>Значит это не деталь выборки, а правило предметной области: <b>сессия принадлежит периоду</b>.
 * Отсюда и отдельная чистая функция — по образцу {@code OrgUnitHierarchyRule} и
 * {@code AuditoriumUsageRule}: тестируется без базы, а сервис остаётся сборочным слоем.</p>
 *
 * <h2>Правило</h2>
 * <ol>
 *   <li>сессия <b>этого</b> периода — если есть;</li>
 *   <li>иначе сессия <b>без периода</b> — легаси: до сессий per-period расписание жило в
 *       глобальной, и терять её нельзя;</li>
 *   <li>сессия <b>чужого</b> периода не берётся никогда — именно она и была ошибкой.</li>
 * </ol>
 */
public final class EditableSessionChoice {

    private EditableSessionChoice() {
    }

    /**
     * Выбирает сессию экрана.
     *
     * @param candidates сессии с размещениями, <b>отсортированные по свежести</b> (свежая первой):
     *                   порядок задаёт вызывающий, правило его только уважает
     * @param periodId   период экрана; {@code null} — период не выбран, работает прежнее «самая
     *                   свежая»: выбирать не из чего, и отказ был бы хуже
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
                .or(() -> candidates.stream()
                        .filter(session -> session.getStudyPeriod() == null)
                        .findFirst());
    }
}
