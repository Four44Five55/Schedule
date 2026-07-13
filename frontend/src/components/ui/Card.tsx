import React from 'react';
import { cn } from '../../utils/cn';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  title?: string;
  subtitle?: string;
  footer?: React.ReactNode;
  headerActions?: React.ReactNode;
  /** Отступы содержимого. По умолчанию `p-6`; плотным таблицам воздух между шапкой и данными мешает. */
  bodyClassName?: string;
}

export const Card: React.FC<CardProps> = ({
  children, className, title, subtitle, footer, headerActions, bodyClassName,
}) => {
  return (
    <div className={cn('bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden flex flex-col', className)}>
      {(title || subtitle) && (
        <div className="px-6 py-3 border-b border-slate-50 flex items-center justify-between gap-3">
          <div>
            {title && <h3 className="text-lg font-bold text-slate-800">{title}</h3>}
            {subtitle && <p className="text-sm text-slate-500">{subtitle}</p>}
          </div>
          {headerActions && <div className="shrink-0">{headerActions}</div>}
        </div>
      )}
      <div className={cn('flex-1', bodyClassName ?? 'p-6')}>{children}</div>
      {footer && (
        <div className="px-6 py-3 bg-slate-50 border-t border-slate-100 mt-auto text-sm">{footer}</div>
      )}
    </div>
  );
};
