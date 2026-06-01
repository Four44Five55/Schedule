import { useState, useCallback, useEffect } from "react";
import type { Page, EducatorDto, GroupDto, StudyPeriodDto, StudyStreamDto } from "./types";
import {
  educatorsApi, groupsApi, auditoriumsApi, disciplinesApi, studyPeriodsApi,
  studyStreamsApi, educatorConstraintsApi, groupConstraintsApi, auditoriumConstraintsApi,
  locationsApi, buildingsApi, featuresApi, purposesApi, poolsApi,
  disciplineCoursesApi, curriculumSlotsApi, assignmentsApi,
} from "./api/client";
import Sidebar from "./components/Sidebar";
import Dashboard from "./pages/Dashboard";
import EducatorsPage from "./pages/EducatorsPage";

function SimpleTable<T extends {id:number; name:string}>({title, subtitle, data, onUpdate, search}:{
  title:string; subtitle:string; data:T[]; onUpdate:(d:T[])=>void; search?:boolean;
}) {
  const [s, setS] = useState("");
  const [m, setM] = useState(false); const [eId, setEId] = useState<number|null>(null); const [n, setN] = useState("");
  const filtered = search ? data.filter(d => d.name.toLowerCase().includes(s.toLowerCase())) : data;
  const openC = () => { setN(""); setEId(null); setM(true); };
  const openE = (d:T) => { setN(d.name); setEId(d.id); setM(true); };
  const save = () => {
    if (!n.trim()) return;
    if (eId!==null) onUpdate(data.map(d => d.id===eId ? {...d, name:n.trim()} : d));
    else onUpdate([...data, {id:Math.max(0,...data.map(d=>d.id))+1, name:n.trim()} as T]);
    setM(false);
  };
  const del = (id:number) => onUpdate(data.filter(d => d.id!==id));
  return (
      <div>
        <div className="flex justify-between items-center mb-6">
          <div><h1 className="text-2xl font-bold">{title}</h1><p className="text-sm text-slate-500">{subtitle} ({data.length})</p></div>
          <button onClick={openC} className="px-4 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 shadow-lg">+ Добавить</button>
        </div>
        {search && <div className="mb-4"><input type="text" placeholder="Поиск..." value={s} onChange={e=>setS(e.target.value)} className="w-full max-w-md px-4 py-2.5 border border-slate-300 rounded-xl text-sm" /></div>}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">{filtered.map(d => (
            <div key={d.id} className="bg-white rounded-2xl border border-slate-200/60 p-5 hover:shadow-md group">
              <div className="flex justify-between items-start">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-indigo-400 to-violet-500 flex items-center justify-center text-white font-bold text-sm shrink-0">{d.name.slice(0,2)}</div>
                  <div><p className="text-sm font-bold">{d.name}</p><p className="text-xs text-slate-400">ID:{d.id}</p></div>
                </div>
                <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button onClick={()=>openE(d)} className="p-1.5 text-slate-400 hover:text-indigo-600"><svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z"/></svg></button>
                  <button onClick={()=>del(d.id)} className="p-1.5 text-slate-400 hover:text-rose-600"><svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9"/></svg></button>
                </div>
              </div>
            </div>
        ))}</div>
        {m && (
            <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4" onClick={()=>setM(false)}>
              <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md" onClick={e=>e.stopPropagation()}>
                <div className="flex items-center justify-between px-6 py-4 border-b"><h2 className="text-lg font-bold">{eId?"Редактировать":"Новый"}</h2><button onClick={()=>setM(false)} className="p-1 text-slate-400"><svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12"/></svg></button></div>
                <div className="p-6"><label className="block text-sm font-medium mb-1">Название *</label><input type="text" value={n} onChange={e=>setN(e.target.value)} className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm" /></div>
                <div className="flex justify-end gap-3 px-6 py-4 border-t bg-slate-50 rounded-b-2xl"><button onClick={()=>setM(false)} className="px-4 py-2 text-sm text-slate-600">Отмена</button><button onClick={save} className="px-5 py-2 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700">{eId?"Сохранить":"Создать"}</button></div>
              </div>
            </div>
        )}
      </div>
  );
}

function SchedulePage() {
  return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Расписание</h1>
        <div className="bg-white rounded-2xl border border-slate-200/60 p-6">
          <p className="text-slate-500 text-sm">Результат генерации будет отображаться здесь.</p>
        </div>
      </div>
  );
}

function ConstraintsPage({title, data, onUpdate}:{
  title:string; data:any[]; onUpdate:(d:any[])=>void;
}) {
  const del = (id:number) => onUpdate(data.filter(d=>d.id!==id));
  return (
      <div>
        <div className="flex justify-between items-center mb-6">
          <div><h1 className="text-2xl font-bold">{title}</h1><p className="text-sm text-slate-500">{data.length}</p></div>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200/60 overflow-hidden">
          <table className="w-full"><thead><tr className="bg-slate-50 border-b"><th className="px-5 py-3 text-left text-xs font-semibold text-slate-500">ID</th><th className="px-5 py-3 text-center text-xs font-semibold text-slate-500">Тип</th><th className="px-5 py-3 text-left text-xs font-semibold text-slate-500">Период</th><th className="px-5 py-3 text-left text-xs font-semibold text-slate-500">Описание</th><th className="px-5 py-3 text-center"></th></tr></thead>
            <tbody>{data.length===0?<tr><td colSpan={5} className="px-5 py-12 text-center text-slate-400 text-sm">Нет ограничений</td></tr>:data.map((c:any) => (
                <tr key={c.id} className="border-b border-slate-100 hover:bg-slate-50/50">
                  <td className="px-5 py-3 text-sm font-medium">{(c.educatorId||c.groupId||c.auditoriumId||c.id)+""}</td>
                  <td className="px-5 py-3 text-center text-xs">{c.kindOfConstraint}</td>
                  <td className="px-5 py-3 text-xs font-mono text-slate-500">{c.startDate} — {c.endDate}</td>
                  <td className="px-5 py-3 text-xs text-slate-500">{c.description||"—"}</td>
                  <td className="px-5 py-3 text-center"><button onClick={()=>del(c.id)} className="p-1.5 text-slate-400 hover:text-rose-600"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9"/></svg></button></td>
                </tr>
            ))}</tbody></table>
        </div>
      </div>
  );
}

function Loading() {
  return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin h-8 w-8 border-4 border-indigo-500 border-t-transparent rounded-full"></div>
        <span className="ml-3 text-slate-500">Загрузка...</span>
      </div>
  );
}

export default function App() {
  const [page, setPage] = useState<Page>("dashboard");
  const navigate = useCallback((p:Page)=>setPage(p), []);
  const [loading, setLoading] = useState(true);

  const [educators, setEducators] = useState<EducatorDto[]>([]);
  const [groups, setGroups] = useState<GroupDto[]>([]);
  const [auditoriums, setAuditoriums] = useState<any[]>([]);
  const [disciplines, setDisciplines] = useState<any[]>([]);
  const [courses, setCourses] = useState<any[]>([]);
  const [slots, setSlots] = useState<any[]>([]);
  const [streams, setStreams] = useState<StudyStreamDto[]>([]);
  const [assignments, setAssignments] = useState<any[]>([]);
  const [periods, setPeriods] = useState<StudyPeriodDto[]>([]);
  const [eConstraints, setEConstraints] = useState<any[]>([]);
  const [gConstraints, setGConstraints] = useState<any[]>([]);
  const [aConstraints, setAConstraints] = useState<any[]>([]);
  const [locations, setLocations] = useState<any[]>([]);
  const [buildings, setBuildings] = useState<any[]>([]);
  const [features, setFeatures] = useState<any[]>([]);
  const [purposes, setPurposes] = useState<any[]>([]);
  const [pools, setPools] = useState<any[]>([]);

  useEffect(() => {
    Promise.all([
      educatorsApi.getAll().catch(() => []),
      groupsApi.getAll().catch(() => []),
      auditoriumsApi.getAll().catch(() => []),
      disciplinesApi.getAll().catch(() => []),
      studyPeriodsApi.getAll().catch(() => []),
      studyStreamsApi.getAll().catch(() => []),
      educatorConstraintsApi.getAll().catch(() => []),
      groupConstraintsApi.getAll().catch(() => []),
      auditoriumConstraintsApi.getAll().catch(() => []),
      locationsApi.getAll().catch(() => []),
      buildingsApi.getAll().catch(() => []),
      featuresApi.getAll().catch(() => []),
      purposesApi.getAll().catch(() => []),
      poolsApi.getAll().catch(() => []),
      disciplineCoursesApi.getAll().catch(() => []),
      curriculumSlotsApi.getAll().catch(() => []),
      assignmentsApi.getAll().catch(() => []),
    ]).then(([
               e, g, a, d, sp, ss, ec, gc, ac,
               loc, bld, feat, purp, pl,
               dc, cs, asgn,
             ]) => {
      setEducators(e as EducatorDto[]);
      setGroups(g as GroupDto[]);
      setAuditoriums(a);
      setDisciplines(d);
      setPeriods(sp as StudyPeriodDto[]);
      setStreams(ss as StudyStreamDto[]);
      setEConstraints(ec);
      setGConstraints(gc);
      setAConstraints(ac);
      setLocations(loc);
      setBuildings(bld);
      setFeatures(feat);
      setPurposes(purp);
      setPools(pl);
      setCourses(dc);
      setSlots(cs);
      setAssignments(asgn);
      setLoading(false);
    });
  }, []);

  const render = () => {
    if (loading) return <Loading />;
    switch (page) {
      case "dashboard":
        return <Dashboard educators={educators} groups={groups} auditoriums={auditoriums} disciplines={disciplines} disciplineCourses={courses} streams={streams} assignments={assignments} studyPeriods={periods} />;
      case "schedule": return <SchedulePage />;
      case "educators": return <EducatorsPage educators={educators} onChange={setEducators} />;
      case "groups": return <SimpleTable title="Группы" subtitle="Учебные группы" data={groups} onUpdate={setGroups as any} search />;
      case "auditoriums": return <SimpleTable title="Аудитории" subtitle="Управление аудиториями" data={auditoriums} onUpdate={setAuditoriums as any} search />;
      case "disciplines": return <SimpleTable title="Дисциплины" subtitle="Учебные дисциплины" data={disciplines} onUpdate={setDisciplines as any} search />;
      case "streams": return <SimpleTable title="Потоки" subtitle="Учебные потоки" data={streams} onUpdate={setStreams as any} search />;
      case "locations": return <SimpleTable title="Локации" subtitle="Территории расположения" data={locations} onUpdate={setLocations as any} search />;
      case "buildings": return <SimpleTable title="Корпуса" subtitle="Учебные корпуса" data={buildings} onUpdate={setBuildings as any} search />;
      case "features": return <SimpleTable title="Оснащение" subtitle="Оснащение аудиторий" data={features} onUpdate={setFeatures as any} search />;
      case "purposes": return <SimpleTable title="Назначения" subtitle="Назначения аудиторий" data={purposes} onUpdate={setPurposes as any} search />;
      case "pools": return <SimpleTable title="Пулы аудиторий" subtitle="Группы аудиторий" data={pools} onUpdate={setPools as any} search />;
      case "discipline-courses": return <SimpleTable title="Курсы дисциплин" subtitle="Связь дисциплин с семестрами" data={courses} onUpdate={setCourses as any} search />;
      case "curriculum-slots": return <div><h1 className="text-2xl font-bold mb-4">Слоты учебного плана</h1><p className="text-slate-400">Всего: {slots.length}</p></div>;
      case "study-periods": return <SimpleTable title="Учебные периоды" subtitle="Семестры и сессии" data={periods} onUpdate={()=>{}} search />;
      case "assignments": return <SimpleTable title="Назначения" subtitle="Связь слотов, потоков и преподавателей" data={assignments} onUpdate={setAssignments as any} search />;
      case "slot-chains": return <SimpleTable title="Сцепки слотов" subtitle="Неразрывные цепочки занятий" data={[]} onUpdate={()=>{}} />;
      case "educator-constraints": return <ConstraintsPage title="Ограничения преподавателей" data={eConstraints} onUpdate={setEConstraints} />;
      case "group-constraints": return <ConstraintsPage title="Ограничения групп" data={gConstraints} onUpdate={setGConstraints} />;
      case "auditorium-constraints": return <ConstraintsPage title="Ограничения аудиторий" data={aConstraints} onUpdate={setAConstraints} />;
      default: return <div className="p-8 text-slate-400 text-center">Страница в разработке</div>;
    }
  };

  return (
      <div className="min-h-screen bg-slate-50">
        <Sidebar currentPage={page} onNavigate={navigate} />
        <main className="ml-64 p-6 lg:p-8">
          {render()}
        </main>
      </div>
  );
}
