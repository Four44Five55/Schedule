import React, { useState } from 'react';
import { StudyStreamDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { StudyStreamFormModal } from './StudyStreamFormModal';
import { Layers, Users, Plus, Pencil, Trash2 } from 'lucide-react';

interface StudyStreamListProps {
  streams: StudyStreamDto[];
  onStreamsChange: () => void;
}

export const StudyStreamList: React.FC<StudyStreamListProps> = ({ streams, onStreamsChange }) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingStream, setEditingStream] = useState<StudyStreamDto | null>(null);
  const [deletingStream, setDeletingStream] = useState<StudyStreamDto | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleCreate = () => { setEditingStream(null); setIsModalOpen(true); };
  const handleEdit = (s: StudyStreamDto) => { setEditingStream(s); setIsModalOpen(true); };
  const handleSaved = () => { setIsModalOpen(false); setEditingStream(null); onStreamsChange(); };
  const handleCloseModal = () => { setIsModalOpen(false); setEditingStream(null); };
  const handleDeleteRequest = (s: StudyStreamDto) => { setDeletingStream(s); };
  const handleDeleteCancel = () => { setDeletingStream(null); };

  const handleDeleteConfirm = async () => {
    if (!deletingStream) return;
    setIsDeleting(true);
    try {
      await ResourceService.deleteStream(deletingStream.id);
      setDeletingStream(null);
      onStreamsChange();
    } catch (err) {
      console.error('Ошибка удаления потока:', err);
      alert('Не удалось удалить поток. Возможно, он используется в расписании.');
    } finally {
      setIsDeleting(false);
    }
  };

  return (
      <div className="space-y-4">
        {/* Панель действий */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-slate-500">
            <Layers size={16} />
            <span className="text-sm font-medium">
            Всего: <span className="font-black text-slate-900">{streams.length}</span>
          </span>
          </div>

          <button
              onClick={handleCreate}
              className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors shadow-md shadow-blue-600/20"
          >
            <Plus size={16} />
            Добавить поток
          </button>
        </div>

        {/* Сетка потоков */}
        {streams.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
              {streams.map((stream) => {
                const totalStudents = stream.groups.reduce((sum, g) => sum + g.size, 0);

                return (
                    <div
                        key={stream.id}
                        className="bg-white rounded-lg border border-slate-100 shadow-sm hover:shadow-md transition-all overflow-hidden group/card"
                    >
                      {/* Шапка */}
                      <div className="px-3 py-2.5 border-b border-slate-50">
                        <div className="flex items-center justify-between gap-2">
                          <h3 className="text-sm font-black text-slate-900 truncate flex-1" title={stream.name}>
                            {stream.name}
                          </h3>
                          <div className="flex items-center gap-0.5 opacity-0 group-hover/card:opacity-100 transition-opacity shrink-0">
                            <button
                                onClick={() => handleEdit(stream)}
                                className="p-1 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                                title="Редактировать"
                            >
                              <Pencil size={13} />
                            </button>
                            <button
                                onClick={() => handleDeleteRequest(stream)}
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
                        <div className="flex items-center gap-2">
                          <BookOpenIcon className="text-slate-400" size={14} />
                          <span className="text-xs font-bold text-slate-700">{stream.semester} семестр</span>
                        </div>

                        <div className="flex items-center gap-2">
                          <Users size={14} className="text-slate-400 shrink-0" />
                          <span className="text-xs text-slate-600">
                      {stream.groups.length} групп · <span className="font-bold text-emerald-600">{totalStudents} студентов</span>
                    </span>
                        </div>

                        {stream.groups.length > 0 && (
                            <div className="flex flex-wrap gap-1 pt-1">
                              {stream.groups.map((group) => (
                                  <span key={group.id} className="px-1.5 py-0.5 bg-slate-100 text-slate-700 text-[10px] font-bold rounded">
                          {group.name}
                        </span>
                              ))}
                            </div>
                        )}
                      </div>
                    </div>
                );
              })}
            </div>
        ) : (
            <div className="py-12 text-center bg-white rounded-xl border-2 border-dashed border-slate-200">
              <Layers size={36} className="mx-auto text-slate-300 mb-3" />
              <h3 className="text-sm font-bold text-slate-700 mb-1">Потоков пока нет</h3>
              <p className="text-xs text-slate-500 mb-4">Создайте первый учебный поток</p>
              <button
                  onClick={handleCreate}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors"
              >
                <Plus size={16} />
                Создать поток
              </button>
            </div>
        )}

        {/* Модальное окно */}
        {isModalOpen && (
            <StudyStreamFormModal
                stream={editingStream}
                onClose={handleCloseModal}
                onSaved={handleSaved}
            />
        )}

        {/* Диалог удаления */}
        {deletingStream && (
            <ConfirmDialog
                title="Удалить поток?"
                message={`Вы уверены, что хотите удалить "${deletingStream.name}"? Это действие нельзя отменить.`}
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

// Иконка книги для семестра
const BookOpenIcon = ({ className, size }: { className?: string; size: number }) => (
    <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
    >
      <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
      <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
    </svg>
);
