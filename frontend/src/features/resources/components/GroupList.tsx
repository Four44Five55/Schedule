import React, { useState } from 'react';
import { GroupDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { GroupFormModal } from './GroupFormModal';
import { Users, Home, Plus, Pencil, Trash2 } from 'lucide-react';

interface GroupListProps {
  groups: GroupDto[];
  onGroupsChange: () => void;
}

export const GroupList: React.FC<GroupListProps> = ({ groups, onGroupsChange }) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<GroupDto | null>(null);
  const [deletingGroup, setDeletingGroup] = useState<GroupDto | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleCreate = () => {
    setEditingGroup(null);
    setIsModalOpen(true);
  };

  const handleEdit = (group: GroupDto) => {
    setEditingGroup(group);
    setIsModalOpen(true);
  };

  const handleSaved = () => {
    setIsModalOpen(false);
    setEditingGroup(null);
    onGroupsChange();
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingGroup(null);
  };

  const handleDeleteRequest = (group: GroupDto) => {
    setDeletingGroup(group);
  };

  const handleDeleteConfirm = async () => {
    if (!deletingGroup) return;
    setIsDeleting(true);
    try {
      await ResourceService.deleteGroup(deletingGroup.id);
      setDeletingGroup(null);
      onGroupsChange();
    } catch (err) {
      console.error('Ошибка удаления группы:', err);
      alert('Не удалось удалить группу. Возможно, она используется в расписании.');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleDeleteCancel = () => {
    setDeletingGroup(null);
  };

  return (
      <div className="space-y-4">
        {/* Панель действий */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-slate-500">
            <Users size={16} />
            <span className="text-sm font-medium">
            Всего: <span className="font-black text-slate-900">{groups.length}</span>
          </span>
          </div>

          <button
              onClick={handleCreate}
              className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors shadow-md shadow-blue-600/20"
          >
            <Plus size={16} />
            Добавить группу
          </button>
        </div>

        {/* Сетка групп */}
        {groups.length > 0 ? (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {groups.map((group) => (
                  <div
                      key={group.id}
                      className="bg-white rounded-lg border border-slate-100 shadow-sm hover:shadow-md transition-all overflow-hidden group/card"
                  >
                    {/* Шапка карточки */}
                    <div className="px-3 py-2.5 border-b border-slate-50">
                      <div className="flex items-center justify-between">
                        <h3 className="text-sm font-black text-slate-900 truncate">{group.name}</h3>
                        <div className="flex items-center gap-0.5 opacity-0 group-hover/card:opacity-100 transition-opacity">
                          <button
                              onClick={() => handleEdit(group)}
                              className="p-1 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                              title="Редактировать"
                          >
                            <Pencil size={13} />
                          </button>
                          <button
                              onClick={() => handleDeleteRequest(group)}
                              className="p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                              title="Удалить"
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Содержимое */}
                    <div className="px-3 py-2 space-y-1.5">
                      <div className="flex items-center gap-2">
                        <Users size={14} className="text-slate-400 shrink-0" />
                        <span className="text-xs font-bold text-slate-700">{group.size} студентов</span>
                      </div>

                      {group.baseAuditorium ? (
                          <div className="flex items-center gap-2">
                            <Home size={14} className="text-slate-400 shrink-0" />
                            <span className="text-xs text-slate-600 truncate">{group.baseAuditorium.name}</span>
                          </div>
                      ) : (
                          <div className="flex items-center gap-2">
                            <Home size={14} className="text-slate-300 shrink-0" />
                            <span className="text-[11px] text-slate-400 italic">Нет аудитории</span>
                          </div>
                      )}
                    </div>
                  </div>
              ))}
            </div>
        ) : (
            <div className="py-12 text-center bg-white rounded-xl border-2 border-dashed border-slate-200">
              <Users size={36} className="mx-auto text-slate-300 mb-3" />
              <h3 className="text-sm font-bold text-slate-700 mb-1">Групп пока нет</h3>
              <p className="text-xs text-slate-500 mb-4">
                Создайте первую учебную группу
              </p>
              <button
                  onClick={handleCreate}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors"
              >
                <Plus size={16} />
                Создать группу
              </button>
            </div>
        )}

        {/* Модальное окно создания/редактирования */}
        {isModalOpen && (
            <GroupFormModal
                group={editingGroup}
                onClose={handleCloseModal}
                onSaved={handleSaved}
            />
        )}

        {/* Диалог подтверждения удаления */}
        {deletingGroup && (
            <ConfirmDialog
                title="Удалить группу?"
                message={`Вы уверены, что хотите удалить группу "${deletingGroup.name}"? Это действие нельзя отменить.`}
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
