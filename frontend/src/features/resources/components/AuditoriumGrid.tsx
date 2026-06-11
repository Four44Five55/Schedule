import React from 'react';
import { AuditoriumDto } from '../../../types/api';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { MapPin, Users, Tag } from 'lucide-react';

interface AuditoriumGridProps {
  auditoriums: AuditoriumDto[];
}

export const AuditoriumGrid: React.FC<AuditoriumGridProps> = ({ auditoriums }) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
      {auditoriums.map((aud) => (
        <Card
          key={aud.id}
          title={aud.name}
          subtitle={`${aud.building.name} • ${aud.building.location.name}`}
        >
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1.5 text-slate-700">
                <Users size={16} className="text-slate-400" />
                <span className="text-sm font-bold">{aud.capacity} мест</span>
              </div>
              {aud.purpose && (
                <div className="flex items-center gap-1.5 text-slate-700">
                  <Tag size={16} className="text-slate-400" />
                  <span className="text-sm font-medium">{aud.purpose.name}</span>
                </div>
              )}
            </div>

            <div className="flex flex-wrap gap-1.5">
              {aud.features.map((feat) => (
                <Badge key={feat.code} variant="slate">
                  {feat.name}
                </Badge>
              ))}
            </div>
          </div>
        </Card>
      ))}
    </div>
  );
};
