import React, { useState } from 'react';
import { StudyStreamDto, GroupDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { Plus, Users, Trash2 } from 'lucide-react';

export const StreamsTab: React.FC<{
  streams: StudyStreamDto[];
  groups: GroupDto[];
  onStreamsChange: (streams: StudyStreamDto[]) => void;
}> = ({ streams, groups, onStreamsChange }) => {
  const [showForm, setShowForm] = useState(false);
  const [formName, setFormName] = useState('');
  const [formSemester, setFormSemester] = useState(1);
  const [formGroupIds, setFormGroupIds] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);

  const handleCreate = async () => {
    if (!formName.trim()) return;
    setSaving(true);
    try {
      await ResourceService.createStream({ name: formName.trim(), semester: formSemester, groupIds: formGroupIds });
      const updated = await ResourceService.getStreams();
      onStreamsChange(updated);
      setShowForm(false);
      setFormName('');
      setFormGroupIds([]);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Удалить поток?')) return;
    await ResourceService.deleteStream(id);
    onStreamsChange(streams.filter(s => s.id !== id));
  };

  const toggleGroup = (id: number) => {
    setFormGroupIds(prev => prev.includes(id) ? prev.filter(g => g !== id) : [...prev, id]);
  };

  return (
    <div className="p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-bold text-slate-800 text-sm">Учебные потоки</h3>
        <button
          onClick={() => setShowForm(v => !v)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={13} />
          Новый поток
        </button>
      </div>

      {showForm && (
        <div className="mb-5 p-4 border border-blue-200 rounded-lg bg-blue-50 space-y-3">
          <h4 className="text-sm font-semibold text-blue-900">Создать поток</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Название *</label>
              <input
                value={formName}
                onChange={e => setFormName(e.target.value)}
                placeholder="Поток А"
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Семестр</label>
              <input
                type="number"
                min={1}
                max={12}
                value={formSemester}
                onChange={e => setFormSemester(Number(e.target.value))}
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Группы</label>
            {groups.length === 0 ? (
              <p className="text-xs text-slate-400">Нет доступных групп</p>
            ) : (
              <div className="grid grid-cols-4 gap-1 max-h-32 overflow-y-auto bg-white border border-slate-200 rounded-lg p-2">
                {groups.map(g => (
                  <label key={g.id} className="flex items-center gap-1.5 text-xs cursor-pointer p-1 rounded hover:bg-blue-50">
                    <input
                      type="checkbox"
                      checked={formGroupIds.includes(g.id)}
                      onChange={() => toggleGroup(g.id)}
                      className="w-3 h-3"
                    />
                    <span>{g.name}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              disabled={!formName.trim() || saving}
              className="px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {saving ? 'Сохранение...' : 'Создать'}
            </button>
            <button
              onClick={() => { setShowForm(false); setFormName(''); setFormGroupIds([]); }}
              className="px-3 py-1.5 bg-white text-slate-600 text-xs font-semibold rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors"
            >
              Отмена
            </button>
          </div>
        </div>
      )}

      {streams.length === 0 ? (
        <div className="py-12 text-center text-slate-400">
          <Users className="mx-auto mb-3 opacity-20" size={36} />
          <p className="text-sm">Нет учебных потоков</p>
          <p className="text-xs mt-1 text-slate-300">Создайте поток для назначения занятий</p>
        </div>
      ) : (
        <div className="space-y-2">
          {streams.map(stream => (
            <div key={stream.id} className="border border-slate-200 rounded-lg px-4 py-3 flex items-center justify-between">
              <div>
                <div className="font-medium text-sm text-slate-800">{stream.name}</div>
                <div className="text-xs text-slate-400 mt-0.5">
                  Семестр {stream.semester}
                  {stream.groups.length > 0 && (
                    <span className="ml-1">· {stream.groups.map(g => g.name).join(', ')}</span>
                  )}
                </div>
              </div>
              <button
                onClick={() => handleDelete(stream.id)}
                className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
              >
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
