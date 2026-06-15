import React, { useState } from 'react';
import { AuditoriumDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { AuditoriumFormModal } from './AuditoriumFormModal';
import { Home, Users, Tag, Plus, Pencil, Trash2, Building2 } from 'lucide-react';

interface AuditoriumGridProps {
  auditoriums: AuditoriumDto[];
  onAuditoriumsChange: () => void;
}

export const AuditoriumGrid: React.FC<AuditoriumGridProps> = ({
                                                                auditoriums,
                                                                onAuditoriumsChange,
                                                              }) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAuditorium, setEditingAuditorium] = useState<AuditoriumDto | null>(null);
  const [deletingAuditorium, setDeletingAuditorium] = useState<AuditoriumDto | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleCreate = () => {
    setEditingAuditorium(null);
    setIsModalOpen(true);
  };

  const handleEdit = (a: AuditoriumDto) => {
    setEditingAuditorium(a);
    setIsModalOpen(true);
  };

  const handleSaved = () => {
    setIsModalOpen(false);
    setEditingAuditorium(null);
    onAuditoriumsChange();
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingAuditorium(null);
  };

  const handleDeleteRequest = (a: AuditoriumDto) => setDeletingAuditorium(a);
  const handleDeleteCancel = () => setDeletingAuditorium(null);

  const handleDeleteConfirm = async () => {
    if (!deletingAuditorium) return;
    setIsDeleting(true);
    try {
      await ResourceService.deleteAuditorium(deletingAuditorium.id);
      setDeletingAuditorium(null);
      onAuditoriumsChange();
    } catch (err) {
      console.error('Ошибка удаления аудитории:', err);
      alert('Не удалось удалить аудиторию. Возможно, она используется в расписании.');
    } finally {
      setIsDeleting(false);
    }
  };

  return (
      <div className="space-y-4">
        {/* Панель действий */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-slate-500">
            <Home size={16} />
            <span className="text-sm font-medium">
            Всего: <span className="font-black text-slate-900">{auditoriums.length}</span>
          </span>
          </div>
          <button
              onClick={handleCreate}
              className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors shadow-md shadow-blue-600/20"
          >
            <Plus size={16} />
            Добавить аудиторию
          </button>
        </div>

        {/* Сетка */}
        {auditoriums.length > 0 ? (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {auditoriums.map((aud) => (
                  <div
                      key={aud.id}
                      className="bg-white rounded-lg border border-slate-100 shadow-sm hover:shadow-md transition-all overflow-hidden group/card"
                  >
                    {/* Шапка */}
                    <div className="px-3 py-2.5 border-b border-slate-50">
                      <div className="flex items-center justify-between gap-2">
                        <h3
                            className="text-xs font-black text-slate-900 truncate flex-1"
                            title={aud.name}
                        >
                          {aud.name}
                        </h3>
                        <div className="flex items-center gap-0.5 opacity-0 group-hover/card:opacity-100 transition-opacity shrink-0">
                          <button
                              onClick={() => handleEdit(aud)}
                              className="p-1 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                              title="Редактировать"
                          >
                            <Pencil size={13} />
                          </button>
                          <button
                              onClick={() => handleDeleteRequest(aud)}
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
                      {/* Вместимость */}
                      <div className="flex items-center gap-1.5">
                        <Users size={12} className="text-slate-400 shrink-0" />
                        <span className="text-[11px] text-slate-700 font-bold">
                    {aud.capacity} мест
                  </span>
                      </div>

                      {/* Корпус */}
                      <div className="flex items-center gap-1.5">
                        <Building2 size={12} className="text-slate-400 shrink-0" />
                        <span className="text-[11px] text-slate-600 truncate" title={aud.building?.name}>
                    {aud.building?.name ?? '—'}
                  </span>
                      </div>

                      {/* Назначение */}
                      {aud.purpose && (
                          <div className="flex items-center gap-1.5">
                            <Tag size={12} className="text-slate-400 shrink-0" />
                            <span
                                className="text-[11px] text-slate-600 truncate"
                                title={aud.purpose.name}
                            >
                      {aud.purpose.name}
                    </span>
                          </div>
                      )}

                      {/* Особенности */}
                      {aud.features && aud.features.length > 0 && (
                          <div className="flex flex-wrap gap-0.5 pt-1 border-t border-slate-50">
                            {aud.features.slice(0, 3).map((f) => (
                                <span
                                    key={f.id}
                                    className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-[9px] font-bold rounded truncate max-w-[80px]"
                                    title={f.name}
                                >
                        {f.name}
                      </span>
                            ))}
                            {aud.features.length > 3 && (
                                <span className="px-1.5 py-0.5 text-[9px] text-slate-400 font-bold">
                        +{aud.features.length - 3}
                      </span>
                            )}
                          </div>
                      )}
                    </div>
                  </div>
              ))}
            </div>
        ) : (
            <div className="py-12 text-center bg-white rounded-xl border-2 border-dashed border-slate-200">
              <Home size={36} className="mx-auto text-slate-300 mb-3" />
              <h3 className="text-sm font-bold text-slate-700 mb-1">Аудиторий пока нет</h3>
              <p className="text-xs text-slate-500 mb-4">Добавьте первую аудиторию</p>
              <button
                  onClick={handleCreate}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors"
              >
                <Plus size={16} />
                Добавить аудиторию
              </button>
            </div>
        )}

        {/* Модалка */}
        {isModalOpen && (
            <AuditoriumFormModal
                auditorium={editingAuditorium}
                onClose={handleCloseModal}
                onSaved={handleSaved}
            />
        )}

        {/* Подтверждение удаления */}
        {deletingAuditorium && (
            <ConfirmDialog
                title="Удалить аудиторию?"
                message={`Вы уверены, что хотите удалить "${deletingAuditorium.name}"? Это действие нельзя отменить.`}
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
