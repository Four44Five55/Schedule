import React from 'react';
import { GroupDto } from '../../../types/api';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Users, Home } from 'lucide-react';

interface GroupListProps {
  groups: GroupDto[];
}

export const GroupList: React.FC<GroupListProps> = ({ groups }) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
      {groups.map((group) => (
        <Card
          key={group.id}
          title={group.name}
          subtitle={`ID: ${group.id}`}
        >
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <Users size={18} className="text-slate-400" />
              <span className="text-sm font-bold text-slate-700">{group.size} студентов</span>
            </div>
            
            {group.baseAuditorium && (
              <div className="flex items-center gap-3">
                <Home size={18} className="text-slate-400" />
                <div className="flex flex-col">
                  <span className="text-[10px] text-slate-400 font-bold uppercase">Базовая аудитория</span>
                  <span className="text-sm font-medium text-slate-700">{group.baseAuditorium.name}</span>
                </div>
              </div>
            )}
            
            {!group.baseAuditorium && (
              <div className="text-xs text-slate-400 italic">Базовая аудитория не назначена</div>
            )}
          </div>
        </Card>
      ))}
    </div>
  );
};
