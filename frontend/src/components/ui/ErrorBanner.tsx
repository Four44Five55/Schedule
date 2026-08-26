import React from 'react';
import { AlertCircle, RotateCw, X } from 'lucide-react';
import { cn } from '../../utils/cn';

interface ErrorBannerProps {
    /** Текст с сервера (через `errorMessage`), либо своя фраза, если сервер ни при чём. */
    message: string;
    /** Показывается кнопкой «Повторить»: даёт человеку выход, а не только новость. */
    onRetry?: () => void;
    retryLabel?: string;
    /**
     * Крестик «закрыть». Нужен там, где плашка живёт в рабочей области и мешает работе после
     * прочтения — в форме её снимает следующая попытка сохранить, а на доске раскладки убрать
     * сообщение больше нечем.
     */
    onDismiss?: () => void;
    className?: string;
}

/**
 * Плашка отказа внутри формы или раздела — там, где место у сообщения есть своё.
 *
 * <p>Разметка ровно та, что уже была разложена по десятку форм руками
 * (<code>bg-red-50 / border-red-200 / AlertCircle</code>): это не новый вид, а один и тот же,
 * получивший имя. Расходиться копиям больше негде.</p>
 *
 * <p>Тост (<code>useToast</code>) — для отказов, у которых своего места нет: команда из сетки,
 * выгрузка, фоновая подгрузка. Здесь — то, что относится к видимому куску экрана.</p>
 */
export const ErrorBanner: React.FC<ErrorBannerProps> = ({
    message,
    onRetry,
    retryLabel = 'Повторить',
    onDismiss,
    className,
}) => (
    <div
        role="alert"
        className={cn(
            'flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700',
            className,
        )}
    >
        <AlertCircle size={18} className="shrink-0 mt-0.5" />
        <span className="flex-1 break-words">{message}</span>
        {onRetry && (
            <button
                type="button"
                onClick={onRetry}
                className="shrink-0 inline-flex items-center gap-1 px-2 py-1 rounded-lg font-bold
                           bg-white hover:bg-red-100 border border-red-200 transition-colors"
            >
                <RotateCw size={13} />
                {retryLabel}
            </button>
        )}
        {onDismiss && (
            <button
                type="button"
                onClick={onDismiss}
                aria-label="Закрыть"
                className="shrink-0 p-0.5 rounded text-red-400 hover:text-red-600 transition-colors"
            >
                <X size={14} />
            </button>
        )}
    </div>
);
