import { KindOfConstraints } from '../../types/api';

/**
 * Презентационные стили вида ограничения (цвет ячейки/текста/точки-легенды).
 *
 * Это чисто UI-слой: данные (аббревиатура, полное имя) приходят с бэка, а цвет —
 * решение фронта, поэтому маппинг живёт здесь, а не в DTO.
 */
export interface ConstraintStyle {
  /** Фон ячейки сетки. */
  cell: string;
  /** Цвет текста аббревиатуры. */
  text: string;
  /** Точка-маркер для легенды. */
  dot: string;
}

export const CONSTRAINT_STYLES: Record<KindOfConstraints, ConstraintStyle> = {
  BUSINESS_TRIP: { cell: 'bg-amber-100 hover:bg-amber-200', text: 'text-amber-800', dot: 'bg-amber-500' },
  VACATION: { cell: 'bg-emerald-100 hover:bg-emerald-200', text: 'text-emerald-800', dot: 'bg-emerald-500' },
  EXAM_SESSION: { cell: 'bg-rose-100 hover:bg-rose-200', text: 'text-rose-800', dot: 'bg-rose-500' },
  MEDICAL_CARE: { cell: 'bg-sky-100 hover:bg-sky-200', text: 'text-sky-800', dot: 'bg-sky-500' },
  LIBRARY: { cell: 'bg-violet-100 hover:bg-violet-200', text: 'text-violet-800', dot: 'bg-violet-500' },
  FINAL_STATE_ATTESTATION: { cell: 'bg-fuchsia-100 hover:bg-fuchsia-200', text: 'text-fuchsia-800', dot: 'bg-fuchsia-500' },
  OTHER: { cell: 'bg-slate-200 hover:bg-slate-300', text: 'text-slate-700', dot: 'bg-slate-500' },
};

/** Запасной стиль на случай неизвестного вида (защита от рассинхрона enum). */
export const FALLBACK_CONSTRAINT_STYLE: ConstraintStyle = CONSTRAINT_STYLES.OTHER;
