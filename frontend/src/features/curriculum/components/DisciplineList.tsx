import React from 'react';
import { DisciplineDto } from '../../../types/api';
import { Card } from '../../../components/ui/Card';
import { BookOpen, Layers } from 'lucide-react';

interface DisciplineListProps {
  disciplines: DisciplineDto[];
}

export const DisciplineList: React.FC<DisciplineListProps> = ({ disciplines }) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
      {disciplines.map((disc) => (
        <Card
          key={disc.id}
          title={disc.name}
          subtitle={disc.abbreviation}
        >
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <Layers size={18} className="text-slate-400" />
              <span className="text-sm text-slate-600">
                {disc.courses?.length || 0} учебных курсов (семестров)
              </span>
            </div>
            
            <div className="pt-2">
              <button className="text-xs font-bold text-blue-600 hover:text-blue-800 transition-colors flex items-center gap-1">
                <BookOpen size={14} />
                УПРАВЛЯТЬ ТЕМАМИ
              </button>
            </div>
          </div>
        </Card>
      ))}
    </div>
  );
};
