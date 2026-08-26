import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, RotateCw, X } from 'lucide-react';
import { errorMessage } from '../services/apiError';

/**
 * Место показа для отказа, у которого своего места нет.
 *
 * <p>Правило проекта: <b>место выбирает компонент</b> — он один знает, было ли действие
 * переднего плана. У поля формы есть подпись под полем, у формы — плашка, у экрана — состояние
 * «не загрузилось, повторить». Тост нужен там, где ничего этого нет: действие сделано не в форме
 * (закрепить занятие, выгрузить бланк, снять размещение) или отказала фоновая подгрузка, чей
 * провал молча превращается в <b>неправдивый</b> экран — «вариантов переноса нет» вместо «их не
 * удалось спросить».</p>
 *
 * <h3>Почему отказ не гаснет сам</h3>
 * Успех гасится через несколько секунд: его достаточно заметить краем глаза. Отказ ждёт закрытия
 * — человек мог смотреть в другую часть экрана, а сообщение об ошибке, исчезнувшее непрочитанным,
 * равносильно его отсутствию. Единственное исключение — кнопка «Повторить»: после неё тост
 * снимается сам, потому что действие уже началось.
 */

export type ToastKind = 'error' | 'success' | 'info';

export interface ToastAction {
    label: string;
    run: () => void;
}

interface Toast {
    id: number;
    kind: ToastKind;
    message: string;
    action?: ToastAction;
}

interface ToastApi {
    /** Отказ по исключению: текст берётся у сервера через общую дверь, своя фраза — запасная. */
    failure: (err: unknown, fallback: string, action?: ToastAction) => void;
    /**
     * Отказ готовым текстом — когда исключения нет.
     *
     * <p>Отдельный метод, а не {@code failure(null, text)}: у того первый параметр означает
     * «разбери вот это», и передавать туда пустоту, чтобы получить второй параметр, — способ
     * написать непонятно. Живёт столько же, сколько {@code failure}: не гаснет сам.</p>
     */
    error: (message: string, action?: ToastAction) => void;
    /** Готово: гаснет само. */
    success: (message: string) => void;
    /** Нейтральное сообщение: гаснет само. */
    info: (message: string) => void;
    dismiss: (id: number) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/** Сколько живёт сообщение, которое не требует решения. */
const AUTO_DISMISS_MS = 4500;

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const nextId = useRef(1);

    const dismiss = useCallback((id: number) => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
    }, []);

    const push = useCallback((kind: ToastKind, message: string, action?: ToastAction) => {
        const id = nextId.current++;
        // Одинаковые сообщения не копятся столбиком: подряд идущие отказы одной подгрузки
        // (сетка спрашивает варианты на каждый клик) — это одна новость, а не десять.
        setToasts((prev) => [...prev.filter((t) => t.message !== message), { id, kind, message, action }]);
        if (kind !== 'error') {
            setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
        }
    }, [dismiss]);

    const api = useMemo<ToastApi>(() => ({
        failure: (err, fallback, action) => push('error', errorMessage(err, fallback), action),
        error: (message, action) => push('error', message, action),
        success: (message) => push('success', message),
        info: (message) => push('info', message),
        dismiss,
    }), [push, dismiss]);

    return (
        <ToastContext.Provider value={api}>
            {children}
            <ToastStack toasts={toasts} onDismiss={dismiss} />
        </ToastContext.Provider>
    );
};

/**
 * Доступ к показу сообщений.
 *
 * <p><b>Бросает, а не отдаёт заглушку</b> — так же, как {@code usePeriod}. Заглушка выглядела
 * бережнее («сообщение об ошибке не должно само стать второй ошибкой»), но берегла от того, чего
 * не бывает: провайдер смонтирован в корне {@code main.tsx}, и его отсутствие — дефект сборки, а
 * не состояние времени выполнения. Хук зовётся в начале рендера, а не внутри {@code catch}, так
 * что отказ виден сразу и везде — вместо того чтобы навсегда спрятаться в консоли. Два контракта
 * на один приём в одном проекте хуже любого из них.</p>
 */
export const useToast = (): ToastApi => {
    const api = useContext(ToastContext);
    if (!api) throw new Error('useToast должен вызываться внутри <ToastProvider>');
    return api;
};

const KIND_STYLES: Record<ToastKind, { box: string; icon: React.ReactNode }> = {
    error: {
        box: 'bg-red-50 border-red-200 text-red-800',
        icon: <AlertCircle size={18} className="shrink-0 mt-0.5 text-red-600" />,
    },
    success: {
        box: 'bg-emerald-50 border-emerald-200 text-emerald-800',
        icon: <CheckCircle2 size={18} className="shrink-0 mt-0.5 text-emerald-600" />,
    },
    info: {
        box: 'bg-slate-50 border-slate-200 text-slate-800',
        icon: <Info size={18} className="shrink-0 mt-0.5 text-slate-500" />,
    },
};

const ToastStack: React.FC<{ toasts: Toast[]; onDismiss: (id: number) => void }> = ({ toasts, onDismiss }) => {
    if (toasts.length === 0) return null;

    return (
        // z-[60] — выше модалок (z-50): отказ команды, запущенной из модального окна, обязан быть
        // виден поверх него, иначе сообщение приезжает в пустоту.
        <div className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 w-[min(28rem,calc(100vw-2rem))]">
            {toasts.map((t) => {
                const style = KIND_STYLES[t.kind];
                return (
                    <div
                        key={t.id}
                        role={t.kind === 'error' ? 'alert' : 'status'}
                        className={`flex items-start gap-2 p-3 border rounded-xl shadow-lg text-sm ${style.box}`}
                    >
                        {style.icon}
                        <span className="flex-1 break-words">{t.message}</span>
                        {t.action && (
                            <button
                                type="button"
                                onClick={() => { onDismiss(t.id); t.action!.run(); }}
                                className="shrink-0 inline-flex items-center gap-1 px-2 py-1 rounded-lg font-bold
                                           bg-white/70 hover:bg-white transition-colors"
                            >
                                <RotateCw size={13} />
                                {t.action.label}
                            </button>
                        )}
                        <button
                            type="button"
                            onClick={() => onDismiss(t.id)}
                            aria-label="Закрыть"
                            className="shrink-0 p-0.5 rounded hover:bg-black/5 transition-colors"
                        >
                            <X size={15} />
                        </button>
                    </div>
                );
            })}
        </div>
    );
};
