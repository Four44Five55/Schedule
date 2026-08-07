import React, { useEffect, useMemo, useState } from 'react';
import { EducatorDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { EducatorFormModal } from './EducatorFormModal';
import { useEnums } from '../../../context/EnumContext';
import { useOrgUnits } from '../../orgUnit/hooks/useOrgUnits';
import { User, Clock, Calendar, Zap, Plus, Pencil, Trash2, Network, Search, X } from 'lucide-react';

interface EducatorListProps {
  educators: EducatorDto[];
  onEducatorsChange: () => void;
}

/** Что показываем: всех, поддерево подразделения или тех, кто ещё не распределён. */
type UnitFilter = 'all' | 'none' | number;

/** Одна секция списка: подразделение (или «без подразделения») и его преподаватели. */
interface Section {
  key: string;
  title: string;
  /** Родитель — для заголовка «Факультет · Кафедра»; у корневых пусто. */
  parent: string | null;
  /** Краткое имя: именно оно уезжает в колонку «Каф» выгрузки. */
  short: string | null;
  items: EducatorDto[];
}

/**
 * Раздел «Преподаватели»: поиск, фильтр по подразделению и группировка карточек по кафедрам.
 *
 * <p><b>Охват подразделения берётся с бэка</b> (`GET /org-units/{id}/scope`): выбрав факультет,
 * пользователь ждёт всех его кафедр, а разворот дерева по решению проекта имеет единственного
 * владельца — `OrgUnitScopeResolver`. Форму дерева фронт строит сам (это презентация), но обход
 * ради выборки не повторяет.</p>
 *
 * <p>Порядок секций — обход дерева оргструктуры, а не алфавит: список читается как структура
 * организации. Внутри секции — по имени, потому что сам список приходит без сортировки
 * (`EducatorService.findAll`), и без этого карточки переставлялись бы после каждой правки.</p>
 */
export const EducatorList: React.FC<EducatorListProps> = ({ educators, onEducatorsChange }) => {
  const { getDayShort, getSlotShort } = useEnums();
  const { units, flat, loading: unitsLoading } = useOrgUnits();

  const [query, setQuery] = useState('');
  const [unitFilter, setUnitFilter] = useState<UnitFilter>('all');
  const [scopeIds, setScopeIds] = useState<Set<number> | null>(null);
  const [scopeError, setScopeError] = useState<string | null>(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingEducator, setEditingEducator] = useState<EducatorDto | null>(null);
  const [deletingEducator, setDeletingEducator] = useState<EducatorDto | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleCreate = () => { setEditingEducator(null); setIsModalOpen(true); };
  const handleEdit = (e: EducatorDto) => { setEditingEducator(e); setIsModalOpen(true); };
  const handleSaved = () => { setIsModalOpen(false); setEditingEducator(null); onEducatorsChange(); };
  const handleCloseModal = () => { setIsModalOpen(false); setEditingEducator(null); };
  const handleDeleteRequest = (e: EducatorDto) => { setDeletingEducator(e); };
  const handleDeleteCancel = () => { setDeletingEducator(null); };

  const handleDeleteConfirm = async () => {
    if (!deletingEducator) return;
    setIsDeleting(true);
    try {
      await ResourceService.deleteEducator(deletingEducator.id);
      setDeletingEducator(null);
      onEducatorsChange();
    } catch (err) {
      console.error('Ошибка удаления преподавателя:', err);
      alert('Не удалось удалить преподавателя. Возможно, он назначен на занятия.');
    } finally {
      setIsDeleting(false);
    }
  };

  // Охват выбранного подразделения. Ошибку не проглатываем показом всех подряд: молча снятый
  // фильтр выглядит как «на кафедре весь состав университета».
  useEffect(() => {
    if (typeof unitFilter !== 'number') {
      setScopeIds(null);
      setScopeError(null);
      return;
    }
    let cancelled = false;
    setScopeError(null);
    ResourceService.getOrgUnitScope(unitFilter)
        .then((scope) => { if (!cancelled) setScopeIds(new Set(scope.educatorIds)); })
        .catch((err) => {
          if (cancelled) return;
          console.error('Ошибка загрузки охвата подразделения:', err);
          setScopeIds(new Set());
          setScopeError('Не удалось получить состав подразделения.');
        });
    return () => { cancelled = true; };
  }, [unitFilter]);

  const unitById = useMemo(() => new Map(units.map((u) => [u.id, u])), [units]);
  const unassignedCount = useMemo(() => educators.filter((e) => e.orgUnitId == null).length, [educators]);
  /** Подразделения, за которыми кто-то числится: расформированные показываем только такие. */
  const attachedUnitIds = useMemo(
      () => new Set(educators.map((e) => e.orgUnitId).filter((id): id is number => id != null)),
      [educators],
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return educators.filter((e) => {
      if (q && !e.name.toLowerCase().includes(q)) return false;
      if (unitFilter === 'none') return e.orgUnitId == null;
      if (typeof unitFilter === 'number') return scopeIds != null && scopeIds.has(e.id);
      return true;
    });
  }, [educators, query, unitFilter, scopeIds]);

  const sections = useMemo<Section[]>(() => {
    const byUnit = new Map<number, EducatorDto[]>();
    const unassigned: EducatorDto[] = [];
    for (const educator of visible) {
      if (educator.orgUnitId == null) {
        unassigned.push(educator);
        continue;
      }
      const bucket = byUnit.get(educator.orgUnitId) ?? [];
      bucket.push(educator);
      byUnit.set(educator.orgUnitId, bucket);
    }

    const byName = (a: EducatorDto, b: EducatorDto) => a.name.localeCompare(b.name, 'ru');
    const result: Section[] = [];

    // Порядок — обход дерева: список читается как структура организации.
    for (const unit of flat) {
      const items = byUnit.get(unit.id);
      if (!items) continue;
      byUnit.delete(unit.id);
      result.push({
        key: `unit-${unit.id}`,
        title: unit.name,
        parent: unit.parentName ?? null,
        short: unit.shortName?.trim() || null,
        items: [...items].sort(byName),
      });
    }

    // Привязка к подразделению, которого нет в выдаче (рассинхрон данных): не теряем людей —
    // имя берём из самой карточки, оно там есть.
    for (const [unitId, items] of byUnit) {
      result.push({
        key: `unit-${unitId}`,
        title: unitById.get(unitId)?.name ?? items[0].orgUnitName ?? `Подразделение #${unitId}`,
        parent: null,
        short: null,
        items: [...items].sort(byName),
      });
    }

    if (unassigned.length > 0) {
      result.push({
        key: 'unassigned',
        title: 'Без подразделения',
        parent: null,
        short: null,
        items: [...unassigned].sort(byName),
      });
    }
    return result;
  }, [visible, flat, unitById]);

  const filtered = query.trim().length > 0 || unitFilter !== 'all';
  const resetFilters = () => { setQuery(''); setUnitFilter('all'); };

  return (
      <div className="space-y-4">
        {/* Панель действий */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-2 text-slate-500">
              <User size={16} />
              <span className="text-sm font-medium">
                {filtered ? (
                    <>Показано: <span className="font-black text-slate-900">{visible.length}</span> из {educators.length}</>
                ) : (
                    <>Всего: <span className="font-black text-slate-900">{educators.length}</span></>
                )}
              </span>
            </div>

            {unassignedCount > 0 && (
                <button
                    onClick={() => setUnitFilter(unitFilter === 'none' ? 'all' : 'none')}
                    className={`px-2 py-1 rounded-lg text-[11px] font-bold transition-colors ${
                        unitFilter === 'none'
                            ? 'bg-amber-500 text-white'
                            : 'bg-amber-50 text-amber-700 hover:bg-amber-100'
                    }`}
                    title="Показать только тех, у кого не указано подразделение"
                >
                  без подразделения: {unassignedCount}
                </button>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Поиск по фамилии"
                  className="w-52 pl-8 pr-7 py-1.5 border border-slate-200 rounded-lg text-xs font-medium outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
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

            <select
                value={unitFilter === 'all' || unitFilter === 'none' ? unitFilter : String(unitFilter)}
                onChange={(e) => {
                  const v = e.target.value;
                  setUnitFilter(v === 'all' || v === 'none' ? v : parseInt(v));
                }}
                disabled={unitsLoading}
                className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-medium bg-white outline-none cursor-pointer focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                title="Фильтр по подразделению — с учётом вложенных"
            >
              <option value="all">Все подразделения</option>
              {flat
                  // Расформированные не предлагаем, но если за ними кто-то числится — показываем,
                  // иначе этих людей не найти через фильтр вовсе.
                  .filter((u) => u.active || attachedUnitIds.has(u.id))
                  .map((u) => (
                      <option key={u.id} value={String(u.id)}>
                        {' '.repeat(u.depth * 4)}
                        {u.name}
                      </option>
                  ))}
              <option value="none">Без подразделения</option>
            </select>

            <button
                onClick={handleCreate}
                className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors shadow-md shadow-blue-600/20"
            >
              <Plus size={16} />
              Добавить преподавателя
            </button>
          </div>
        </div>

        {scopeError && (
            <div className="px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs font-bold text-red-700">
              {scopeError}
            </div>
        )}

        {/* Секции по подразделениям */}
        {educators.length === 0 ? (
            <div className="py-12 text-center bg-white rounded-xl border-2 border-dashed border-slate-200">
              <User size={36} className="mx-auto text-slate-300 mb-3" />
              <h3 className="text-sm font-bold text-slate-700 mb-1">Преподавателей пока нет</h3>
              <p className="text-xs text-slate-500 mb-4">Добавьте первого преподавателя</p>
              <button
                  onClick={handleCreate}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors"
              >
                <Plus size={16} />
                Добавить преподавателя
              </button>
            </div>
        ) : visible.length === 0 ? (
            <div className="py-12 text-center bg-white rounded-xl border-2 border-dashed border-slate-200">
              <Search size={32} className="mx-auto text-slate-300 mb-3" />
              <h3 className="text-sm font-bold text-slate-700 mb-1">Никого не нашлось</h3>
              <p className="text-xs text-slate-500 mb-4">Под текущий поиск и фильтр никто не подходит</p>
              <button
                  onClick={resetFilters}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-slate-100 text-slate-700 rounded-lg font-bold text-xs hover:bg-slate-200 transition-colors"
              >
                Сбросить фильтры
              </button>
            </div>
        ) : (
            <div className="space-y-5">
              {sections.map((section) => (
                  <section key={section.key}>
                    <div className="flex items-baseline gap-2 mb-2 pb-1.5 border-b border-slate-200">
                      <Network size={13} className="text-slate-400 shrink-0 self-center" />
                      {section.parent && (
                          <span className="text-[11px] font-medium text-slate-400 truncate">
                            {section.parent} ·
                          </span>
                      )}
                      <h3 className="text-xs font-black text-slate-700 uppercase tracking-wider truncate">
                        {section.title}
                      </h3>
                      {section.short && (
                          <span className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-[10px] font-black rounded shrink-0"
                                title="Краткое имя — попадает в колонку «Каф» выгрузки">
                            {section.short}
                          </span>
                      )}
                      <span className="ml-auto text-[11px] font-bold text-slate-400 shrink-0">
                        {section.items.length}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2">
                      {section.items.map((educator) => (
                          <EducatorCard
                              key={educator.id}
                              educator={educator}
                              unitShort={
                                educator.orgUnitId != null
                                    ? (unitById.get(educator.orgUnitId)?.shortName?.trim()
                                        || educator.orgUnitName
                                        || null)
                                    : null
                              }
                              getDayShort={getDayShort}
                              getSlotShort={getSlotShort}
                              onEdit={() => handleEdit(educator)}
                              onDelete={() => handleDeleteRequest(educator)}
                          />
                      ))}
                    </div>
                  </section>
              ))}
            </div>
        )}

        {isModalOpen && (
            <EducatorFormModal educator={editingEducator} onClose={handleCloseModal} onSaved={handleSaved} />
        )}

        {deletingEducator && (
            <ConfirmDialog
                title="Удалить преподавателя?"
                message={`Вы уверены, что хотите удалить "${deletingEducator.name}"? Это действие нельзя отменить.`}
                confirmLabel="Удалить"
                cancelLabel="Отмена"
                variant="danger"
                isLoading={isDeleting}
                onConfirm={handleDeleteConfirm}
                onCancel={handleDeleteCancel}
            />
        )}
      </div>
  );
};

interface EducatorCardProps {
  educator: EducatorDto;
  /** Краткое имя подразделения; заголовок секции может уехать за экран в длинном списке. */
  unitShort: string | null;
  getDayShort: (day: string) => string;
  getSlotShort: (slot: string) => string;
  onEdit: () => void;
  onDelete: () => void;
}

const EducatorCard: React.FC<EducatorCardProps> = ({
                                                     educator, unitShort, getDayShort, getSlotShort, onEdit, onDelete,
                                                   }) => {
  const hasDays = educator.preferredDays.length > 0;
  const hasSlots = educator.preferredTimeSlots.length > 0;

  return (
      <div className="bg-white rounded-lg border border-slate-100 shadow-sm hover:shadow-md transition-all overflow-hidden group/card">
        <div className="px-2 py-1.5 space-y-1">
          {/* Фамилия и кафедра — одной строкой: отдельная строка под подразделение занимала
              высоту в КАЖДОЙ карточке, а краткое имя короткое и рядом с фамилией читается. */}
          <div className="flex items-center gap-1">
            <h3 className="text-xs font-black text-slate-900 truncate min-w-0 flex-1" title={educator.name}>
              {educator.name}
            </h3>

            {unitShort && (
                <span
                    className="px-1 py-px bg-slate-100 text-slate-600 text-[10px] font-bold rounded shrink-0 max-w-[45%] truncate"
                    title={educator.orgUnitName ?? undefined}
                >
                  {unitShort}
                </span>
            )}

            <div className="flex items-center gap-0.5 opacity-0 group-hover/card:opacity-100 transition-opacity shrink-0">
              <button
                  onClick={onEdit}
                  className="p-0.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                  title="Редактировать"
              >
                <Pencil size={12} />
              </button>
              <button
                  onClick={onDelete}
                  className="p-0.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                  title="Удалить"
              >
                <Trash2 size={12} />
              </button>
            </div>
          </div>

          {/* Дни и пары — одной строкой: ширины карточки хватает, а группы различимы по цвету
              (дни синие, пары зелёные), поэтому переносить их в две строки незачем. При избытке
              чипов строка перенесётся сама. */}
          {(hasDays || hasSlots) && (
              <div className="flex items-center gap-1 flex-wrap">
                {hasDays && (
                    <>
                      <Calendar size={11} className="text-slate-400 shrink-0" />
                      {educator.preferredDays.map((day) => (
                          <span key={day} className="px-1 py-px bg-blue-50 text-blue-700 text-[10px] font-bold rounded">
                            {getDayShort(day)}
                          </span>
                      ))}
                    </>
                )}
                {hasSlots && (
                    <>
                      <Clock size={11} className={`text-slate-400 shrink-0 ${hasDays ? 'ml-0.5' : ''}`} />
                      {educator.preferredTimeSlots.map((slot) => (
                          <span key={slot} className="px-1 py-px bg-emerald-50 text-emerald-700 text-[10px] font-bold rounded">
                            {getSlotShort(slot)}
                          </span>
                      ))}
                    </>
                )}
              </div>
          )}

          {!hasDays && !hasSlots && (
              <div className="flex items-center gap-1">
                <Calendar size={11} className="text-slate-300 shrink-0" />
                <span className="text-[10px] text-slate-400 italic">Любые дни и пары</span>
              </div>
          )}

          {/* Компактное расписание */}
          {educator.compactSchedule && (
              <div className="flex items-center gap-1">
                <Zap size={11} className="text-emerald-500 shrink-0" />
                <span className="text-[10px] text-emerald-700 font-bold">Компактное</span>
              </div>
          )}
        </div>
      </div>
  );
};
