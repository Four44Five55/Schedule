/**
 * ЕДИНЫЙ источник подсветки по виду занятия (UI-слой).
 *
 * Раньше цвета видов были зашиты прямо в `AcademicGridSchedule` тернарниками
 * («экзамен → фиолетовый, лекция → розовый, остальное → голубой»), и палитра ручной раскладки
 * не подсвечивала виды вовсе — второй источник правды неизбежно разошёлся бы с первым.
 * Теперь и сетка, и палитра берут стиль отсюда: новый вид или смена цвета — правка одного файла.
 *
 * Прецедент — `features/constraints/constraintStyles.ts`: бэк отдаёт семантику (код вида,
 * аббревиатуру, полное имя), а КАК её показывать — решение фронта, поэтому маппинг живёт здесь,
 * а не в DTO.
 *
 * Виды сводятся в ГРУППЫ: цвет несёт смысл «лекция / практическое / аттестация / опрос»,
 * а не «каждому коду свой оттенок» — иначе сетка превращается в радугу.
 */

/**
 * Категория вида занятия — приходит с бэка полем `category` в `/api/enums/kind-of-study`.
 *
 * Списка «какие коды сюда относятся» здесь НЕТ и быть не должно: классификацией владеет
 * `ru.enums.KindOfStudy.Category`, фронт получает готовую категорию через
 * `useEnums().getStudyCategory()`. Раньше правило «экзамен и зачёты — аттестация» было выписано
 * тут и ещё раз в редакторе плана, то есть новый вид аттестации пришлось бы вносить в три места.
 *
 * Не «группа»: группа в этом проекте — учебная (`GroupDto`), а `PROGRESS_CHECK` не назван `QUIZ`,
 * чтобы категория не совпадала с именем конкретного вида занятия.
 */
export type KindCategory = 'LECTURE' | 'ASSESSMENT' | 'PROGRESS_CHECK' | 'PRACTICE';

/** Категория по умолчанию: вид неизвестен или enum-ы ещё не загрузились. */
export const DEFAULT_KIND_CATEGORY: KindCategory = 'PRACTICE';

export interface KindStyle {
  /** Фон занятия в сетке, когда его дисциплина «активна» (наведение/выделение). */
  active: string;
  /** Фон занятия в сетке в покое (цветом не кричим — иначе сетка пестрит). */
  resting: string;
  /** Бейдж/строка в палитре и легенде: фон + текст. */
  chip: string;
  /** Точка-маркер для легенды. */
  dot: string;
  /** Подпись для легенды. */
  label: string;
}

export const KIND_STYLES: Record<KindCategory, KindStyle> = {
  LECTURE: {
    active: 'bg-rose-150 text-slate-900 hover:bg-rose-200',
    resting: 'bg-white text-slate-900 hover:bg-slate-50',
    chip: 'bg-rose-100 text-rose-800',
    dot: 'bg-rose-400',
    label: 'Лекция',
  },
  PRACTICE: {
    active: 'bg-sky-150 text-slate-900 hover:bg-sky-200',
    resting: 'bg-white text-slate-900 hover:bg-slate-50',
    chip: 'bg-sky-100 text-sky-800',
    dot: 'bg-sky-400',
    label: 'Практическое',
  },
  ASSESSMENT: {
    active: 'bg-violet-150 text-slate-900 hover:bg-violet-200',
    resting: 'bg-slate-300 text-slate-900 hover:bg-slate-400',
    chip: 'bg-violet-100 text-violet-800',
    dot: 'bg-violet-400',
    label: 'Аттестация',
  },
  PROGRESS_CHECK: {
    active: 'bg-sky-150 text-slate-900 hover:bg-sky-200',
    resting: 'bg-slate-100 text-slate-900 hover:bg-slate-200',
    chip: 'bg-slate-100 text-slate-700',
    dot: 'bg-slate-400',
    label: 'Опрос',
  },
};

/** Стиль по категории вида — короткий путь для компонентов (категория берётся из `useEnums()`). */
export const kindStyleOfCategory = (category?: KindCategory | null): KindStyle =>
  KIND_STYLES[category ?? DEFAULT_KIND_CATEGORY];
