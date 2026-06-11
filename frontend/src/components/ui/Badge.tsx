import React from 'react';
import { cn } from '../../utils/cn';

interface BadgeProps {
  children: React.ReactNode;
  variant?: 'blue' | 'emerald' | 'amber' | 'red' | 'purple' | 'slate';
  className?: string;
}

const variants = {
  blue: 'bg-blue-50 text-blue-700 border-blue-100',
  emerald: 'bg-emerald-50 text-emerald-700 border-emerald-100',
  amber: 'bg-amber-50 text-amber-700 border-amber-100',
  red: 'bg-red-50 text-red-700 border-red-100',
  purple: 'bg-purple-50 text-purple-700 border-purple-100',
  slate: 'bg-slate-50 text-slate-700 border-slate-100',
};

export const Badge: React.FC<BadgeProps> = ({ children, variant = 'slate', className }) => {
  return (
    <span
      className={cn(
        'px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider border',
        variants[variant],
        className,
      )}
    >
      {children}
    </span>
  );
};
