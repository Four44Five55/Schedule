import React, { useEffect, useRef, useState } from 'react';
import {
  Upload, FileUp, Loader2, AlertTriangle, CheckCircle2, X, Eye, Trash2, Users, BookOpen, School,
  MapPin, Link2Off, Plus, PackagePlus, FolderSearch,
} from 'lucide-react';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { ImportService, ResourceService } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import type {
  FolderInspectionReportDto, ImportCreationReportDto, ImportCutKind, ImportFooterRowDto,
  ImportMatchSectionDto, ImportMatchStatus, ImportReportDto, LocationDto, SheetInspectionDto,
} from '../../../types/api';
import { cn } from '../../../utils/cn';

/**
 * Импорт расписания из сторонней программы — **пробный разбор**.
 *
 * <p><b>Экран намеренно ничего не записывает.</b> Он отвечает на единственный вопрос: что программа
 * поняла из файла. Первый прогон импорта сущностей не создаёт по решению И-10 — завести
 * преподавателя легко, а убрать (когда на него сошлётся назначение) уже нет, и дубль преподавателя
 * разрежет расписание надвое тише и вреднее, чем кривые занятия. Поэтому здесь нет ни кнопки
 * «Импортировать», ни выбора периода: писать пока нечем и некуда.</p>
 *
 * <p><b>Замечания разбора — главное на экране, а не сноска.</b> Разбор тотален: он не падает на
 * кривизне, а откладывает её в список. Значит ценность отчёта ровно в том, чтобы этот список был
 * виден раньше цифр — по нему и видно, что разбор поехал.</p>
 *
 * <p>Разрез (группы / преподавателя / аудитории) определяет <b>бэк по шапке файла</b>, а не фронт по
 * имени: имена в выгрузке («911», «ГоряиновР.И.») ничего не гарантируют.</p>
 */
export const ImportManager: React.FC = () => {
  const [files, setFiles] = useState<File[]>([]);
  const [report, setReport] = useState<ImportReportDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [locations, setLocations] = useState<LocationDto[]>([]);
  const [locationId, setLocationId] = useState<number | null>(null);
  const [creation, setCreation] = useState<ImportCreationReportDto | null>(null);
  const [creating, setCreating] = useState(false);
  const [groupSize, setGroupSize] = useState(25);
  const [roomCapacity, setRoomCapacity] = useState(30);
  const [folder, setFolder] = useState('');
  const [folderReport, setFolderReport] = useState<FolderInspectionReportDto | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Локация нужна только аудиториям: корпус «3» законно существует в нескольких кампусах, а в
  // файле локации нет вовсе. Если она в базе одна — выбирать не из чего, подставляем её.
  useEffect(() => {
    ResourceService.getLocations()
      .then((list) => {
        setLocations(list);
        if (list.length === 1) setLocationId(list[0].id);
      })
      .catch((e) => console.error('Ошибка загрузки локаций:', e));
  }, []);

  const addFiles = (incoming: FileList | null) => {
    if (!incoming || incoming.length === 0) return;
    // Одноимённый файл повторно не добавляем: пачка собирается в несколько заходов,
    // а два одинаковых разбора в отчёте — только шум.
    setFiles((current) => {
      const known = new Set(current.map((f) => f.name));
      return [...current, ...Array.from(incoming).filter((f) => !known.has(f.name))];
    });
  };

  const removeFile = (name: string) => setFiles((current) => current.filter((f) => f.name !== name));

  const inspect = async () => {
    setLoading(true);
    setError(null);
    try {
      setReport(await ImportService.inspect(files, locationId));
    } catch (e) {
      console.error('Ошибка разбора файлов выгрузки:', e);
      setError(serverMessage(e) ?? 'Не удалось разобрать файлы. Бэкенд ответил ошибкой — смотрите консоль и логи.');
      setReport(null);
    } finally {
      setLoading(false);
    }
  };

  const inspectFolder = async () => {
    setLoading(true);
    setError(null);
    setReport(null);
    try {
      setFolderReport(await ImportService.inspectFolder(folder.trim(), locationId));
    } catch (e) {
      console.error('Ошибка разбора каталога:', e);
      setError(serverMessage(e) ?? 'Не удалось разобрать каталог — смотрите логи бэкенда.');
      setFolderReport(null);
    } finally {
      setLoading(false);
    }
  };

  /**
   * Завести недостающее и сразу пересчитать сверку: после заведения строки обязаны стать
   * сопоставленными, и это единственная честная проверка, что заведено то самое.
   */
  const createMissing = async () => {
    setCreating(true);
    setError(null);
    const settings = { locationId, groupSize, roomCapacity };
    try {
      // Откуда приехали файлы — оттуда же и заводим, и оттуда же пересчитываем сверку.
      if (folderReport) {
        setCreation(await ImportService.createMissingFromFolder(folder.trim(), settings));
        setFolderReport(await ImportService.inspectFolder(folder.trim(), locationId));
      } else {
        setCreation(await ImportService.createMissing(files, settings));
        setReport(await ImportService.inspect(files, locationId));
      }
    } catch (e) {
      console.error('Ошибка заведения справочников:', e);
      setError(serverMessage(e) ?? 'Не удалось завести справочники. Часть могла успеть создаться — смотрите логи.');
    } finally {
      setCreating(false);
    }
  };

  // Сверка одна и та же, откуда бы ни приехали файлы — из загрузки или с диска.
  const matching = report?.matching ?? folderReport?.matching ?? null;
  const missingCount = matching?.sections
    .flatMap((section) => section.rows)
    .filter((row) => row.status === 'MISSING').length ?? 0;

  const results = report?.files ?? null;
  const totalProblems = results?.reduce((sum, r) => sum + r.problems.length, 0) ?? 0;
  const totalMegabytes = files.reduce((sum, file) => sum + file.size, 0) / (1024 * 1024);

  return (
    <div className="space-y-4">
      <Card
        title="Импорт расписания"
        subtitle="Пробный разбор файлов сторонней программы и сверка со справочниками. В базу не пишется ничего."
      >
        <div
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => { e.preventDefault(); setDragging(false); addFiles(e.dataTransfer.files); }}
          onClick={() => inputRef.current?.click()}
          className={cn(
            'border-2 border-dashed rounded-2xl px-6 py-10 text-center cursor-pointer transition-colors',
            dragging ? 'border-blue-400 bg-blue-50/60' : 'border-slate-200 hover:border-slate-300 bg-slate-50/50',
          )}
        >
          <div className="w-12 h-12 mx-auto mb-3 rounded-2xl bg-white border border-slate-200 flex items-center justify-center">
            <Upload size={20} className="text-slate-400" />
          </div>
          <p className="font-bold text-slate-700 text-sm">Перетащите файлы выгрузки или нажмите, чтобы выбрать</p>
          <p className="text-xs text-slate-400 mt-1">
            HTML любого разреза — групп, преподавателей, аудиторий. Разрез определяется по шапке файла, а не по имени.
          </p>
          <input
            ref={inputRef}
            type="file"
            multiple
            accept=".html,.htm"
            className="hidden"
            onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }}
          />
        </div>

        {files.length > 0 && (
          <div className="mt-4">
            <p className={cn('text-[11px] font-bold mb-1.5',
              totalMegabytes > 250 ? 'text-red-600' : 'text-slate-400')}>
              Выбрано файлов: {files.length} · {totalMegabytes.toFixed(1)} МБ
              {totalMegabytes > 250 && ' — близко к пределу запроса (300 МБ), загружайте частями'}
            </p>
            <div className="flex flex-wrap gap-1.5">
              {files.map((file) => (
                <span
                  key={file.name}
                  className="inline-flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-medium"
                >
                  {file.name}
                  <span className="text-slate-400">{Math.round(file.size / 1024)} КБ</span>
                  <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); removeFile(file.name); }}
                    className="p-0.5 rounded hover:bg-slate-200 text-slate-400 hover:text-slate-700"
                    title="Убрать файл"
                  >
                    <X size={12} />
                  </button>
                </span>
              ))}
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-2">
              {locations.length > 1 && (
                <label className="inline-flex items-center gap-1.5 text-xs text-slate-500">
                  <MapPin size={14} className="text-slate-400" />
                  Локация
                  <select
                    value={locationId ?? ''}
                    onChange={(e) => setLocationId(e.target.value === '' ? null : Number(e.target.value))}
                    className="px-2 py-1.5 rounded-lg border border-slate-200 text-xs font-medium text-slate-700 bg-white"
                    title="В файле локации нет: корпус «3» может быть в нескольких кампусах"
                  >
                    <option value="">не выбрана</option>
                    {locations.map((location) => (
                      <option key={location.id} value={location.id}>{location.name}</option>
                    ))}
                  </select>
                </label>
              )}
              <button
                type="button"
                onClick={() => void inspect()}
                disabled={loading}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm font-bold transition-colors"
              >
                {loading ? <Loader2 size={16} className="animate-spin" /> : <Eye size={16} />}
                {loading ? 'Разбираем…' : `Разобрать (${files.length})`}
              </button>
              <button
                type="button"
                onClick={() => { setFiles([]); setReport(null); setCreation(null); setError(null); }}
                className="inline-flex items-center gap-2 px-3 py-2 rounded-xl text-slate-500 hover:bg-slate-100 text-sm font-medium transition-colors"
              >
                <Trash2 size={15} />
                Очистить
              </button>
            </div>
          </div>
        )}

        {/* Второй путь — для полного объёма. В живой выгрузке 1792 файла на 390 МБ: через
            браузер это упирается в лимит запроса, а бэкенд лежит на той же машине, что и файлы. */}
        <div className="mt-5 pt-4 border-t border-slate-100">
          <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">
            Или разобрать каталог на диске — для полного комплекта
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <FolderSearch size={16} className="text-slate-400" />
            <input
              value={folder}
              onChange={(e) => setFolder(e.target.value)}
              placeholder="C:\...\Расписание\осень"
              className="flex-1 min-w-[240px] px-3 py-2 rounded-xl border border-slate-200 text-xs text-slate-700"
            />
            <button
              type="button"
              onClick={() => void inspectFolder()}
              disabled={loading || folder.trim() === ''}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-900 disabled:opacity-40 text-white text-sm font-bold transition-colors"
            >
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Eye size={16} />}
              Разобрать каталог
            </button>
          </div>
          <p className="text-[11px] text-slate-400 mt-1.5">
            Файлы не загружаются — читаются на месте. Каталог должен быть внутри разрешённого корня
            (<code>import.source-root</code>), иначе бэкенд откажет. Обход рекурсивный: папки подразделений тоже.
          </p>
        </div>

        {error && (
          <div className="mt-4 flex items-start gap-2 p-3 rounded-xl bg-red-50 border border-red-100 text-red-700 text-sm">
            <AlertTriangle size={16} className="shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}
      </Card>

      {folderReport && <FolderReportCard report={folderReport} />}

      {results && results.length > 0 && (
        <div className="flex items-center gap-2 text-xs text-slate-500 px-1">
          <span className="font-bold text-slate-700">Разобрано файлов: {results.length}</span>
          {totalProblems > 0 ? (
            <span className="inline-flex items-center gap-1 text-amber-600 font-bold">
              <AlertTriangle size={13} /> замечаний: {totalProblems}
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 text-emerald-600 font-bold">
              <CheckCircle2 size={13} /> замечаний нет
            </span>
          )}
        </div>
      )}

      {missingCount > 0 && (files.length > 0 || folderReport != null) && (
        <Card bodyClassName="p-5">
          <div className="flex flex-wrap items-center gap-3">
            <div>
              <p className="font-black text-slate-900 text-sm">Завести недостающее — {missingCount}</p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                Только справочники: преподаватели, группы, дисциплины, аудитории. Плана и расписания не касается.
                Подразделения не заводятся — им нужен вид и родитель, это решение человека.
              </p>
            </div>

            <label className="ml-auto inline-flex items-center gap-1.5 text-xs text-slate-500">
              Размер группы
              <input
                type="number" min={1} value={groupSize}
                onChange={(e) => setGroupSize(Number(e.target.value))}
                className="w-16 px-2 py-1.5 rounded-lg border border-slate-200 text-xs text-slate-700"
              />
            </label>
            <label className="inline-flex items-center gap-1.5 text-xs text-slate-500">
              Вместимость запасная
              <input
                type="number" min={1} value={roomCapacity}
                onChange={(e) => setRoomCapacity(Number(e.target.value))}
                className="w-16 px-2 py-1.5 rounded-lg border border-slate-200 text-xs text-slate-700"
                title="Берётся только для комнат, про которые в файлах нет ни одного занятия"
              />
            </label>

            <button
              type="button"
              onClick={() => void createMissing()}
              disabled={creating}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-sm font-bold transition-colors"
            >
              {creating ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
              {creating ? 'Заводим…' : 'Завести'}
            </button>
          </div>

          <p className="mt-3 text-[11px] text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
            Размера группы в выгрузке нет — берётся число выше, потом уточняется вручную.
            А <b>вместимость аудитории не выдумывается</b>: она считается по факту — сколько групп
            одновременно сидит в комнате по расписанию, столько размеров группы она и вмещает. Поточная
            выйдет на четыре группы, лаборатория на одну. Запасное число берётся только для комнат,
            про которые занятий в файлах нет вовсе.
          </p>
        </Card>
      )}

      {creation?.sections.map((section) => (
        <CreationSectionCard key={section.title} section={section} />
      ))}

      {matching?.sections.map((section) => (
        <MatchSectionCard key={section.title} section={section} />
      ))}

      {results?.map((sheet) => <InspectionCard key={sheet.file} sheet={sheet} />)}
    </div>
  );
};

// ============================================================================
// Сверка со справочниками
// ============================================================================

/**
 * Раздел сверки: что из файла нашлось у нас, а что нет.
 *
 * <p>Несопоставленные строки бэк отдаёт первыми — это то, что требует действия. Кнопки «завести»
 * здесь нет намеренно: первый прогон импорта не создаёт сущностей (решение И-10), потому что
 * завести преподавателя легко, а убрать — уже нет.</p>
 */
/**
 * Четыре судьбы значения. Разведены намеренно: заводить можно только «нет в базе», а
 * «неоднозначно» — это незнание, и новая строка сделала бы его вечным.
 */
const STATUS: Record<ImportMatchStatus, { label: string; textClass: string; rowClass: string }> = {
  MATCHED: { label: 'есть', textClass: 'text-slate-700', rowClass: '' },
  MISSING: { label: 'нет', textClass: 'text-amber-700', rowClass: 'bg-amber-50/40' },
  AMBIGUOUS: { label: 'неоднозначно', textClass: 'text-red-700', rowClass: 'bg-red-50/40' },
  UNREADABLE: { label: 'не прочитано', textClass: 'text-slate-400', rowClass: 'bg-slate-50/60' },
};

const MatchSectionCard: React.FC<{ section: ImportMatchSectionDto }> = ({ section }) => {
  const missing = section.total - section.matched;
  const complete = missing === 0 && section.total > 0;

  return (
    <Card bodyClassName="p-5 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        {complete
          ? <CheckCircle2 size={16} className="text-emerald-500" />
          : <Link2Off size={16} className="text-amber-500" />}
        <span className="font-black text-slate-900 text-sm">{section.title}</span>
        <Badge variant={complete ? 'emerald' : 'amber'}>
          {section.matched} из {section.total}
        </Badge>
        {missing > 0 && <Badge variant="red">не найдено: {missing}</Badge>}
        <span className="ml-auto text-[11px] text-slate-400">{section.hint}</span>
      </div>

      {section.total === 0 ? (
        <p className="text-xs text-slate-300">в файлах не встретилось</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-100">
          <table className="w-full text-xs">
            <thead className="bg-slate-50 text-slate-500">
              <tr className="text-left">
                <th className="px-3 py-2 font-bold">В файле</th>
                <th className="px-3 py-2 font-bold">Что понял разбор</th>
                <th className="px-3 py-2 font-bold">У нас</th>
                <th className="px-3 py-2 font-bold">Замечание</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {section.rows.map((row) => (
                <tr
                  key={row.source}
                  className={cn('hover:bg-slate-50/50', STATUS[row.status].rowClass)}
                >
                  <td className="px-3 py-2 font-bold text-slate-800 whitespace-nowrap">{row.source}</td>
                  <td className="px-3 py-2 text-slate-500">{row.detail ?? '—'}</td>
                  <td className="px-3 py-2">
                    {row.status === 'MATCHED'
                      ? <span className="text-slate-700">{row.matchedName}</span>
                      : <span className={cn('font-bold', STATUS[row.status].textClass)}>
                          {STATUS[row.status].label}
                        </span>}
                  </td>
                  <td className="px-3 py-2 text-slate-500">{row.note ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
};

// ============================================================================
// Сводка по одному файлу
// ============================================================================

const CUTS: Record<ImportCutKind, { label: string; variant: 'emerald' | 'blue' | 'purple' | 'red' }> = {
  GROUP: { label: 'разрез групп', variant: 'emerald' },
  EDUCATOR: { label: 'разрез преподавателей', variant: 'blue' },
  AUDITORIUM: { label: 'разрез аудиторий', variant: 'purple' },
  UNKNOWN: { label: 'разрез не опознан', variant: 'red' },
};

const InspectionCard: React.FC<{ sheet: SheetInspectionDto }> = ({ sheet }) => {
  const cut = CUTS[sheet.cut];

  return (
    <Card bodyClassName="p-5 space-y-4">
      {/* Шапка: чей файл и что в нём нашлось */}
      <div className="flex flex-wrap items-center gap-2">
        <FileUp size={16} className="text-slate-400" />
        <span className="font-black text-slate-900 text-sm">{sheet.owner ?? sheet.file}</span>
        <Badge variant={cut.variant}>{cut.label}</Badge>
        {sheet.faculty && <Badge variant="slate">факультет {sheet.faculty}</Badge>}
        {sheet.department && <Badge variant="slate">{sheet.department}</Badge>}
        {sheet.studyYear != null && (
          <Badge variant="slate">{sheet.studyYear}/{sheet.studyYear + 1} {sheet.semester ?? ''}</Badge>
        )}
        <span className="ml-auto text-[11px] text-slate-400">{sheet.file}</span>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <Metric label="Занятий" value={sheet.lessons} />
        <Metric label="Маркеров" value={sheet.markers} />
        <Metric label="Дисциплин" value={sheet.disciplines.length} />
        <Metric label="Период" value={formatRange(sheet.firstDate, sheet.lastDate)} small />
      </div>

      {/* Замечания — выше цифр по важности: разбор не падает, он откладывает кривизну сюда. */}
      {sheet.problems.length > 0 ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-3">
          <p className="flex items-center gap-1.5 text-xs font-black text-amber-800 uppercase tracking-wider mb-2">
            <AlertTriangle size={13} /> Замечания разбора — {sheet.problems.length}
          </p>
          <ul className="space-y-1">
            {sheet.problems.map((problem, i) => (
              <li key={i} className="text-xs text-amber-900 leading-relaxed pl-3 -indent-3">— {problem}</li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="flex items-center gap-1.5 text-xs text-emerald-600 font-bold">
          <CheckCircle2 size={13} /> Замечаний нет — файл прочитан целиком
        </p>
      )}

      <div className="grid md:grid-cols-3 gap-3">
        <Chips icon={BookOpen} title="Дисциплины" values={sheet.disciplines} />
        <Chips icon={Users} title="Группы" values={sheet.groups} />
        <Chips icon={School} title="Аудитории" values={sheet.rooms} />
      </div>

      {sheet.markerCodes.length > 0 && (
        <Chips title="Маркеры занятости (вместо занятия)" values={sheet.markerCodes} tone="amber" />
      )}

      {sheet.footer.length > 0 && <FooterTable rows={sheet.footer} />}
      {sheet.sample.length > 0 && <SampleTable sheet={sheet} />}
    </Card>
  );
};

/**
 * Итог разбора каталога: цифры по пачке и самые частые замечания.
 *
 * <p>Карточек на файл здесь нет намеренно — в живой выгрузке их было бы 1792. Замечания схлопнуты
 * по тексту: одинаковая кривизна в трёхстах файлах это одна находка с числом, а не триста строк,
 * прячущих все остальные.</p>
 */
const FolderReportCard: React.FC<{ report: FolderInspectionReportDto }> = ({ report }) => (
  <Card bodyClassName="p-5 space-y-4">
    <div className="flex flex-wrap items-center gap-2">
      <FolderSearch size={16} className="text-slate-400" />
      <span className="font-black text-slate-900 text-sm">Каталог разобран</span>
      <span className="text-[11px] text-slate-400 font-mono">{report.path}</span>
    </div>

    <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
      <Metric label="Файлов" value={report.files} />
      <Metric label="Разобрано" value={report.parsed} />
      <Metric label="Занятий" value={report.lessons} />
      <Metric label="Маркеров" value={report.markers} />
      <Metric label="Период" value={formatRange(report.firstDate, report.lastDate)} small />
    </div>

    <div>
      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Разрезы</p>
      <div className="flex flex-wrap gap-1.5">
        {Object.entries(report.byCut).map(([cut, count]) => (
          <Badge key={cut} variant={CUTS[cut as ImportCutKind].variant}>
            {CUTS[cut as ImportCutKind].label}: {count}
          </Badge>
        ))}
        {report.failed > 0 && <Badge variant="red">не прочитано: {report.failed}</Badge>}
      </div>
    </div>

    {report.problems.length > 0 && (
      <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-3">
        <p className="flex items-center gap-1.5 text-xs font-black text-amber-800 uppercase tracking-wider mb-2">
          <AlertTriangle size={13} /> Замечания — {report.problemsTotal}, разных {report.problems.length}
        </p>
        <ul className="space-y-1 max-h-64 overflow-y-auto">
          {report.problems.map((problem) => (
            <li key={problem.message} className="text-xs text-amber-900 leading-relaxed pl-3 -indent-3">
              — {problem.message}
              {problem.count > 1 && <span className="font-bold"> × {problem.count}</span>}
              {problem.example && (
                <span className="text-amber-700/70"> · например: {problem.example}</span>
              )}
            </li>
          ))}
        </ul>
      </div>
    )}
  </Card>
);

/** Итог заведения: что создано и что пропущено — пропуски здесь не менее важны. */
const CreationSectionCard: React.FC<{ section: ImportCreationReportDto['sections'][number] }> = ({ section }) => (
  <Card bodyClassName="p-5 space-y-3">
    <div className="flex flex-wrap items-center gap-2">
      <PackagePlus size={16} className="text-emerald-500" />
      <span className="font-black text-slate-900 text-sm">{section.title}</span>
      <Badge variant="emerald">заведено: {section.created}</Badge>
      {section.skipped > 0 && <Badge variant="amber">пропущено: {section.skipped}</Badge>}
    </div>

    <div className="overflow-x-auto rounded-xl border border-slate-100">
      <table className="w-full text-xs">
        <thead className="bg-slate-50 text-slate-500">
          <tr className="text-left">
            <th className="px-3 py-2 font-bold">В файле</th>
            <th className="px-3 py-2 font-bold">Заведено как</th>
            <th className="px-3 py-2 font-bold">Что уточнить</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-50">
          {section.rows.map((row) => (
            <tr key={row.source} className={cn('hover:bg-slate-50/50', row.id == null && 'bg-amber-50/40')}>
              <td className="px-3 py-2 font-bold text-slate-800 whitespace-nowrap">{row.source}</td>
              <td className="px-3 py-2">
                {row.id == null
                  ? <span className="text-amber-700 font-bold">пропущено</span>
                  : <span className="text-slate-700">{row.name}</span>}
              </td>
              <td className="px-3 py-2 text-slate-500">{row.note ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  </Card>
);

const Metric: React.FC<{ label: string; value: React.ReactNode; small?: boolean }> = ({ label, value, small }) => (
  <div className="rounded-xl bg-slate-50 border border-slate-100 px-3 py-2">
    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">{label}</p>
    <p className={cn('font-black text-slate-800', small ? 'text-xs mt-0.5' : 'text-lg leading-tight')}>{value}</p>
  </div>
);

const Chips: React.FC<{
  title: string;
  values: string[];
  icon?: React.ElementType;
  tone?: 'slate' | 'amber';
}> = ({ title, values, icon: Icon, tone = 'slate' }) => (
  <div>
    <p className="flex items-center gap-1.5 text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
      {Icon && <Icon size={12} />} {title} — {values.length}
    </p>
    <div className="flex flex-wrap gap-1">
      {values.length === 0 && <span className="text-xs text-slate-300">пусто</span>}
      {values.map((value) => (
        <span
          key={value}
          className={cn(
            'px-2 py-0.5 rounded text-[11px] font-medium border',
            tone === 'amber'
              ? 'bg-amber-50 text-amber-700 border-amber-100'
              : 'bg-slate-50 text-slate-600 border-slate-100',
          )}
        >
          {value}
        </span>
      ))}
    </div>
  </div>
);

/**
 * Подвал группового файла: дисциплина → лектор(ы) и преподаватели практик.
 *
 * Ради этой таблицы разбор подвала и написан: групповая ячейка преподавателя не несёт, а без него
 * занятие не разрешается в назначение. Двое практиков у одной дисциплины подсвечены — подвал их не
 * разводит по занятиям, и такие строки придётся добирать преподавательским разрезом.
 */
const FooterTable: React.FC<{ rows: ImportFooterRowDto[] }> = ({ rows }) => (
  <div>
    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
      Подвал файла: дисциплины и преподаватели — {rows.length}
    </p>
    <div className="overflow-x-auto rounded-xl border border-slate-100">
      <table className="w-full text-xs">
        <thead className="bg-slate-50 text-slate-500">
          <tr className="text-left">
            <th className="px-3 py-2 font-bold">Обозн</th>
            <th className="px-3 py-2 font-bold">Дисциплина</th>
            <th className="px-3 py-2 font-bold">Каф.</th>
            <th className="px-3 py-2 font-bold">Лектор</th>
            <th className="px-3 py-2 font-bold">Другие виды занятий</th>
            <th className="px-3 py-2 font-bold">Часы</th>
            <th className="px-3 py-2 font-bold">Отчёт</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-50">
          {rows.map((row) => (
            <tr key={row.code} className="hover:bg-slate-50/50">
              <td className="px-3 py-2 font-bold text-slate-800 whitespace-nowrap">{row.code}</td>
              <td className="px-3 py-2 text-slate-600">{row.name}</td>
              <td className="px-3 py-2 text-slate-500">{row.department}</td>
              <td className="px-3 py-2 text-slate-600">{row.lecturers.join('; ') || '—'}</td>
              <td className="px-3 py-2 text-slate-600">
                {row.practicians.join('; ') || '—'}
                {row.practicians.length > 1 && (
                  <Badge variant="amber" className="ml-1.5">двое</Badge>
                )}
              </td>
              <td className="px-3 py-2 text-slate-500 whitespace-nowrap">{row.hours || '—'}</td>
              <td className="px-3 py-2 text-slate-500">{row.report || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  </div>
);

/** Первые занятия «на глаз» — сверить с файлом, что дата и пара встали на свои места. */
const SampleTable: React.FC<{ sheet: SheetInspectionDto }> = ({ sheet }) => {
  const { getSlotShort } = useEnums();

  return (
    <div>
      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
        Первые занятия — сверить глазом с файлом
      </p>
      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className="w-full text-xs">
          <thead className="bg-slate-50 text-slate-500">
            <tr className="text-left">
              <th className="px-3 py-2 font-bold">Дата</th>
              <th className="px-3 py-2 font-bold">Пара</th>
              <th className="px-3 py-2 font-bold">Вид</th>
              <th className="px-3 py-2 font-bold">Тема</th>
              <th className="px-3 py-2 font-bold">Дисциплина</th>
              <th className="px-3 py-2 font-bold">Группа</th>
              <th className="px-3 py-2 font-bold">Аудитория</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50">
            {sheet.sample.map((lesson, i) => (
              <tr key={i} className="hover:bg-slate-50/50">
                <td className={cn('px-3 py-2 whitespace-nowrap', lesson.date ? 'text-slate-700' : 'text-red-600 font-bold')}>
                  {lesson.date ? formatDate(lesson.date) : 'дата не восстановлена'}
                </td>
                <td className="px-3 py-2 text-slate-500">{getSlotShort(lesson.slot)}</td>
                <td className="px-3 py-2 font-bold text-slate-800">{lesson.kind ?? '—'}</td>
                <td className="px-3 py-2 text-slate-500">{lesson.theme ?? '—'}</td>
                <td className="px-3 py-2 text-slate-600">{lesson.discipline ?? '—'}</td>
                <td className="px-3 py-2 text-slate-600">{lesson.groups.join(', ') || '—'}</td>
                <td className="px-3 py-2 text-slate-500">{lesson.rooms.join(', ') || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// ============================================================================

/**
 * Текст ошибки, который прислал сервер. Своя формулировка на фронте годится только как запасная:
 * «пачка слишком велика» и «файлы не разобрались» — разные новости, и подменять первую второй
 * значит отправить человека искать дефект разбора там, где запрос до разбора не дошёл.
 */
const serverMessage = (error: unknown): string | null => {
  const data = (error as { response?: { data?: unknown } })?.response?.data;
  const message = (data as { message?: unknown })?.message;
  return typeof message === 'string' && message.trim() !== '' ? message : null;
};

const formatDate = (iso: string) => new Date(iso).toLocaleDateString('ru-RU');

const formatRange = (from: string | null, to: string | null) =>
  from && to ? `${formatDate(from)} — ${formatDate(to)}` : 'дат нет';
