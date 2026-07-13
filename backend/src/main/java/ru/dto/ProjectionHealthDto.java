package ru.dto;

import java.util.UUID;

/**
 * Здоровье проекции периода: сходятся ли Command Side и Query Side.
 *
 * <p>Нужен потому, что проекция обновляется <b>асинхронно</b> и её сбой (упавший слушатель,
 * гонка, перезапуск в неудачный момент) виден только в логе — то есть на практике не виден никому.
 * Этот срез превращает молчаливое расхождение в число, которое можно показать в интерфейсе рядом
 * с кнопкой «Пересобрать read-модель».</p>
 *
 * <p>Строк-сирот (view без размещения) в списке нет намеренно: они невозможны по схеме —
 * {@code schedule_view → lesson_placement ON DELETE CASCADE} (миграция 017).</p>
 *
 * @param sessionId  живая сессия периода ({@code null} — сессии нет, проверять нечего)
 * @param placements сколько занятий стоит на Command Side (источник правды)
 * @param projected  сколько из них доехало до read-модели
 * @param missing    не спроецировано ({@code placements - projected}); &gt; 0 — расписание
 *                   отображается неполно, лечится перепроекцией
 */
public record ProjectionHealthDto(UUID sessionId, int placements, int projected, int missing) {}
