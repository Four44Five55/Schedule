import React, { useMemo, useState } from 'react';
import { DisciplineDto, DisciplineCourseDto } from '../../../types/api';
import { BookOpen, Plus, Edit2, Trash2, Search, X, ExternalLink } from 'lucide-react';
import { CurriculumService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { DisciplineFormModal } from './DisciplineFormModal';
import { DisciplineCourseFormModal } from './DisciplineCourseFormModal';
import type { TabId } from '../../../components/layout/Sidebar';
import { useToast } from '../../../context/ToastContext';

interface DisciplineListProps {
  disciplines: DisciplineDto[];
  onRefresh?: () => void;
  /** Переход в планировщик: учебные планы правят там, здесь — только справочник. */
  onNavigate?: (tab: TabId) => void;
}

/**
 * Справочник дисциплин: завести, исправить название, вести перечень курсов по периодам.
 *
 * <p><b>Учебный план здесь не редактируется.</b> Тот же {@code CurriculumPlanEditor} живёт в
 * планировщике (вкладка «Курсы»), где план и наполняют — вместе с клоном из другого периода,
 * потоками и назначениями. Держать второй вход в редактор внутри справочника значило иметь два
 * рабочих места для одной задачи.</p>
 *
 * <p><b>Удаление дисциплины разрешено только пустой.</b> Каскад БД уносит курсы вместе с планами,
 * назначениями и уже размещёнными занятиями — цену такого удаления нельзя осмысленно принять в
 * одном диалоге, поэтому бэк отвечает 409, а не предупреждением. Курсы удаляются по одному, и там
 * цена называется заранее.</p>
 */
export const DisciplineList: React.FC<DisciplineListProps> = ({ disciplines, onRefresh, onNavigate }) => {
  const toast = useToast();
  const [query, setQuery] = useState('');

  const [selectedDiscipline, setSelectedDiscipline] = useState<DisciplineDto | null>(null);
  const [showDisciplineForm, setShowDisciplineForm] = useState(false);
  const [deletingDiscipline, setDeletingDiscipline] = useState<DisciplineDto | null>(null);

  const [selectedCourseForEdit, setSelectedCourseForEdit] = useState<DisciplineCourseDto | null>(null);
  const [courseFormDisciplineId, setCourseFormDisciplineId] = useState<number | undefined>();
  const [showCourseForm, setShowCourseForm] = useState(false);

  /** Курс, который собираются удалить, вместе с уже загруженной ценой удаления. */
  const [deletingCourse, setDeletingCourse] = useState<{ course: DisciplineCourseDto; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return disciplines;
    return disciplines.filter(
        (d) => d.name.toLowerCase().includes(q) || (d.abbreviation ?? '').toLowerCase().includes(q),
    );
  }, [disciplines, query]);

  // ── Дисциплина ───────────────────────────────────────────────
  const handleDeleteDisciplineConfirm = async () => {
    if (!deletingDiscipline) return;
    setBusy(true);
    try {
      await CurriculumService.deleteDiscipline(deletingDiscipline.id);
      setDeletingDiscipline(null);
      onRefresh?.();
    } catch (err: any) {
      // 409 — у дисциплины есть курсы; бэк присылает текст с их числом.
      setDeletingDiscipline(null);
      toast.failure(err, 'Не удалось удалить дисциплину.');
    } finally {
      setBusy(false);
    }
  };

  // ── Курс ─────────────────────────────────────────────────────
  const requestDeleteCourse = async (course: DisciplineCourseDto) => {
    // Цену удаления называем ДО подтверждения: тот же эндпоинт зовёт планировщик.
    let message = `Удалить курс «Семестр ${course.semester}»? Это действие нельзя отменить.`;
    try {
      const impact = await CurriculumService.getCourseDeletionImpact(course.id);
      const loss = [
        impact.slots > 0 ? `занятий плана: ${impact.slots}` : null,
        impact.assignments > 0 ? `назначений: ${impact.assignments}` : null,
        impact.placedLessons > 0 ? `занятий в расписании: ${impact.placedLessons}` : null,
      ].filter(Boolean).join(', ');
      if (loss) {
        message = `Вместе с курсом будет удалено — ${loss}.\n\nЭто действие нельзя отменить.`;
      }
    } catch {
      // Как и с занятием плана: не смогли узнать цену — говорим это в самом вопросе, иначе
      // «Удалить курс?» читается как «терять нечего».
      message = 'Проверить, что будет удалено вместе с курсом, не удалось (сервер не ответил). '
        + 'Каскадом уходят занятия плана, назначения и уже размещённые занятия.\n\n'
        + 'Это действие нельзя отменить.';
    }
    setDeletingCourse({ course, message });
  };

  const handleDeleteCourseConfirm = async () => {
    if (!deletingCourse) return;
    setBusy(true);
    try {
      await CurriculumService.deleteCourse(deletingCourse.course.id);
      setDeletingCourse(null);
      onRefresh?.();
    } catch (e) {
      setDeletingCourse(null);
      toast.failure(e, 'Не удалось удалить курс.');
    } finally {
      setBusy(false);
    }
  };

  return (
      <>
        {/* Панель */}
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-slate-500">
            <BookOpen size={16} />
            <span className="text-sm font-medium">
              {query.trim()
                  ? <>Показано: <span className="font-black text-slate-900">{visible.length}</span> из {disciplines.length}</>
                  : <>Всего: <span className="font-black text-slate-900">{disciplines.length}</span></>}
            </span>
          </div>

          <div className="flex items-center gap-2">
            <div className="relative">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Поиск по названию"
                  className="w-56 pl-8 pr-7 py-1.5 border border-slate-200 rounded-lg text-xs font-medium outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
              />
              {query && (
                  <button
                      onClick={() => setQuery('')}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                      title="Очистить"
                  >
                    <X size={13} />
                  </button>
              )}
            </div>

            <button
                onClick={() => { setSelectedDiscipline(null); setShowDisciplineForm(true); }}
                className="px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors flex items-center gap-2"
            >
              <Plus size={14} />
              Новая дисциплина
            </button>
          </div>
        </div>

        {disciplines.length === 0 ? (
            <div className="py-20 text-center text-slate-300 border-2 border-dashed border-slate-100 rounded-2xl">
              <BookOpen size={48} className="mx-auto mb-4 opacity-30" />
              <p className="text-slate-400 font-medium">Нет дисциплин. Создайте первую.</p>
            </div>
        ) : visible.length === 0 ? (
            <div className="py-16 text-center border-2 border-dashed border-slate-100 rounded-2xl">
              <Search size={32} className="mx-auto mb-3 text-slate-300" />
              <p className="text-slate-400 font-medium text-sm">Ничего не нашлось</p>
              <button
                  onClick={() => setQuery('')}
                  className="mt-3 px-3 py-1.5 bg-slate-100 text-slate-700 rounded-lg font-bold text-xs hover:bg-slate-200 transition-colors"
              >
                Сбросить поиск
              </button>
            </div>
        ) : (
            <div className="space-y-3">
              {visible.map((disc) => (
                  <div key={disc.id} className="border border-slate-200 rounded-xl overflow-hidden bg-white">
                    {/* Дисциплина */}
                    <div className="flex items-center justify-between px-4 py-3 bg-slate-50 border-b border-slate-200">
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-8 h-8 rounded-lg bg-blue-100 flex items-center justify-center shrink-0">
                          <span className="text-blue-700 font-black text-xs">
                            {disc.abbreviation || disc.name[0]}
                          </span>
                        </div>
                        <div className="min-w-0">
                          <h3 className="font-bold text-slate-900 text-sm truncate" title={disc.name}>{disc.name}</h3>
                          {disc.abbreviation && (
                              <p className="text-[11px] text-slate-400">{disc.abbreviation}</p>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        <span className="text-[11px] font-bold text-slate-400 mr-1">
                          курсов: {disc.courses?.length ?? 0}
                        </span>
                        <button
                            onClick={() => { setSelectedDiscipline(disc); setShowDisciplineForm(true); }}
                            className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                            title="Редактировать дисциплину"
                        >
                          <Edit2 size={14} />
                        </button>
                        <button
                            onClick={() => setDeletingDiscipline(disc)}
                            className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                            title="Удалить дисциплину"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>

                    {/* Курсы — перечень, без редактора плана */}
                    <div className="divide-y divide-slate-100">
                      {(!disc.courses || disc.courses.length === 0) && (
                          <div className="px-4 py-3 text-xs text-slate-400 italic">Нет учебных курсов</div>
                      )}

                      {disc.courses?.map((course) => (
                          <div key={course.id} className="flex items-center justify-between px-4 py-2.5">
                            <div className="flex items-baseline gap-2 min-w-0">
                              <span className="text-sm font-semibold text-slate-800 shrink-0">
                                Семестр {course.semester}
                              </span>
                              {course.studyPeriod && (
                                  <span className="text-xs text-slate-400 truncate">{course.studyPeriod.name}</span>
                              )}
                            </div>
                            <div className="flex items-center gap-1 shrink-0">
                              <button
                                  onClick={() => { setSelectedCourseForEdit(course); setShowCourseForm(true); }}
                                  className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                                  title="Редактировать курс"
                              >
                                <Edit2 size={13} />
                              </button>
                              <button
                                  onClick={() => requestDeleteCourse(course)}
                                  className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                                  title="Удалить курс"
                              >
                                <Trash2 size={13} />
                              </button>
                            </div>
                          </div>
                      ))}
                    </div>

                    {/* Подвал: добавить курс + отсылка к планировщику за планом */}
                    <div className="px-4 py-2.5 border-t border-slate-100 bg-slate-50/50 flex flex-wrap items-center justify-between gap-2">
                      <button
                          onClick={() => {
                            setCourseFormDisciplineId(disc.id);
                            setSelectedCourseForEdit(null);
                            setShowCourseForm(true);
                          }}
                          className="flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-blue-600 transition-colors"
                      >
                        <Plus size={13} />
                        Добавить учебный курс
                      </button>

                      {onNavigate && (
                          <button
                              onClick={() => onNavigate('planner')}
                              className="flex items-center gap-1.5 text-xs font-semibold text-slate-400 hover:text-blue-600 transition-colors"
                              title="Учебные планы наполняются в планировщике"
                          >
                            План — в планировщике
                            <ExternalLink size={12} />
                          </button>
                      )}
                    </div>
                  </div>
              ))}
            </div>
        )}

        {/* Модалки */}
        {showDisciplineForm && (
            <DisciplineFormModal
                discipline={selectedDiscipline}
                onClose={() => { setShowDisciplineForm(false); setSelectedDiscipline(null); }}
                onSaved={() => { setShowDisciplineForm(false); setSelectedDiscipline(null); onRefresh?.(); }}
            />
        )}

        {showCourseForm && (
            <DisciplineCourseFormModal
                course={selectedCourseForEdit}
                disciplineId={courseFormDisciplineId}
                onClose={() => { setShowCourseForm(false); setSelectedCourseForEdit(null); setCourseFormDisciplineId(undefined); }}
                onSaved={() => { setShowCourseForm(false); setSelectedCourseForEdit(null); setCourseFormDisciplineId(undefined); onRefresh?.(); }}
            />
        )}

        {deletingDiscipline && (
            <ConfirmDialog
                title="Удалить дисциплину?"
                message={`Дисциплина «${deletingDiscipline.name}» будет удалена. Удаление возможно, только если у неё нет учебных курсов.`}
                confirmLabel="Удалить"
                variant="danger"
                isLoading={busy}
                onConfirm={handleDeleteDisciplineConfirm}
                onCancel={() => setDeletingDiscipline(null)}
            />
        )}

        {deletingCourse && (
            <ConfirmDialog
                title="Удалить учебный курс?"
                message={deletingCourse.message}
                confirmLabel="Удалить"
                variant="danger"
                isLoading={busy}
                onConfirm={handleDeleteCourseConfirm}
                onCancel={() => setDeletingCourse(null)}
            />
        )}
      </>
  );
};
