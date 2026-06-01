import { useState } from 'react';
import type { EducatorDto, DayOfWeek, TimeSlotPair } from '../types';
import { DAY_LABELS, TIME_SLOT_LABELS, ALL_SLOTS } from '../types';

interface Props { educators: EducatorDto[]; onChange: (e: EducatorDto[]) => void; }
const ALL_DAYS: DayOfWeek[] = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'];

export default function EducatorsPage({ educators, onChange }: Props) {
  const [show, setShow] = useState(false);
  const [editId, setEditId] = useState<number|null>(null);
  const [name, setName] = useState('');
  const [days, setDays] = useState<DayOfWeek[]>([]);
  const [slots, setSlots] = useState<TimeSlotPair[]>([]);
  const [compact, setCompact] = useState(false);
  const [search, setSearch] = useState('');

  const filtered = educators.filter(e => e.name.toLowerCase().includes(search.toLowerCase()));
  const toggleDay = (d:DayOfWeek) => setDays(p=>p.includes(d)?p.filter(x=>x!==d):[...p,d]);
  const toggleSlot = (s:TimeSlotPair) => setSlots(p=>p.includes(s)?p.filter(x=>x!==s):[...p,s]);

  const openCreate = () => { setName(''); setDays([]); setSlots([]); setCompact(false); setEditId(null); setShow(true); };
  const openEdit = (e:EducatorDto) => { setName(e.name); setDays([...e.preferredDays]); setSlots([...e.preferredTimeSlots]); setCompact(e.compactSchedule); setEditId(e.id); setShow(true); };
  const save = () => {
    if (!name.trim()) return;
    const data: Omit<EducatorDto,'id'> = { name:name.trim(), preferredDays:days, preferredTimeSlots:slots, compactSchedule:compact };
    if (editId !== null) onChange(educators.map(e => e.id===editId ? {...e,...data} : e));
    else onChange([...educators, { id:Math.max(0,...educators.map(e=>e.id))+1, ...data }]);
    setShow(false);
  };
  const del = (id:number) => onChange(educators.filter(e=>e.id!==id));

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div><h1 className="text-2xl font-bold">Преподаватели</h1><p className="text-sm text-slate-500">{educators.length}</p></div>
        <button onClick={openCreate} className="px-4 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 shadow-lg">+ Добавить</button>
      </div>
      <div className="mb-4"><input type="text" placeholder="Поиск..." value={search} onChange={e=>setSearch(e.target.value)} className="w-full max-w-md px-4 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-indigo-500" /></div>
      <div className="bg-white rounded-2xl border border-slate-200/60 overflow-hidden">
        <table className="w-full"><thead><tr className="bg-slate-50 border-b">
          <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">ФИО</th>
          <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Дни</th>
          <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Пары</th>
          <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase">Комп.</th>
          <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase"></th>
        </tr></thead>
        <tbody>{filtered.map(e => (
          <tr key={e.id} className="border-b border-slate-100 hover:bg-slate-50/50">
            <td className="px-5 py-3 text-sm font-semibold">{e.name}</td>
            <td className="px-5 py-3"><div className="flex gap-1">{e.preferredDays.map(d => <span key={d} className="px-1.5 py-0.5 bg-indigo-50 text-indigo-600 text-[10px] font-semibold rounded">{DAY_LABELS[d]}</span>)}</div></td>
            <td className="px-5 py-3"><div className="flex gap-1">{e.preferredTimeSlots.map(s => <span key={s} className="px-1.5 py-0.5 bg-violet-50 text-violet-600 text-[10px] font-semibold rounded">{TIME_SLOT_LABELS[s].label}</span>)}</div></td>
            <td className="px-5 py-3 text-center text-xs">{e.compactSchedule ? <span className="text-emerald-500">Да</span>:<span className="text-slate-300">Нет</span>}</td>
            <td className="px-5 py-3 text-center"><div className="flex justify-center gap-1">
              <button onClick={()=>openEdit(e)} className="p-1.5 text-slate-400 hover:text-indigo-600"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125" /></svg></button>
              <button onClick={()=>del(e.id)} className="p-1.5 text-slate-400 hover:text-rose-600"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397" /></svg></button>
            </div></td>
          </tr>
        ))}</tbody></table>
      </div>
      {show && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4" onClick={()=>setShow(false)}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg" onClick={e=>e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-4 border-b"><h2 className="text-lg font-bold">{editId?'Редактировать':'Новый преподаватель'}</h2><button onClick={()=>setShow(false)} className="p-1 text-slate-400"><svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg></button></div>
            <div className="p-6 space-y-4">
              <div><label className="block text-sm font-medium mb-1">ФИО *</label><input type="text" value={name} onChange={e=>setName(e.target.value)} className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm" /></div>
              <div><label className="block text-sm font-medium mb-2">Предпочитаемые дни</label><div className="flex flex-wrap gap-2">{ALL_DAYS.map(d => <button key={d} onClick={()=>toggleDay(d)} className={`px-3 py-1.5 text-xs font-medium rounded-lg border ${days.includes(d)?'bg-indigo-100 border-indigo-300 text-indigo-700':'bg-white border-slate-200 text-slate-500'}`}>{DAY_LABELS[d]}</button>)}</div></div>
              <div><label className="block text-sm font-medium mb-2">Предпочитаемые пары</label><div className="flex flex-wrap gap-2">{ALL_SLOTS.map(s => <button key={s} onClick={()=>toggleSlot(s)} className={`px-3 py-1.5 text-xs font-medium rounded-lg border ${slots.includes(s)?'bg-violet-100 border-violet-300 text-violet-700':'bg-white border-slate-200 text-slate-500'}`}>{TIME_SLOT_LABELS[s].label}</button>)}</div></div>
              <div className="flex items-center gap-3"><input type="checkbox" id="cmp" checked={compact} onChange={e=>setCompact(e.target.checked)} className="w-4 h-4" /><label htmlFor="cmp" className="text-sm">Компактное расписание</label></div>
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t bg-slate-50 rounded-b-2xl"><button onClick={()=>setShow(false)} className="px-4 py-2 text-sm font-medium text-slate-600">Отмена</button><button onClick={save} className="px-5 py-2 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700">{editId?'Сохранить':'Создать'}</button></div>
          </div>
        </div>
      )}
    </div>
  );
}
