import { useState, useEffect } from 'react';
import { ResourceService, CurriculumService } from '../services/apiServices';
import type { EducatorDto, AuditoriumDto, GroupDto, DisciplineDto } from '../types/api';

export function useResources() {
  const [data, setData] = useState({
    educators: [] as EducatorDto[],
    auditoriums: [] as AuditoriumDto[],
    groups: [] as GroupDto[],
    disciplines: [] as DisciplineDto[],
    streams: [] as any[], // Добавляем потоки
    loading: true,
    error: null as string | null,
  });

  useEffect(() => {
    Promise.all([
      ResourceService.getEducators(),
      ResourceService.getAuditoriums(),
      ResourceService.getGroups(),
      CurriculumService.getDisciplines(),
      ResourceService.getStreams(),
    ])
      .then(([educators, auditoriums, groups, disciplines, streams]) => {
        setData({ educators, auditoriums, groups, disciplines, streams, loading: false, error: null });
      })
      .catch((err) => {
        console.error('Ошибка загрузки данных:', err);
        setData((prev) => ({ ...prev, loading: false, error: err.message }));
      });
  }, []);

  return data;
}
