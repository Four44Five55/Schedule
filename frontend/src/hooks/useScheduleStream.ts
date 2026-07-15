import { useEffect, useRef } from 'react';

/** «Звонок» с бэка: расписание сессии изменилось и УЖЕ спроецировано — можно перечитывать. */
export interface ScheduleChangedEvent {
  sessionId: string;
  version: number | null;
}

/**
 * Подписка вкладки на живые изменения расписания (Server-Sent Events).
 *
 * <p>Закрывает то, чего в приложении не было вообще: вкладка жила со снимком, снятым при загрузке,
 * и о чужих правках узнавала только получив 409 на собственной команде. Двое диспетчеров видели
 * разные расписания, пока кто-нибудь не нажимал F5.</p>
 *
 * <p><b>Событие приходит ПОСЛЕ записи read-модели</b> (его публикует единственный писатель
 * `schedule_view`), поэтому оно означает буквально «данные готовы». Именно поэтому оно заменяет
 * фиксированные паузы `setTimeout(1000)`, которыми фронт вслепую пережидал асинхронную проекцию:
 * теперь ждать не нужно — нам скажут.</p>
 *
 * <p>Переподключение делает сам `EventSource` (в т.ч. после таймаута соединения на сервере),
 * поэтому ни поллинга, ни heartbeat тут нет.</p>
 *
 * @param sessionId сессия расписания; `undefined` — не подписываемся
 * @param onChanged что делать по звонку (перечитать данные, подхватить версию)
 * @returns ref «соединение живо» — по нему хост решает, нужен ли запасной `setTimeout`
 */
export function useScheduleStream(
  sessionId: string | undefined,
  onChanged: (event: ScheduleChangedEvent) => void
) {
  // Колбэк держим в ref: иначе каждый новый рендер хоста пересоздавал бы EventSource.
  const handler = useRef(onChanged);
  handler.current = onChanged;

  const connected = useRef(false);

  useEffect(() => {
    if (!sessionId) return;

    const source = new EventSource(`/api/schedule/query/stream/${sessionId}`);

    // Пачки событий СХЛОПЫВАЕМ, но с «передним фронтом»: первое событие уходит хосту СРАЗУ, а
    // всё, что прилетело следом в окне COALESCE_MS, схлопывается в один хвостовой вызов.
    //
    // Почему не просто debounce: массовые операции публикуют событие на КАЖДОЕ размещение
    // («очистить всё» — сотни звонков), и без гашения хост столько же раз перечитал бы сетку
    // (~1.35 МБ). Но чистый debounce наказывал бы и обычное одиночное действие — добавлял бы
    // задержку на ровном месте, а именно на неё жалуются после отказа от setTimeout(1000).
    // Передний фронт даёт: одиночное действие → мгновенно; шквал → ровно два вызова.
    let cooldown: number | undefined;
    let pending: ScheduleChangedEvent | null = null;

    source.onopen = () => { connected.current = true; };
    source.onerror = () => {
      // Обрыв — норма (таймаут соединения, перезапуск бэка). EventSource переподключится сам;
      // пока он не открыт, хосты вернутся к запасной паузе.
      connected.current = false;
    };
    source.addEventListener('schedule-changed', (e) => {
      let event: ScheduleChangedEvent;
      try {
        event = JSON.parse((e as MessageEvent).data) as ScheduleChangedEvent;
      } catch {
        return; // битое тело: следующая команда всё равно сверится по версии (409)
      }

      if (cooldown === undefined) {
        handler.current(event);          // передний фронт — без задержки
      } else {
        pending = event;                 // идёт шквал — копим последнее
      }

      if (cooldown !== undefined) window.clearTimeout(cooldown);
      cooldown = window.setTimeout(() => {
        cooldown = undefined;
        if (pending) {
          const last = pending;
          pending = null;
          handler.current(last);         // хвост шквала: одна финальная перезагрузка
        }
      }, COALESCE_MS);
    });

    return () => {
      if (cooldown !== undefined) window.clearTimeout(cooldown);
      source.close();
      connected.current = false;
    };
  }, [sessionId]);

  return connected;
}

/**
 * Окно схлопывания шквала событий. На одиночное действие НЕ влияет (оно проходит передним
 * фронтом); нужно только чтобы массовые операции («очистить всё» — сотни событий) не вызывали
 * сотни перезагрузок.
 */
const COALESCE_MS = 400;
