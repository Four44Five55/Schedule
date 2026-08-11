/**
 * Презентационные стили вида ограничения (цвет ячейки/текста/точки-легенды).
 *
 * Это чисто UI-слой: данные (название, сокращение, КЛЮЧ ЦВЕТА) приходят из справочника с бэка,
 * а какие именно CSS-классы соответствуют ключу — решение фронта, поэтому карта живёт здесь.
 *
 * ⚠️ Карта по КЛЮЧАМ ПАЛИТРЫ, а не по кодам видов, и это не деталь оформления. Виды ограничений
 * теперь заводит пользователь: их коды заранее неизвестны, и карта «код → цвет» устарела бы
 * в момент, когда кто-то добавит «Наряд». Tailwind при этом собирает классы статически —
 * `bg-${color}-100` из базы не сработает, класса просто не окажется в сборке. Отсюда решение:
 * в БД лежит ключ, здесь — полные классы для каждого ключа.
 *
 * Палитрой владеет ФРОНТ: бэк хранит ключ строкой и о его смысле не судит — иначе список цветов
 * пришлось бы держать в двух местах, а такие копии в этом проекте уже разъезжались. Ключ, которого
 * здесь нет, отрисуется нейтрально (`FALLBACK_CONSTRAINT_STYLE`), а не пропадёт.
 */
export interface ConstraintStyle {
  /** Фон ячейки сетки. */
  cell: string;
  /** Цвет текста аббревиатуры. */
  text: string;
  /** Точка-маркер для легенды. */
  dot: string;
}

export const CONSTRAINT_PALETTE: Record<string, ConstraintStyle> = {
  slate: { cell: 'bg-slate-200 hover:bg-slate-300', text: 'text-slate-700', dot: 'bg-slate-500' },
  amber: { cell: 'bg-amber-100 hover:bg-amber-200', text: 'text-amber-800', dot: 'bg-amber-500' },
  emerald: { cell: 'bg-emerald-100 hover:bg-emerald-200', text: 'text-emerald-800', dot: 'bg-emerald-500' },
  rose: { cell: 'bg-rose-100 hover:bg-rose-200', text: 'text-rose-800', dot: 'bg-rose-500' },
  sky: { cell: 'bg-sky-100 hover:bg-sky-200', text: 'text-sky-800', dot: 'bg-sky-500' },
  violet: { cell: 'bg-violet-100 hover:bg-violet-200', text: 'text-violet-800', dot: 'bg-violet-500' },
  fuchsia: { cell: 'bg-fuchsia-100 hover:bg-fuchsia-200', text: 'text-fuchsia-800', dot: 'bg-fuchsia-500' },
  teal: { cell: 'bg-teal-100 hover:bg-teal-200', text: 'text-teal-800', dot: 'bg-teal-500' },
  indigo: { cell: 'bg-indigo-100 hover:bg-indigo-200', text: 'text-indigo-800', dot: 'bg-indigo-500' },
  orange: { cell: 'bg-orange-100 hover:bg-orange-200', text: 'text-orange-800', dot: 'bg-orange-500' },
  lime: { cell: 'bg-lime-100 hover:bg-lime-200', text: 'text-lime-800', dot: 'bg-lime-500' },
  cyan: { cell: 'bg-cyan-100 hover:bg-cyan-200', text: 'text-cyan-800', dot: 'bg-cyan-500' },
  pink: { cell: 'bg-pink-100 hover:bg-pink-200', text: 'text-pink-800', dot: 'bg-pink-500' },
  stone: { cell: 'bg-stone-200 hover:bg-stone-300', text: 'text-stone-700', dot: 'bg-stone-500' },
};

/** Порядок показа в выборе цвета — тот же, что в палитре, но предсказуемо отсортированный. */
export const CONSTRAINT_COLOR_KEYS = Object.keys(CONSTRAINT_PALETTE);

/** Запасной стиль: цвет из базы неизвестен фронту (старая сборка, чужие данные). */
export const FALLBACK_CONSTRAINT_STYLE: ConstraintStyle = CONSTRAINT_PALETTE.slate;

/** Стиль по ключу цвета из справочника. */
export const constraintStyleOfColor = (color?: string | null): ConstraintStyle =>
  (color ? CONSTRAINT_PALETTE[color] : undefined) ?? FALLBACK_CONSTRAINT_STYLE;
