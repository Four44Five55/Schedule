// ============ SESSION STATUS CARD COMPONENT ============
// Компонент для отображения статуса сессии расписания

import React from 'react';
import { CheckCircle, Clock, Archive, Loader2, FileText } from 'lucide-react';
import { ScheduleSessionDto, SessionStatus } from '../../../types/cqrs';
import { cn } from '../../../utils/cn';

interface SessionStatusCardProps {
  session: ScheduleSessionDto;
  className?: string;
}

/**
 * Конфигурация для каждого статуса сессии
 */
const STATUS_CONFIG: Record<SessionStatus, {
  label: string;
  description: string;
  bgColor: string;
  textColor: string;
  icon: typeof Clock;
  borderColor: string;
}> = {
  INITIALIZED: {
    label: 'Инициализирована',
    description: 'Сессия создана, ожидает генерации расписания',
    bgColor: 'bg-slate-100',
    textColor: 'text-slate-700',
    icon: Clock,
    borderColor: 'border-slate-300'
  },
  GENERATING: {
    label: 'Генерация...',
    description: 'Идёт генерация расписания, подождите...',
    bgColor: 'bg-yellow-100',
    textColor: 'text-yellow-800',
    icon: Loader2,
    borderColor: 'border-yellow-300'
  },
  READY_FOR_EDIT: {
    label: 'Готово к редактированию',
    description: 'Расписание сгенерировано и готово к редактированию',
    bgColor: 'bg-green-100',
    textColor: 'text-green-800',
    icon: CheckCircle,
    borderColor: 'border-green-300'
  },
  FINAL: {
    label: 'Финализировано',
    description: 'Расписание утверждено и закрыто для редактирования',
    bgColor: 'bg-blue-100',
    textColor: 'text-blue-800',
    icon: FileText,
    borderColor: 'border-blue-300'
  },
  ARCHIVED: {
    label: 'Архивирована',
    description: 'Сессия перемещена в архив',
    bgColor: 'bg-purple-100',
    textColor: 'text-purple-800',
    icon: Archive,
    borderColor: 'border-purple-300'
  }
};

/**
 * Компонент для отображения статуса сессии расписания
 *
 * Показывает текущий статус сессии с соответствующим цветом и иконкой.
 */
export const SessionStatusCard: React.FC<SessionStatusCardProps> = ({
  session,
  className = ''
}) => {
  const config = STATUS_CONFIG[session.status];
  const Icon = config.icon;

  return (
    <div className={cn(
      'p-4 rounded-xl border-2 transition-all',
      config.bgColor,
      config.borderColor,
      className
    )}>
      <div className="flex items-start gap-3">
        <div className={cn('p-2 rounded-lg', config.bgColor)}>
          <Icon
            size={24}
            className={cn(
              config.textColor,
              session.status === 'GENERATING' && 'animate-spin'
            )}
          />
        </div>

        <div className="flex-1 space-y-1">
          <div className="flex items-center gap-2">
            <h3 className={cn('text-sm font-black uppercase tracking-tight', config.textColor)}>
              {config.label}
            </h3>

            <span className={cn('px-2 py-0.5 rounded-full text-[9px] font-bold', config.bgColor, config.textColor)}>
              v{session.version}
            </span>
          </div>

          <p className={cn('text-xs font-medium', config.textColor)}>
            {config.description}
          </p>

          <div className="flex items-center gap-4 text-xs text-slate-600">
            <span className="font-mono">
              ID: {session.id.slice(0, 8)}...
            </span>
            <span>
              Занятий: <span className="font-bold">{session.placementsCount}</span>
            </span>
            <span>
              Создан: {new Date(session.createdAt).toLocaleDateString('ru-RU')}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * Компактный бейдж статуса
 */
interface SessionStatusBadgeProps {
  status: SessionStatus;
  className?: string;
}

export const SessionStatusBadge: React.FC<SessionStatusBadgeProps> = ({
  status,
  className = ''
}) => {
  const config = STATUS_CONFIG[status];
  const Icon = config.icon;

  return (
    <div className={cn(
      'px-3 py-1.5 rounded-lg border flex items-center gap-2',
      config.bgColor,
      config.borderColor,
      className
    )}>
      <Icon size={14} className={cn(config.textColor, status === 'GENERATING' && 'animate-spin')} />
      <span className={cn('text-[10px] font-black uppercase tracking-tighter', config.textColor)}>
        {config.label}
      </span>
    </div>
  );
};

/**
 * Детальная информация о сессии
 */
export const SessionInfo: React.FC<{ session: ScheduleSessionDto; className?: string }> = ({
  session,
  className = ''
}) => {
  return (
    <div className={cn('space-y-2 text-xs', className)}>
      <div className="grid grid-cols-2 gap-2">
        <div className="font-medium text-slate-600">Название:</div>
        <div className="font-bold text-slate-900">{session.name}</div>

        <div className="font-medium text-slate-600">Создал:</div>
        <div className="font-medium text-slate-900">{session.createdBy}</div>

        <div className="font-medium text-slate-600">Обновлено:</div>
        <div className="font-medium text-slate-900">
          {new Date(session.updatedAt).toLocaleString('ru-RU')}
        </div>

        <div className="font-medium text-slate-600">Версия:</div>
        <div className="font-black text-slate-900">{session.version}</div>

        <div className="font-medium text-slate-600">Занятий:</div>
        <div className="font-black text-slate-900">{session.placementsCount}</div>

        <div className="font-medium text-slate-600">Workspace:</div>
        <div className={cn('font-medium', session.hasWorkspaceSnapshot ? 'text-green-700' : 'text-slate-500')}>
          {session.hasWorkspaceSnapshot ? '✅ Есть' : '❌ Нет'}
        </div>
      </div>
    </div>
  );
};
