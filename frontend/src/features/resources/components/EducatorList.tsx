import React from 'react';
import { EducatorDto } from '../../../types/api';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { User, Clock, CheckCircle2, AlertCircle } from 'lucide-react';

interface EducatorListProps {
  educators: EducatorDto[];
}

export const EducatorList: React.FC<EducatorListProps> = ({ educators }) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
      {educators.map((educator) => (
        <Card
          key={educator.id}
          title={educator.name}
          footer={
            <div className="flex items-center justify-between">
              <span className="text-slate-400">ID: {educator.id}</span>
              <button className="text-blue-600 font-bold hover:underline">Профиль</button>
            </div>
          }
        >
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Clock size={16} className="text-slate-400" />
              <div className="flex flex-wrap gap-1">
                {educator.preferredDays.length > 0 ? (
                  educator.preferredDays.map((day) => (
                    <Badge key={day} variant="blue">{day.slice(0, 3)}</Badge>
                  ))
                ) : (
                  <span className="text-xs text-slate-400">Дни не выбраны</span>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2">
              {educator.compactSchedule ? (
                <div className="flex items-center gap-2 text-emerald-600 text-xs font-bold uppercase tracking-wide">
                  <CheckCircle2 size={16} />
                  Компактное расписание
                </div>
              ) : (
                <div className="flex items-center gap-2 text-slate-400 text-xs font-bold uppercase tracking-wide">
                  <AlertCircle size={16} />
                  Обычное расписание
                </div>
              )}
            </div>
          </div>
        </Card>
      ))}
    </div>
  );
};
