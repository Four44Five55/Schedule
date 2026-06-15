import React, { useState } from 'react';
import { EducatorDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { EducatorFormModal } from './EducatorFormModal';
import { useEnums } from '../../../context/EnumContext';
import { User, Clock, Calendar, Zap, Plus, Pencil, Trash2 } from 'lucide-react';

interface EducatorListProps {
  educators: EducatorDto[];
  onEducatorsChange: () => void;
}

export const EducatorList: React.FC<EducatorListProps> = ({ educators, onEducatorsChange }) => {
  const { getDayShort, getSlotShort } = useEnums();

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

  return (
      <div className="space-y-4">
        {/* Панель действий */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-slate-500">
            <User size={16} />
            <span className="text-sm font-medium">
            Всего: <span className="font-black text-slate-900">{educators.length}</span>
          </span>
          </div>
          <button
              onClick={handleCreate}
              className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors shadow-md shadow-blue-600/20"
          >
            <Plus size={16} />
            Добавить преподавателя
          </button>
        </div>

        {/* Сетка */}
        {educators.length > 0 ? (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {educators.map((educator) => (
                  <div
                      key={educator.id}
                      className="bg-white rounded-lg border border-slate-100 shadow-sm hover:shadow-md transition-all overflow-hidden group/card"
                  >
                    {/* Шапка */}
                    <div className="px-3 py-2.5 border-b border-slate-50">
                      <div className="flex items-center justify-between gap-2">
                        <h3 className="text-xs font-black text-slate-900 truncate flex-1" title={educator.name}>
                          {educator.name}
                        </h3>
                        <div className="flex items-center gap-0.5 opacity-0 group-hover/card:opacity-100 transition-opacity shrink-0">
                          <button
                              onClick={() => handleEdit(educator)}
                              className="p-1 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                              title="Редактировать"
                          >
                            <Pencil size={13} />
                          </button>
                          <button
                              onClick={() => handleDeleteRequest(educator)}
                              className="p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                              title="Удалить"
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Содержимое */}
                    <div className="px-3 py-2 space-y-2">
                      {/* Дни — label из бэкенда */}
                      <div className="flex items-center gap-1.5">
                        <Calendar size={12} className="text-slate-400 shrink-0" />
                        {educator.preferredDays.length > 0 ? (
                            <div className="flex gap-0.5 flex-wrap">
                              {educator.preferredDays.map((day) => (
                                  <span key={day} className="px-1.5 py-0.5 bg-blue-50 text-blue-700 text-[10px] font-bold rounded">
                          {getDayShort(day)}
                        </span>
                              ))}
                            </div>
                        ) : (
                            <span className="text-[10px] text-slate-400 italic">Любые дни</span>
                        )}
                      </div>

                      {/* Пары — label из бэкенда */}
                      <div className="flex items-center gap-1.5">
                        <Clock size={12} className="text-slate-400 shrink-0" />
                        {educator.preferredTimeSlots.length > 0 ? (
                            <div className="flex gap-0.5">
                              {educator.preferredTimeSlots.map((slot) => (
                                  <span key={slot} className="px-1.5 py-0.5 bg-emerald-50 text-emerald-700 text-[10px] font-bold rounded">
                          {getSlotShort(slot)}
                        </span>
                              ))}
                            </div>
                        ) : (
                            <span className="text-[10px] text-slate-400 italic">Любые пары</span>
                        )}
                      </div>

                      {/* Компактное расписание */}
                      {educator.compactSchedule && (
                          <div className="flex items-center gap-1.5">
                            <Zap size={12} className="text-emerald-500 shrink-0" />
                            <span className="text-[10px] text-emerald-700 font-bold">Компактное</span>
                          </div>
                      )}
                    </div>
                  </div>
              ))}
            </div>
        ) : (
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
