import React, { useState } from 'react';
import { AuditoriumDto } from '../../../types/api';
import { AuditoriumGrid } from './AuditoriumGrid';
import { BuildingList } from './BuildingList';
import { LocationList } from './LocationList';
import { Home, Building2, MapPin } from 'lucide-react';
import { cn } from '../../../utils/cn';

interface AuditoriumsSectionProps {
    auditoriums: AuditoriumDto[];
    onAuditoriumsChange: () => void;
}

type SubTab = 'auditoriums' | 'buildings' | 'locations';

/**
 * Раздел «Аудитории»: аудитории, учебные корпуса и локации под одной крышей. Иерархия
 * локация → корпус → аудитория, поэтому управлять ими логично рядом. Переключение — сегментами.
 */
export const AuditoriumsSection: React.FC<AuditoriumsSectionProps> = ({
                                                                          auditoriums,
                                                                          onAuditoriumsChange,
                                                                      }) => {
    const [tab, setTab] = useState<SubTab>('auditoriums');

    return (
        <div className="space-y-4">
            {/* Сегменты */}
            <div className="inline-flex items-center gap-1 p-1 bg-slate-100 rounded-xl">
                <SegButton
                    active={tab === 'auditoriums'}
                    onClick={() => setTab('auditoriums')}
                    icon={<Home size={14} />}
                    label="Аудитории"
                />
                <SegButton
                    active={tab === 'buildings'}
                    onClick={() => setTab('buildings')}
                    icon={<Building2 size={14} />}
                    label="Корпуса"
                />
                <SegButton
                    active={tab === 'locations'}
                    onClick={() => setTab('locations')}
                    icon={<MapPin size={14} />}
                    label="Локации"
                />
            </div>

            {tab === 'auditoriums' && (
                <AuditoriumGrid auditoriums={auditoriums} onAuditoriumsChange={onAuditoriumsChange} />
            )}
            {tab === 'buildings' && <BuildingList onAuditoriumsChanged={onAuditoriumsChange} />}
            {tab === 'locations' && <LocationList />}
        </div>
    );
};

const SegButton: React.FC<{
    active: boolean;
    onClick: () => void;
    icon: React.ReactNode;
    label: string;
}> = ({ active, onClick, icon, label }) => (
    <button
        onClick={onClick}
        className={cn(
            'flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all',
            active
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
        )}
    >
        {icon}
        {label}
    </button>
);
