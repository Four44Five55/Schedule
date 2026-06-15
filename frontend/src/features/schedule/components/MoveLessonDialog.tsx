// ============ MOVE LESSON DIALOG ============
// Компонент для поиска и переноса занятий с использованием CQRS

import { useState, useEffect } from 'react';
import { Calendar, Clock, AlertTriangle, X, Check } from 'lucide-react';
import { MoveOptionDto, CQRSService, dateUtils } from '../../../services/cqrsApiService';
import { ScheduledLessonDto } from '../../../types/api';

interface MoveLessonDialogProps {
  placement: ScheduledLessonDto;
  sessionId: string;
  currentVersion: number;
  onMoveSuccessful: () => void;
  onCancel: () => void;
}

/**
 * Компонент диалогового окна для переноса занятия
 *
 * Предоставляет UI для:
 * - Поиска вариантов переноса
 * - Выбора оптимального варианта
 * - Применения переноса с optimistic lock
 */
export const MoveLessonDialog: React.FC<MoveLessonDialogProps> = ({
  placement,
  sessionId,
  currentVersion,
  onMoveSuccessful,
  onCancel
}) => {
  const [visible, setVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [options, setOptions] = useState<MoveOptionDto[]>([]);
  const [selectedOption, setSelectedOption] = useState<MoveOptionDto | null>(null);
  const [moving, setMoving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  /**
   * Открыть диалог и начать поиск вариантов
   */
  const handleOpen = () => {
    setVisible(true);
    setLoading(true);
    setOptions([]);
    setSelectedOption(null);
    setError(null);
    setSuccess(false);

    // Ищем варианты переноса
    CQRSService.findMoveOptions({
      sessionId: sessionId,
      lessonId: placement.id,
      rootEntityId: placement.educatorIds[0] || 1, // Берем первого преподавателя
      rootEntityType: 'EDUCATOR'
    })
      .then((foundOptions) => {
        setOptions(foundOptions);
      })
      .catch((err) => {
        console.error('❌ Ошибка поиска вариантов:', err);
        setError('Не удалось найти варианты для переноса');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  /**
   * Применить перенос выбранного варианта
   */
  const handleApplyMove = async () => {
    if (!selectedOption) return;

    setMoving(true);
    setError(null);

    try {
      const result = await CQRSService.moveLesson(sessionId, {
        placementId: String(placement.id),
        newDate: selectedOption.date,
        newSlot: selectedOption.timeSlot,
        auditoriumIds: selectedOption.auditoriumIds,
        version: currentVersion
      });

      if (result.success) {
        // ✅ Успех!
        setSuccess(true);
        setTimeout(() => {
          handleClose();
          onMoveSuccessful();
        }, 1500);
      } else if (result.conflict) {
        // ❌ Конфликт версий
        setError(
          `Конфликт версий! ${result.conflict.message}\n` +
          `Текущая версия: ${result.conflict.currentVersion}`
        );
      }
    } catch (err: any) {
      console.error('❌ Ошибка переноса:', err);
      setError('Не удалось перенести занятие. Попробуйте ещё раз.');
    } finally {
      setMoving(false);
    }
  };

  /**
   * Закрыть диалог
   */
  const handleClose = () => {
    setVisible(false);
    setSelectedOption(null);
    setError(null);
    setSuccess(false);
    onCancel();
  };

  /**
   * Получить название временного слота
   */
  const getSlotLabel = (slot: string): string => {
    const labels: Record<string, string> = {
      'FIRST': '1-я пара',
      'SECOND': '2-я пара',
      'THIRD': '3-я пара',
      'FOURTH': '4-я пара'
    };
    return labels[slot] || slot;
  };

  /**
   * Получить цвет для оценки варианта
   */
  const getScoreColor = (score: number): string => {
    if (score >= 90) return 'text-green-600';
    if (score >= 70) return 'text-yellow-600';
    return 'text-slate-600';
  };

  // Если диалог не открыт, показываем только кнопку открытия
  if (!visible) {
    return (
      <button
        onClick={handleOpen}
        className="inline-flex items-center gap-2 px-3 py-1.5 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors text-xs font-medium"
        title="Перенести занятие"
      >
        <Calendar size={14} />
        Перенести
      </button>
    );
  }

  // Диалоговое окно
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-hidden flex flex-col">
        {/* Заголовок */}
        <div className="px-6 py-4 border-b border-slate-200 bg-white">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-black text-slate-900">Перенести занятие</h2>
            <button
              onClick={handleClose}
              className="p-1 hover:bg-slate-100 rounded-lg transition-colors"
            >
              <X size={20} className="text-slate-500" />
            </button>
          </div>
        </div>

        {/* Контент */}
        <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
          {/* Текущее занятие */}
          <div className="p-4 bg-slate-50 rounded-lg border border-slate-200">
            <h3 className="font-black text-slate-900 mb-3 flex items-center gap-2">
              <Clock size={16} />
              Текущее занятие
            </h3>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-xs text-slate-500">Дисциплина:</span>
                <span className="font-medium">{placement.disciplineName}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500">Преподаватели:</span>
                <span className="font-medium">{placement.educatorNames.join(', ')}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500">Группы:</span>
                <span className="font-medium">{placement.groupNames.join(', ')}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500">Текущее время:</span>
                <span className="font-medium">
                  {placement.date} - {getSlotLabel(placement.timeSlotPair)}
                </span>
              </div>
              <div>
                <span className="text-xs text-slate-500">Аудитории:</span>
                <span className="font-medium">
                  {placement.auditoriumNames.join(', ') || 'Не назначены'}
                </span>
              </div>
            </div>
          </div>

          {/* Варианты переноса */}
          <div>
            <h3 className="font-black text-slate-900 mb-3">Доступные варианты:</h3>

            {loading ? (
              <div className="flex flex-col items-center justify-center py-12">
                <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600 mb-3"></div>
                <p className="text-sm text-slate-500">Поиск вариантов...</p>
              </div>
            ) : error ? (
              <div className="flex flex-col items-center justify-center py-12">
                <AlertTriangle size={32} className="text-red-500 mb-2" />
                <p className="text-red-600 font-medium">{error}</p>
                <button
                  onClick={handleOpen}
                  className="mt-3 text-blue-600 hover:text-blue-700 text-sm underline"
                >
                  Попробовать снова
                </button>
              </div>
            ) : success ? (
              <div className="flex flex-col items-center justify-center py-12">
                <Check size={32} className="text-green-500 mb-2" />
                <p className="text-green-600 font-medium">Занятие успешно перенесено!</p>
              </div>
            ) : options.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-slate-500">
                <AlertTriangle size={32} className="mb-2" />
                <p>Нет доступных вариантов для переноса</p>
                <p className="text-xs text-slate-400 mt-2">
                  Все возможные слоты заняты или не подходят по ограничениям
                </p>
              </div>
            ) : (
              <div className="space-y-2">
                {options.map((option, index) => (
                  <div
                    key={index}
                    onClick={() => setSelectedOption(option)}
                    className={`p-4 border rounded-xl cursor-pointer transition-all ${
                      selectedOption?.date === option.date &&
                      selectedOption?.timeSlot === option.timeSlot
                        ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-500'
                        : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-2">
                          <Calendar size={16} className="text-slate-600" />
                          <div className="font-black text-slate-900">
                            {option.date}
                          </div>
                          <div className="text-sm text-slate-500">
                            {getSlotLabel(option.timeSlot)}
                          </div>
                        </div>

                        <div className="flex items-center gap-2 text-xs text-slate-600">
                          <span>Аудитории:</span>
                          <span className="font-medium text-slate-900">
                            {option.auditoriumIds.length > 0
                              ? option.auditoriumIds.join(', ')
                              : 'Не назначены'}
                          </span>
                        </div>
                      </div>

                      <div className="text-right">
                        <div className={`text-lg font-black ${getScoreColor(option.score)}`}>
                          ⭐ {option.score}%
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Подвал с действиями */}
        <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
          <div className="text-xs text-slate-500">
            Версия сессии: {currentVersion}
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleClose}
              className="px-4 py-2 border border-slate-300 rounded-lg hover:bg-slate-100 transition-colors"
            >
              Отмена
            </button>
            <button
              onClick={handleApplyMove}
              disabled={!selectedOption || moving}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {moving ? (
                <span className="flex items-center gap-2">
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                  Перенос...
                </span>
              ) : (
                'Перенести'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
