// ============ CONFLICT ALERT COMPONENT ============
// Компонент для отображения конфликтов оптимистичной блокировки

import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { ConflictResponse } from '../../../types/cqrs';

interface ConflictAlertProps {
  conflict: ConflictResponse;
  onReload: () => void;
  className?: string;
}

/**
 * Компонент для отображения конфликта версий (optimistic lock)
 *
 * Показывает пользователю информацию о конфликте и предлагает решение.
 * Конфликт возникает, когда два пользователя одновременно редактируют одну сессию.
 */
export const ConflictAlert: React.FC<ConflictAlertProps> = ({
  conflict,
  onReload,
  className = ''
}) => {
  return (
    <div className={`p-4 bg-red-50 border-2 border-red-200 rounded-xl ${className}`}>
      <div className="flex items-start gap-3">
        <div className="p-2 bg-red-100 rounded-lg">
          <AlertTriangle size={20} className="text-red-600" />
        </div>

        <div className="flex-1 space-y-2">
          <h3 className="text-sm font-black text-red-900 uppercase tracking-tight">
            ⚠️ Конфликт версий
          </h3>

          <p className="text-sm font-medium text-red-800">
            {conflict.message}
          </p>

          <div className="flex items-center justify-between gap-4">
            <p className="text-xs text-red-700">
              Актуальная версия на сервере:{' '}
              <span className="font-black text-red-900 text-lg">{conflict.currentVersion}</span>
            </p>

            <button
              onClick={onReload}
              className="px-4 py-2 bg-red-600 text-white text-xs font-bold rounded-lg hover:bg-red-700 transition-colors flex items-center gap-2"
            >
              <RefreshCw size={14} />
              Обновить данные
            </button>
          </div>

          <p className="text-xs text-red-600 italic">
            💡 Конфликт возник из-за одновременного редактирования. Обновите данные и повторите действие.
          </p>
        </div>
      </div>
    </div>
  );
};

/**
 * Компактная версия для уведомлений
 */
interface ConflictBadgeProps {
  conflict: ConflictResponse;
  onClick: () => void;
}

export const ConflictBadge: React.FC<ConflictBadgeProps> = ({ conflict, onClick }) => {
  return (
    <button
      onClick={onClick}
      className="px-3 py-1.5 bg-red-100 border-2 border-red-300 rounded-lg hover:bg-red-200 transition-colors flex items-center gap-2 cursor-pointer"
    >
      <AlertTriangle size={14} className="text-red-600" />
      <span className="text-xs font-bold text-red-900">
        Конфликт (v{conflict.currentVersion})
      </span>
    </button>
  );
};
