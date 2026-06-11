import React from 'react';
import { cn } from '../../utils/cn';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  title?: string;
  subtitle?: string;
  footer?: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({ children, className, title, subtitle, footer }) => {
  return (
    <div className={cn('bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden flex flex-col', className)}>
      {(title || subtitle) && (
        <div className="px-6 py-4 border-b border-slate-50">
          {title && <h3 className="text-lg font-bold text-slate-800">{title}</h3>}
          {subtitle && <p className="text-sm text-slate-500">{subtitle}</p>}
        </div>
      )}
      <div className="p-6 flex-1">{children}</div>
      {footer && (
        <div className="px-6 py-3 bg-slate-50 border-t border-slate-100 mt-auto text-sm">{footer}</div>
      )}
    </div>
  );
};
