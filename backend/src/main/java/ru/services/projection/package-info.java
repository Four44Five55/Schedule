/**
 * Целостность read-модели: чтобы снимок расписания ({@code schedule_view}) не расходился с
 * размещениями, которыми владеет Command Side.
 *
 * <p>{@code ProjectionMaintenance} — единственная дверь «мои данные изменились»;
 * {@code ProjectionSource} — <b>Strategy</b> как enum «от изменившейся сущности → к затронутым
 * размещениям»; {@code ProjectionHealthService} — сверка Command ↔ Query для баннера на дашборде.</p>
 *
 * <p><b>Мутатор говорит <i>что</i> изменилось, а не <i>как</i> это отражается на расписании.</b>
 * Доменные сервисы не видят ни {@code ScheduleViewRepository}, ни событий (DIP); единственный
 * писатель снимка — {@code ScheduleSynchronizer}.</p>
 *
 * <p><b>Обратная поломка невозможна схемой:</b> строка снимка не переживает своё размещение
 * ({@code schedule_view.placement_id → lesson_placement ON DELETE CASCADE}, миграция 017) — до неё
 * каскады БД шли мимо Hibernate и оставляли занятия-призраки.</p>
 */
package ru.services.projection;
