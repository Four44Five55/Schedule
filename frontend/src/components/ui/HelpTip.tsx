import React from 'react';
import { HelpCircle } from 'lucide-react';
import { cn } from '../../utils/cn';

/**
 * Знак вопроса с пояснением в тултипе.
 *
 * Пояснения к отчётам раньше висели абзацем над таблицей: их читают один раз, а место они
 * занимают всегда и отодвигают данные. Текст никуда не делся — он тут, под «?».
 */
export const HelpTip: React.FC<{ text: string; className?: string }> = ({ text, className }) => (
  <span
    title={text}
    tabIndex={0}
    aria-label={text}
    className={cn(
      'inline-flex items-center justify-center text-slate-300 hover:text-slate-500 cursor-help transition-colors',
      className
    )}
  >
    <HelpCircle size={15} />
  </span>
);
