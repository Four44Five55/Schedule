import { useEffect } from 'react';
import { apiError } from '../services/apiError';
import { useToast } from './ToastContext';

/**
 * Последняя сетка под отказы, которые никто не поймал.
 *
 * <p><b>Зачем.</b> Сервисный слой перестал глушить чтения (двадцать `.catch(() => [])` сняты
 * 2026-08-26), и это правильно: решать, что показать, должен тот, кто знает экран. Но теперь
 * пропущенный обработчик даёт не пустой список, а <b>unhandled rejection</b> — то есть возвращает
 * ровно ту тишину, ради устранения которой всё делалось, только этажом выше. Места вызова
 * проверены руками; ручная проверка не гарантия, а следующая правка добавит новый вызов.</p>
 *
 * <p>Это <b>не</b> замена местному показу и не повод его не писать: тост здесь говорит «что-то
 * отвалилось и никто этого не ждал», без привязки к действию. Появление такого тоста — сигнал,
 * что в конкретном месте не хватает своего обработчика.</p>
 *
 * <p>Событие не гасится ({@code preventDefault} не зовём): пусть браузер по-прежнему печатает
 * отказ в консоль со стеком — тост его не заменяет, а дополняет.</p>
 */
export const UnhandledFailureWatch: React.FC = () => {
    const toast = useToast();

    useEffect(() => {
        const onRejection = (event: PromiseRejectionEvent) => {
            const failure = apiError(event.reason);
            // Сообщение сервера показываем, если оно есть: отказ, до которого не дошли руки,
            // всё равно чаще всего осмысленный — просто его некому было показать.
            toast.error(failure.status != null || failure.network
                ? failure.message
                : 'Действие не завершилось, и причина не показана. Обновите данные и повторите.');
            console.error('Необработанный отказ:', event.reason);
        };

        window.addEventListener('unhandledrejection', onRejection);
        return () => window.removeEventListener('unhandledrejection', onRejection);
    }, [toast]);

    return null;
};
