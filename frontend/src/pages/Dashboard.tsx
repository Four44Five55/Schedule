import type {
  EducatorDto, GroupDto, AuditoriumDto, DisciplineDto,
  DisciplineCourseDto, StudyStreamDto, AssignmentDto, StudyPeriodDto,
} from '../types';
import { PERIOD_TYPE_LABELS } from '../types';

interface Props {
  educators: EducatorDto[]; groups: GroupDto[]; auditoriums: AuditoriumDto[];
  disciplines: DisciplineDto[]; disciplineCourses: DisciplineCourseDto[];
  streams: StudyStreamDto[]; assignments: AssignmentDto[];
  studyPeriods: StudyPeriodDto[];
}

export default function Dashboard({ educators, groups, auditoriums, disciplines, disciplineCourses, streams, assignments, studyPeriods }: Props) {
  const cp = studyPeriods[0];
  const allOk = educators.length > 0 && groups.length > 0 && auditoriums.length > 0 && assignments.length > 0;

  const cards = [
    { label:'Преподаватели', v:educators.length, icon:'👤' },
    { label:'Группы', v:groups.length, s:`${groups.reduce((a,g)=>a+g.size,0)} студентов`, icon:'👥' },
    { label:'Аудитории', v:auditoriums.length, icon:'🏫' },
    { label:'Дисциплины', v:disciplines.length, icon:'📚' },
    { label:'Курсы дисциплин', v:disciplineCourses.length, icon:'📋' },
    { label:'Потоки', v:streams.length, icon:'🔀' },
    { label:'Назначения', v:assignments.length, icon:'📌' },
  ];

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900">Дашборд</h1>
        <p className="mt-1 text-slate-500">Обзор системы генерации расписания</p>
      </div>
      {cp && (
        <div className="mb-6 p-4 bg-gradient-to-r from-indigo-50 to-violet-50 border border-indigo-100 rounded-2xl flex items-center gap-3 text-sm">
          <span className="px-2 py-0.5 bg-indigo-100 text-indigo-700 rounded-md text-xs font-semibold">{PERIOD_TYPE_LABELS[cp.periodType]}</span>
          <span className="font-semibold text-slate-900">{cp.name}</span>
          <span className="text-slate-500">({cp.startDate} — {cp.endDate})</span>
        </div>
      )}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mb-8">{cards.map(c => (
        <div key={c.label} className="bg-white rounded-2xl border border-slate-200/60 p-5">
          <div className="flex items-start justify-between">
            <div><p className="text-xs font-medium text-slate-500">{c.label}</p><p className="mt-1 text-2xl font-bold text-slate-900">{c.v}</p>{'s' in c && <p className="text-xs text-slate-400">{c.s}</p>}</div>
            <div className="text-lg">{c.icon}</div>
          </div>
        </div>
      ))}</div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-2xl border border-slate-200/60 p-6">
          <h3 className="text-lg font-semibold mb-4">✅ Готовность данных</h3>
          <div className="space-y-2">
            {[
              ['Преподаватели', educators.length],
              ['Группы', groups.length],
              ['Аудитории', auditoriums.length],
              ['Дисциплины', disciplines.length],
              ['Курсы', disciplineCourses.length],
              ['Слоты плана', disciplineCourses.reduce((s,dc)=>s+(dc as any).curriculumSlots?.length||0,0)],
              ['Потоки', streams.length],
              ['Назначения', assignments.length],
            ].map(([label,count]) => (
              <div key={String(label)} className="flex items-center justify-between px-3 py-2 rounded-lg bg-slate-50">
                <span className="text-sm text-slate-700">{String(label)}</span>
                <span className={`text-xs font-semibold px-2 py-0.5 rounded-md ${Number(count)>0?'bg-emerald-100 text-emerald-700':'bg-rose-100 text-rose-700'}`}>
                  {Number(count)>0 ? '✓':'✗'} {count}
                </span>
              </div>
            ))}
          </div>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200/60 p-6">
          <h3 className="text-lg font-semibold mb-4">📊 Структура</h3>
          <div className="text-xs font-mono text-slate-600 space-y-1.5">
            <p className="text-slate-900 font-bold">Инфраструктура:</p>
            <p>Location → Building → Auditorium + Purpose + Feature[]</p>
            <p>AuditoriumPool → Auditorium[]</p>
            <p className="text-slate-900 font-bold mt-2">Учебный план:</p>
            <p>StudyPeriod → DisciplineCourse → CurriculumSlot</p>
            <p>CurriculumSlot → ThemeLesson + KindOfStudy + AuditoriumPool</p>
            <p>StudyStream → Group[]; SlotChain → неразрывные слоты</p>
            <p className="text-slate-900 font-bold mt-2">Назначения + Генерация:</p>
            <p>Assignment = CurriculumSlot + StudyStream + Educator[]</p>
            <p>ScheduleWorkspace = Grid + ResourceManager</p>
            <p>Генерация: Лекции (фаза1) + Практики (фаза2) + Оптимизация (фаза3)</p>
          </div>
        </div>
      </div>

      <div className={`mt-6 rounded-2xl p-6 text-white ${allOk?'bg-gradient-to-r from-indigo-500 to-violet-600':'bg-gradient-to-r from-slate-400 to-slate-500'}`}>
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div><h3 className="text-lg font-bold">{allOk?'🚀 Готовы к генерации':'⏳ Не все данные заполнены'}</h3>
            <p className="text-sm mt-1 opacity-80">{allOk?`${educators.length} преп., ${assignments.length} назначений — можно запускать`:'Заполните разделы перед генерацией'}</p>
          </div>
          <button className={`shrink-0 px-6 py-2.5 font-semibold rounded-xl shadow-lg ${allOk?'bg-white text-indigo-600 hover:bg-indigo-50':'bg-white/20 text-white/60 cursor-not-allowed'}`}>
            Сгенерировать
          </button>
        </div>
      </div>
    </div>
  );
}
