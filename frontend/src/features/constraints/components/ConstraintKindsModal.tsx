import React, { useEffect, useState } from 'react';
import { Loader2, Plus, Trash2, X, Check, Pencil } from 'lucide-react';
import { ConstraintKindDto, ConstraintKindFormDto } from '../../../types/api';
import { ConstraintKindService } from '../../../services/apiServices';
import { CONSTRAINT_COLOR_KEYS, constraintStyleOfColor } from '../constraintStyles';
import { useEnums } from '../../../context/EnumContext';
import { cn } from '../../../utils/cn';

interface Props {
  onClose: () => void;
}

/**
 * Редактор справочника видов ограничений.
 *
 * <p>Раньше виды жили Java-enum'ом, и «нужен наряд» означало «нужен релиз». Теперь перечень —
 * данные: код по видам не ветвится, вид определяет только подпись и цвет.</p>
 *
 * Что здесь намеренно ограничено:
 * - <b>цвет выбирается из палитры</b>, а не пипеткой: классы Tailwind собираются статически,
 *   произвольный оттенок из базы в вёрстку не подставить (см. `constraintStyles.ts`);
 * - <b>системные виды не удаляются</b> — их коды лежат в уже проставленных ограничениях; их
 *   можно переименовать, перекрасить или погасить;
 * - <b>вид, которым что-то размечено, не удаляется</b> — бэк вернёт 409 с числом ссылающихся,
 *   его и показываем; погасить можно всегда.
 */
export const ConstraintKindsModal: React.FC<Props> = ({ onClose }) => {
  const { reloadConstraintKinds } = useEnums();
  const [kinds, setKinds] = useState<ConstraintKindDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Код редактируемой строки; '' — черновик новой. null — ничего не редактируется. */
  const [editing, setEditing] = useState<string | null>(null);
  const [form, setForm] = useState<ConstraintKindFormDto>({ name: '', shortName: '', color: 'slate' });

  const reload = async () => {
    setLoading(true);
    try {
      setKinds(await ConstraintKindService.getAll());
    } catch {
      setError('Не удалось загрузить справочник');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { reload(); }, []);

  const startCreate = () => {
    setEditing('');
    setForm({ name: '', shortName: '', color: 'slate', sortOrder: (kinds.length + 1) * 10, active: true });
    setError(null);
  };

  const startEdit = (k: ConstraintKindDto) => {
    setEditing(k.code);
    setForm({ name: k.name, shortName: k.shortName, color: k.color, sortOrder: k.sortOrder, active: k.active });
    setError(null);
  };

  const save = async () => {
    if (!form.name.trim() || !form.shortName.trim()) {
      setError('Название и сокращение обязательны');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (editing === '') await ConstraintKindService.create(form);
      else if (editing) await ConstraintKindService.update(editing, form);
      setEditing(null);
      await reload();
      // Сетка, Гант и форма ввода читают справочник из общего провайдера — обновляем и его,
      // иначе новый вид не появится в выборе до перезагрузки страницы.
      await reloadConstraintKinds();
    } catch (e: any) {
      setError(typeof e?.response?.data === 'string' ? e.response.data : 'Не удалось сохранить');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (k: ConstraintKindDto) => {
    if (!window.confirm(`Удалить вид «${k.name}»?`)) return;
    setBusy(true);
    setError(null);
    try {
      await ConstraintKindService.remove(k.code);
      await reload();
      await reloadConstraintKinds();
    } catch (e: any) {
      // 409 приходит с внятным текстом (сколько ограничений размечено / вид системный).
      setError(typeof e?.response?.data === 'string' ? e.response.data : 'Не удалось удалить');
    } finally {
      setBusy(false);
    }
  };

  const inputCls = 'w-full px-2 py-1 border border-slate-300 rounded text-xs outline-none focus:border-blue-500';

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onMouseDown={onClose}>
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[85vh] flex flex-col"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
          <h2 className="text-sm font-black text-slate-800">Виды ограничений</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700"><X size={18} /></button>
        </div>

        {error && (
          <div className="mx-4 mt-3 px-3 py-2 bg-red-50 border border-red-200 rounded text-[11px] text-red-700">
            {error}
          </div>
        )}

        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {loading ? (
            <div className="py-8 text-center text-slate-400"><Loader2 className="animate-spin inline" size={18} /></div>
          ) : (
            <>
              {kinds.map((k) => (
                editing === k.code ? (
                  <KindForm key={k.code} form={form} setForm={setForm} onSave={save} onCancel={() => setEditing(null)}
                            busy={busy} inputCls={inputCls} />
                ) : (
                  <div key={k.code} className={cn('flex items-center gap-2 px-3 py-2 border border-slate-200 rounded-lg',
                    !k.active && 'opacity-50')}>
                    <span className={cn('w-3 h-3 rounded-sm shrink-0', constraintStyleOfColor(k.color).dot)} />
                    <span className="font-black text-xs text-slate-800 shrink-0">{k.shortName}</span>
                    <span className="text-xs text-slate-600 truncate flex-1">{k.name}</span>
                    {k.system && <span className="text-[9px] text-slate-400 shrink-0">из кода</span>}
                    {!k.active && <span className="text-[9px] text-slate-400 shrink-0">погашен</span>}
                    <span className="text-[10px] text-slate-400 shrink-0" title="Сколько ограничений размечено">
                      {k.usageCount}
                    </span>
                    <button onClick={() => startEdit(k)} disabled={busy}
                            className="text-slate-400 hover:text-blue-600 shrink-0" title="Изменить">
                      <Pencil size={13} />
                    </button>
                    <button onClick={() => remove(k)} disabled={busy || k.system || k.usageCount > 0}
                            className="text-slate-400 hover:text-red-600 disabled:opacity-30 shrink-0"
                            title={k.system ? 'Вид из кода — можно только погасить'
                              : k.usageCount > 0 ? `Используется в ${k.usageCount} огранич.` : 'Удалить'}>
                      <Trash2 size={13} />
                    </button>
                  </div>
                )
              ))}

              {editing === '' ? (
                <KindForm form={form} setForm={setForm} onSave={save} onCancel={() => setEditing(null)}
                          busy={busy} inputCls={inputCls} />
              ) : (
                <button onClick={startCreate} disabled={busy}
                        className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold text-blue-600 hover:bg-blue-50 rounded-lg w-full">
                  <Plus size={14} /> Добавить вид
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

const KindForm: React.FC<{
  form: ConstraintKindFormDto;
  setForm: (f: ConstraintKindFormDto) => void;
  onSave: () => void;
  onCancel: () => void;
  busy: boolean;
  inputCls: string;
}> = ({ form, setForm, onSave, onCancel, busy, inputCls }) => (
  <div className="p-3 border border-blue-300 bg-blue-50/40 rounded-lg space-y-2">
    <div className="grid grid-cols-3 gap-2">
      <input className={cn(inputCls, 'col-span-2')} placeholder="Название (Наряд)" value={form.name}
             onChange={(e) => setForm({ ...form, name: e.target.value })} disabled={busy} autoFocus />
      <input className={inputCls} placeholder="Сокр. (Нар)" value={form.shortName}
             onChange={(e) => setForm({ ...form, shortName: e.target.value })} disabled={busy} />
    </div>
    <div className="flex flex-wrap items-center gap-1">
      {CONSTRAINT_COLOR_KEYS.map((color) => (
        <button key={color} onClick={() => setForm({ ...form, color })} disabled={busy} title={color}
                className={cn('w-5 h-5 rounded-sm border-2', constraintStyleOfColor(color).dot,
                  form.color === color ? 'border-slate-800' : 'border-transparent')} />
      ))}
    </div>
    <div className="flex items-center gap-3">
      <label className="flex items-center gap-1.5 text-[11px] text-slate-600">
        <input type="checkbox" checked={form.active !== false}
               onChange={(e) => setForm({ ...form, active: e.target.checked })} disabled={busy} />
        Доступен при вводе
      </label>
      <input type="number" className={cn(inputCls, 'w-20')} placeholder="Порядок" value={form.sortOrder ?? ''}
             onChange={(e) => setForm({ ...form, sortOrder: e.target.value === '' ? undefined : Number(e.target.value) })}
             disabled={busy} title="Порядок в списке" />
      <div className="ml-auto flex gap-1">
        <button onClick={onCancel} disabled={busy}
                className="px-2 py-1 text-xs text-slate-500 hover:text-slate-800">Отмена</button>
        <button onClick={onSave} disabled={busy}
                className="flex items-center gap-1 px-3 py-1 bg-blue-600 text-white rounded text-xs font-bold hover:bg-blue-700 disabled:opacity-50">
          {busy ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />} Сохранить
        </button>
      </div>
    </div>
  </div>
);
