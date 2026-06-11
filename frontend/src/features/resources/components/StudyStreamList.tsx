import React from 'react';
import { StudyStreamDto } from '../../../types/api';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Layers, Users } from 'lucide-react';

interface StudyStreamListProps {
  streams: StudyStreamDto[];
}

export const StudyStreamList: React.FC<StudyStreamListProps> = ({ streams }) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
      {streams.map((stream) => (
        <Card
          key={stream.id}
          title={stream.name}
          subtitle={`Семестр: ${stream.semester}`}
        >
          <div className="space-y-4">
            <div className="flex items-center gap-2 text-slate-500 mb-2">
              <Users size={16} />
              <span className="text-xs font-bold uppercase tracking-wider">Состав потока:</span>
            </div>
            <div className="flex flex-wrap gap-2">
              {stream.groups.map((group) => (
                <div key={group.id} className="flex items-center gap-2 bg-slate-50 border border-slate-100 px-3 py-1.5 rounded-lg">
                  <span className="text-sm font-bold text-slate-700">{group.name}</span>
                  <Badge variant="slate">{group.size}</Badge>
                </div>
              ))}
            </div>
            
            <div className="pt-2 border-t border-slate-50 flex justify-between items-center">
              <span className="text-xs text-slate-400">ID: {stream.id}</span>
              <div className="flex items-center gap-1 text-emerald-600 font-bold text-xs">
                <Layers size={14} />
                {stream.groups.reduce((acc, g) => acc + g.size, 0)} студентов
              </div>
            </div>
          </div>
        </Card>
      ))}
      {streams.length === 0 && (
        <div className="col-span-full py-12 text-center text-slate-400 bg-white rounded-2xl border-2 border-dashed border-slate-100">
           Потоки еще не созданы
        </div>
      )}
    </div>
  );
};
