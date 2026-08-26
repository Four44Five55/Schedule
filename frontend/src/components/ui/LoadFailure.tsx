import React from 'react';
import { AlertTriangle, RotateCw } from 'lucide-react';
import { cn } from '../../utils/cn';

interface LoadFailureProps {
    /** Что именно не загрузилось — «Не удалось загрузить расписание». */
    title: string;
    /** Причина с сервера (через `errorMessage`). */
    message: string;
    onRetry?: () => void;
    /** Идёт повторная попытка — кнопка гасится, чтобы не наплодить запросов. */
    retrying?: boolean;
    className?: string;
}

/**
 * Экран (или крупная секция), который не загрузился.
 *
 * <p>Отличие от {@link ErrorBanner}: плашка живёт <b>рядом</b> с содержимым, а это состояние стоит
 * <b>вместо</b> него. Пустой экран без объяснения читается как «данных нет» — то есть врёт: разница
 * между «занятий не заведено» и «список не спросили» для человека решающая, а выглядят они
 * одинаково.</p>
 *
 * <p>Кнопка «Повторить» здесь обязательна по смыслу: отказ загрузки — обычно сеть или перезапуск
 * бэкенда, и второй попытки чаще всего хватает. Без неё единственный выход — F5, теряющий
 * состояние экрана.</p>
 */
export const LoadFailure: React.FC<LoadFailureProps> = ({
    title,
    message,
    onRetry,
    retrying = false,
    className,
}) => (
    <div
        role="alert"
        className={cn(
            'flex flex-col items-center justify-center gap-3 py-12 px-6 text-center',
            className,
        )}
    >
        <div className="p-3 rounded-2xl bg-red-100 text-red-600">
            <AlertTriangle size={24} />
        </div>
        <div className="space-y-1">
            <h3 className="text-base font-black text-slate-900">{title}</h3>
            <p className="text-sm text-slate-600 max-w-md break-words">{message}</p>
        </div>
        {onRetry && (
            <button
                type="button"
                onClick={onRetry}
                disabled={retrying}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold
                           bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-50 transition-colors"
            >
                <RotateCw size={15} className={retrying ? 'animate-spin' : undefined} />
                {retrying ? 'Повторяем…' : 'Повторить'}
            </button>
        )}
    </div>
);
