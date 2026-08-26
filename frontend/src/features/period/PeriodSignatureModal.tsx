import React, { useState } from 'react';
import { StudyPeriodDto } from '../../types/api';
import { ResourceService } from '../../services/apiServices';
import { PenLine, X, Loader2, Check } from 'lucide-react';
import { errorMessage } from '../../services/apiError';
import { ErrorBanner } from '../../components/ui/ErrorBanner';

/**
 * Подпись под расписанием: должность, регалии и фамилия с инициалами того, кто его подписывает.
 *
 * <p>Стоит рядом с выгрузкой, потому что нужна именно там, а хранится <b>у периода</b>: подписант
 * меняется от семестра к семестру, и вводить его в каждую выгрузку заново — та самая ручная работа,
 * от которой уходим. Прошлые семестры при этом сохраняют своего подписанта.</p>
 *
 * <p>Три поля, а не одна строка: в бланке должность идёт отдельной строкой, а регалии и фамилия —
 * одной. Предпросмотр показывает ровно то, что попадёт в файл, — иначе раскладку пришлось бы
 * проверять выгрузкой.</p>
 */
export const PeriodSignatureModal: React.FC<{
  period: StudyPeriodDto;
  onClose: () => void;
  onSaved: (period: StudyPeriodDto) => void;
}> = ({ period, onClose, onSaved }) => {
  const [position, setPosition] = useState(period.signerPosition ?? '');
  const [credentials, setCredentials] = useState(period.signerCredentials ?? '');
  const [name, setName] = useState(period.signerName ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const personLine = [credentials.trim(), name.trim()].filter(Boolean).join(' ');
  const isEmpty = !position.trim() && !personLine;

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await ResourceService.updatePeriodSignature(period.id, {
        signerPosition: position,
        signerCredentials: credentials,
        signerName: name,
      });
      onSaved(updated);
    } catch (err: any) {
      setError(errorMessage(err, 'Не удалось сохранить подпись.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden" onClick={e => e.stopPropagation()}>
        <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 rounded-lg"><PenLine size={20} className="text-blue-600" /></div>
            <div>
              <h2 className="text-lg font-black text-slate-900">Подпись под расписанием</h2>
              <p className="text-xs text-slate-500 font-medium">{period.name} ({period.studyYear})</p>
            </div>
          </div>
          <button onClick={onClose} disabled={saving} className="p-1 hover:bg-slate-200 rounded-lg transition-colors">
            <X size={20} className="text-slate-500" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {error && (
            <ErrorBanner message={error} />
          )}

          <p className="text-xs text-slate-500">
            Печатается внизу каждого листа выгрузки, справа. Подписант один на весь период —
            и на все файлы в архиве.
          </p>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Должность</label>
            <input
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              placeholder="Начальник учебного отдела"
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Регалии</label>
              <input
                value={credentials}
                onChange={(e) => setCredentials(e.target.value)}
                placeholder="полковник"
                className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Фамилия и инициалы</label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Иванов И.И."
                className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          {/* Предпросмотр — ровно те строки, что уйдут в бланк (пустые не печатаются). */}
          <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg">
            <div className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1">В бланке</div>
            {isEmpty ? (
              <div className="text-sm text-slate-400 italic">подпись не печатается</div>
            ) : (
              <div className="text-sm text-slate-800 text-right font-medium leading-tight">
                {position.trim() && <div>{position.trim()}</div>}
                {personLine && <div>{personLine}</div>}
              </div>
            )}
          </div>
        </div>

        <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end gap-2">
          <button
            onClick={onClose}
            disabled={saving}
            className="px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-200 rounded-lg transition-colors"
          >
            Отмена
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-60"
          >
            {saving ? <Loader2 size={16} className="animate-spin" /> : <Check size={16} />}
            Сохранить
          </button>
        </div>
      </div>
    </div>
  );
};
