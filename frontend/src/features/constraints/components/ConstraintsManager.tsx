import React, { useState, useEffect } from 'react';
import { ConstraintsService } from '../../../services/apiServices';
import { EducatorConstraintDto, GroupConstraintDto, AuditoriumConstraintDto } from '../../../types/api';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { User, Users, School, Calendar, Info } from 'lucide-react';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';

export const ConstraintsManager: React.FC = () => {
  const [educatorConstraints, setEducatorConstraints] = useState<EducatorConstraintDto[]>([]);
  const [groupConstraints, setGroupConstraints] = useState<GroupConstraintDto[]>([]);
  const [auditoriumConstraints, setAuditoriumConstraints] = useState<AuditoriumConstraintDto[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      ConstraintsService.getEducatorConstraints(),
      ConstraintsService.getGroupConstraints(),
      ConstraintsService.getAuditoriumConstraints()
    ]).then(([ec, gc, ac]) => {
      setEducatorConstraints(ec);
      setGroupConstraints(gc);
      setAuditoriumConstraints(ac);
      setLoading(false);
    });
  }, []);

  if (loading) return <div className="p-8 text-center animate-pulse text-slate-400">Загрузка ограничений...</div>;

  return (
    <div className="space-y-8">
      <section>
        <div className="flex items-center gap-2 mb-4">
          <User className="text-blue-500" size={20} />
          <h2 className="font-bold text-slate-800">Преподаватели</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {educatorConstraints.map(c => (
            <ConstraintCard key={c.id} name={c.educatorName} type={c.kindOfConstraint} start={c.startDate} end={c.endDate} desc={c.description} />
          ))}
          {educatorConstraints.length === 0 && <EmptyState label="Ограничений преподавателей не найдено" />}
        </div>
      </section>

      <section>
        <div className="flex items-center gap-2 mb-4">
          <Users className="text-amber-500" size={20} />
          <h2 className="font-bold text-slate-800">Группы</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {groupConstraints.map(c => (
            <ConstraintCard key={c.id} name={c.groupName} type={c.kindOfConstraint} start={c.startDate} end={c.endDate} desc={c.description} />
          ))}
          {groupConstraints.length === 0 && <EmptyState label="Ограничений групп не найдено" />}
        </div>
      </section>

      <section>
        <div className="flex items-center gap-2 mb-4">
          <School className="text-emerald-500" size={20} />
          <h2 className="font-bold text-slate-800">Аудитории</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {auditoriumConstraints.map(c => (
            <ConstraintCard key={c.id} name={c.auditoriumName} type={c.kindOfConstraint} start={c.startDate} end={c.endDate} desc={c.description} />
          ))}
          {auditoriumConstraints.length === 0 && <EmptyState label="Ограничений аудиторий не найдено" />}
        </div>
      </section>
    </div>
  );
};

const ConstraintCard = ({ name, type, start, end, desc }: any) => (
  <Card className="border-l-4 border-l-red-400">
    <div className="space-y-3">
      <div className="flex justify-between items-start">
        <h4 className="font-bold text-slate-800">{name}</h4>
        <Badge variant="red">{type}</Badge>
      </div>
      <div className="flex items-center gap-2 text-xs text-slate-500">
        <Calendar size={14} />
        {format(new Date(start), 'dd MMM', { locale: ru })} — {format(new Date(end), 'dd MMM yyyy', { locale: ru })}
      </div>
      {desc && (
        <div className="flex items-start gap-2 bg-slate-50 p-2 rounded text-[11px] text-slate-600">
          <Info size={14} className="shrink-0 mt-0.5" />
          {desc}
        </div>
      )}
    </div>
  </Card>
);

const EmptyState = ({ label }: { label: string }) => (
  <div className="col-span-full py-6 text-center text-xs text-slate-400 italic">
    {label}
  </div>
);
