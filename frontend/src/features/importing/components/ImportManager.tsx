import React, { useEffect, useRef, useState } from 'react';
import {
  Upload, FileUp, Loader2, AlertTriangle, CheckCircle2, X, Eye, Trash2, Users, BookOpen, School,
  MapPin, Link2Off, Plus, PackagePlus, FolderSearch, CalendarRange, Layers, ListTree, Network,
} from 'lucide-react';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { ImportService, ResourceService } from '../../../services/apiServices';
import { CQRSService } from '../../../services/cqrsApiService';
import type { GroupNameStyle } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import { usePeriod } from '../../period/PeriodContext';
import { useOrgUnits } from '../../orgUnit/hooks/useOrgUnits';
import type { OrgUnitNode } from '../../orgUnit/hooks/useOrgUnits';
import type {
  FolderInspectionReportDto, MergeReportDto, MergedLessonDto, PlanReportDto, ImportCreationReportDto, ImportCutKind, ImportFooterRowDto,
  ImportMatchRowDto, ImportMatchSectionDto, ImportMatchStatus, ImportReportDto, ImportedSessionDto,
  LocationDto, RollbackImpactDto, ScheduleWriteReportDto, SheetInspectionDto,
} from '../../../types/api';
import { cn } from '../../../utils/cn';

/**
 * Импорт расписания из сторонней программы — **пробный разбор**.
 *
 * <p><b>Порядок шагов на экране повторяет порядок решений, а не удобство вёрстки:</b> разобрать →
 * свести разрезы → завести недостающее в справочниках → посчитать план. Каждый следующий шаг
 * бессмысленен без предыдущего: до заведения справочников занятие не разрешается вовсе.</p>
 *
 * <p><b>Записи расписания здесь по-прежнему нет.</b> Заводятся только справочники, и то по решению
 * И-10 — после отчёта: завести преподавателя легко, а убрать (когда на него сошлётся назначение)
 * уже нет, и дубль преподавателя разрежет расписание надвое тише и вреднее, чем кривые занятия.
 * План пока только считается.</p>
 *
 * <p><b>Замечания разбора — главное на экране, а не сноска.</b> Разбор тотален: он не падает на
 * кривизне, а откладывает её в список. Значит ценность отчёта ровно в том, чтобы этот список был
 * виден раньше цифр — по нему и видно, что разбор поехал.</p>
 *
 * <p>Разрез (группы / преподавателя / аудитории) определяет <b>бэк по шапке файла</b>, а не фронт по
 * имени: имена в выгрузке («911», «ВетровР.И.») ничего не гарантируют.</p>
 */
/**
 * Состояние, переживающее перезагрузку и уход на другую страницу.
 *
 * <p><b>Зачем.</b> Прогон импорта — это не одно нажатие, а последовательность шагов, между которыми
 * человек уходит смотреть справочники и возвращается. Пока параметры жили только в React-state,
 * возврат обнулял их все, и приходилось выставлять заново — а забытая локация молча пропускает все
 * аудитории.</p>
 *
 * <p>Хранятся <b>параметры прогона и последний результат записи</b>, но не отчёты: разбор каталога
 * на 1792 файла весит мегабайты, и класть его в localStorage значило бы менять одну проблему на
 * другую. Отчёты пересчитываются кнопкой, а вот куда именно записано расписание — единственное
 * знание, которое иначе теряется безвозвратно.</p>
 *
 * <p>Период сюда НЕ входит: он общий для всех разделов и живёт в {@code PeriodContext}.</p>
 */
const SETTINGS_KEY = 'unischedule.import.settings';
const WRITE_KEY = 'unischedule.import.lastWrite';

function readStored<T>(key: string, fallback: T): T {
  try {
    const saved = localStorage.getItem(key);
    return saved ? { ...fallback, ...(JSON.parse(saved) as object) } as T : fallback;
  } catch {
    // Кривой JSON — не повод падать: параметры это удобство, а не данные.
    return fallback;
  }
}

interface StoredSettings {
  locationId: number | null;
  groupSize: number;
  roomCapacity: number;
  groupNameStyle: GroupNameStyle;
  folder: string;
  project: boolean;
}

const DEFAULT_SETTINGS: StoredSettings = {
  locationId: null, groupSize: 25, roomCapacity: 30, groupNameStyle: 'SLASH', folder: '', project: false,
};

export const ImportManager: React.FC = () => {
  const stored = React.useRef(readStored<StoredSettings>(SETTINGS_KEY, DEFAULT_SETTINGS)).current;

  const [files, setFiles] = useState<File[]>([]);
  const [report, setReport] = useState<ImportReportDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [locations, setLocations] = useState<LocationDto[]>([]);
  const [locationId, setLocationId] = useState<number | null>(stored.locationId);
  const [creation, setCreation] = useState<ImportCreationReportDto | null>(null);
  const [creating, setCreating] = useState(false);
  const [groupSize, setGroupSize] = useState(stored.groupSize);
  const [roomCapacity, setRoomCapacity] = useState(stored.roomCapacity);
  const [groupNameStyle, setGroupNameStyle] = useState<GroupNameStyle>(stored.groupNameStyle);
  // Период — из ОБЩЕГО контекста (шапка), а не свой. Пока он был свой, импорт писал расписание в
  // один период, а раздел «Расписание» показывал другой — записанное «нигде не находилось».
  const { periods, selectedPeriodId: periodId, setSelectedPeriodId: setPeriodId } = usePeriod();
  const [plan, setPlan] = useState<PlanReportDto | null>(null);
  const [planning, setPlanning] = useState(false);
  const [planCreating, setPlanCreating] = useState(false);
  /** Отчёт после заведения читается иначе: «заведено», а не «будет заведено». */
  const [planCreated, setPlanCreated] = useState(false);
  // Куда записано расписание — единственное, что нельзя пересчитать кнопкой: после перезагрузки
  // id сессии иначе теряется, а сессия остаётся в базе.
  const [write, setWrite] = useState<ScheduleWriteReportDto | null>(
    () => readStored<ScheduleWriteReportDto | null>(WRITE_KEY, null));
  const [writing, setWriting] = useState(false);
  // Проекция — по требованию, а не по умолчанию: в живом периоде две сессии смешаются в сетке
  // (schedule_view не несёт session_id), поэтому включать её можно только в экспериментальном.
  const [project, setProject] = useState(stored.project);
  const [sessions, setSessions] = useState<ImportedSessionDto[]>([]);
  const [impact, setImpact] = useState<RollbackImpactDto | null>(null);
  const [rollingBack, setRollingBack] = useState(false);
  const [projecting, setProjecting] = useState<string | null>(null);
  const [folder, setFolder] = useState(stored.folder);
  const [folderReport, setFolderReport] = useState<FolderInspectionReportDto | null>(null);
  /**
   * Кафедры, проставленные человеком вручную: раздел → значение из отчёта → id подразделения.
   *
   * Нужны там, где разбор кафедру не вывел (у преподавателя нет своего файла, номер группы не по
   * стандарту, кафедра в базе не заведена). Держим здесь, а не внутри карточки раздела: карточка
   * пересоздаётся с каждым новым отчётом, а выбор должен дожить до кнопки «Завести».
   */
  const [orgUnits, setOrgUnits] = useState<Record<string, Record<string, number>>>({});
  /**
   * Родители подразделений, выбранные человеком: имя строки → имя родителя.
   *
   * Отдельно от `orgUnits` и по имени, а не по id: спорный факультет может быть ещё не заведён —
   * его создаёт этот же прогон, — и id у него нет. Выбор из одного справочника был бы вопросом,
   * на который нечем ответить.
   */
  const [orgUnitParents, setOrgUnitParents] = useState<Record<string, string>>({});
  const { flat: unitList, loading: unitsLoading } = useOrgUnits();
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

  /**
   * Локация одна — выбираем её за человека.
   *
   * И-17 требует явную локацию потому, что корпус «3» законно существует в нескольких кампусах. Но
   * когда кампус ровно один, выбирать не из чего, а пустой параметр стоит дорого: заведение
   * пропускает ВСЕ аудитории с «локация не выбрана». Правило то же, что при разрешении комнаты:
   * один кандидат — берём молча, несколько — спрашиваем.
   */
  useEffect(() => {
    if (locations.length === 1) {
      setLocationId((current) => current ?? locations[0].id);
    }
  }, [locations]);

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
      setReport(await ImportService.inspect(files, locationId, groupNameStyle, periodId));
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
      setFolderReport(await ImportService.inspectFolder(folder.trim(), locationId, groupNameStyle, periodId));
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
  /**
   * Завести подразделения — отдельным шагом, до всего остального.
   *
   * На них ссылаются и преподаватель, и группа, и комната; незаведённая кафедра не отменяет
   * заведение зависимых, а тихо оставляет их без привязки. Поэтому дерево заводится и проверяется
   * раньше, чем появятся сотни ссылающихся строк.
   */
  const createOrgUnits = async () => {
    setCreating(true);
    setError(null);
    try {
      if (folderReport) {
        setCreation(await ImportService.createOrgUnitsFromFolder(folder.trim(), orgUnitParents));
        setFolderReport(await ImportService.inspectFolder(folder.trim(), locationId, groupNameStyle, periodId));
      } else {
        setCreation(await ImportService.createOrgUnits(files, orgUnitParents));
        setReport(await ImportService.inspect(files, locationId, groupNameStyle, periodId));
      }
    } catch (e) {
      console.error('Ошибка заведения подразделений:', e);
      setError(serverMessage(e) ?? 'Не удалось завести подразделения — смотрите логи бэкенда.');
    } finally {
      setCreating(false);
    }
  };

  const createMissing = async () => {
    setCreating(true);
    setError(null);
    // Ручные привязки живут только до заведения: после него сверка пересчитывается, строки
    // становятся сопоставленными, и выбор относился бы уже не к ним. Раздел едет ключом — какие
    // из них про преподавателей, а какие про группы, знает бэк.
    const settings = { locationId, groupSize, roomCapacity, groupNameStyle, orgUnits, orgUnitParents };
    try {
      // Откуда приехали файлы — оттуда же и заводим, и оттуда же пересчитываем сверку.
      if (folderReport) {
        setCreation(await ImportService.createMissingFromFolder(folder.trim(), settings));
        setFolderReport(await ImportService.inspectFolder(folder.trim(), locationId, groupNameStyle, periodId));
      } else {
        setCreation(await ImportService.createMissing(files, settings));
        setReport(await ImportService.inspect(files, locationId, groupNameStyle, periodId));
      }
      setOrgUnits({});
      setOrgUnitParents({});
    } catch (e) {
      console.error('Ошибка заведения справочников:', e);
      setError(serverMessage(e) ?? 'Не удалось завести справочники. Часть могла успеть создаться — смотрите логи.');
    } finally {
      setCreating(false);
    }
  };

  /**
   * Посчитать, во что превратится выгрузка в учебном плане. Не пишет ничего.
   *
   * Шаг отдельный от разбора намеренно: без заведённых справочников занятие не разрешается, и
   * запускать расчёт до «Завести» бессмысленно — весь отчёт будет состоять из «дисциплина не
   * заведена».
   */
  const previewPlan = async () => {
    if (periodId == null) return;
    setPlanning(true);
    setError(null);
    try {
      setPlan(folderReport
        ? await ImportService.planPreviewFromFolder(folder.trim(), periodId, groupNameStyle)
        : await ImportService.planPreview(files, periodId, groupNameStyle));
      setPlanCreated(false);
    } catch (e) {
      console.error('Ошибка расчёта плана:', e);
      setError(serverMessage(e) ?? 'Не удалось посчитать план — смотрите логи бэкенда.');
      setPlan(null);
    } finally {
      setPlanning(false);
    }
  };

  /**
   * Завести план по посчитанному. Отдельным нажатием, а не продолжением расчёта: заведённые курс,
   * слот и назначение окажутся под ссылкой размещения и станут неудаляемыми — правило И-10 здесь
   * строже, чем со справочниками.
   */
  const createPlan = async () => {
    if (periodId == null) return;
    setPlanCreating(true);
    setError(null);
    try {
      setPlan(folderReport
        ? await ImportService.createPlanFromFolder(folder.trim(), periodId, groupNameStyle)
        : await ImportService.createPlan(files, periodId, groupNameStyle));
      setPlanCreated(true);
    } catch (e) {
      console.error('Ошибка заведения плана:', e);
      setError(serverMessage(e) ?? 'Не удалось завести план — смотрите логи бэкенда.');
    } finally {
      setPlanCreating(false);
    }
  };

  /**
   * Записать расписание: план и размещения в НОВУЮ сессию. Последний шаг импорта.
   *
   * Живое расписание не двигается, поэтому нажатие обратимо: сессия сносится одной командой ниже.
   */
  const writeSchedule = async () => {
    if (periodId == null) return;
    setWriting(true);
    setError(null);
    try {
      const result = folderReport
        ? await ImportService.createScheduleFromFolder(folder.trim(), periodId, locationId, groupNameStyle, project)
        : await ImportService.createSchedule(files, periodId, locationId, groupNameStyle, project);
      setWrite(result);
      setPlanCreated(true);
      setPlan(result.plan);
      await refreshRollback(periodId);
    } catch (e) {
      console.error('Ошибка записи расписания:', e);
      setError(serverMessage(e) ?? 'Не удалось записать расписание — смотрите логи бэкенда.');
    } finally {
      setWriting(false);
    }
  };

  /** Что сейчас лежит в периоде: импортные сессии и цена сноса плана. */
  const refreshRollback = async (id: number) => {
    try {
      const [found, price] = await Promise.all([
        ImportService.importedSessions(id),
        ImportService.rollbackImpact(id),
      ]);
      setSessions(found);
      setImpact(price);
    } catch (e) {
      console.error('Не удалось прочитать состояние периода:', e);
    }
  };

  /** Снести сессию: расписание исчезает, план остаётся — этого хватает, чтобы переложить заново. */
  const dropSession = async (id: string, placements: number) => {
    if (!window.confirm(`Снести сессию и ${placements} размещений? Учебный план останется.`)) return;
    try {
      await CQRSService.deleteSession(id);
      if (periodId != null) await refreshRollback(periodId);
      if (write?.sessionId === id) setWrite(null);
    } catch (e) {
      console.error('Ошибка удаления сессии:', e);
      setError(serverMessage(e) ?? 'Не удалось удалить сессию.');
    }
  };

  /** Снести план периода: курсы → слоты → назначения → размещения, плюс осиротевшие потоки. */
  const dropPlan = async () => {
    if (periodId == null || impact == null) return;
    if (!window.confirm(
      `Снести учебный план периода?\n\nИсчезнут: курсов ${impact.courses}, слотов ${impact.slots}, `
      + `назначений ${impact.assignments}, занятий расписания ${impact.placements}.\n\n`
      + 'Справочники (дисциплины, преподаватели, группы, аудитории) останутся.')) return;
    setRollingBack(true);
    setError(null);
    try {
      const done = await ImportService.rollbackPlan(periodId);
      setImpact(done);
      setPlan(null);
      setPlanCreated(false);
      setWrite(null);
      await refreshRollback(periodId);
    } catch (e) {
      console.error('Ошибка отката плана:', e);
      setError(serverMessage(e) ?? 'Не удалось снести план — смотрите логи бэкенда.');
    } finally {
      setRollingBack(false);
    }
  };

  /**
   * Спроецировать записанное — «показать в сетке» уже после записи.
   *
   * <p>Отдельным действием, а не только галочкой при записи: галочку легко не поставить, и тогда
   * расписание есть, а в сетке его нет. Раньше единственным выходом было переписать прогон целиком.
   * Дверь та же, что у ремонта проекции ({@code reproject}), — третьего пути к read-модели не
   * заводим.</p>
   */
  const projectSession = async (id: string) => {
    setProjecting(id);
    setError(null);
    try {
      const rows = await CQRSService.reproject(id);
      setWrite((prev) => (prev && prev.sessionId === id ? { ...prev, projected: rows } : prev));
    } catch (e) {
      console.error('Ошибка проекции сессии:', e);
      setError(serverMessage(e) ?? 'Не удалось показать расписание в сетке.');
    } finally {
      setProjecting(null);
    }
  };

  // Параметры прогона переживают перезагрузку и уход на другую страницу: возврат к импорту не
  // должен требовать выставлять их заново, а забытая локация молча пропускает все аудитории.
  useEffect(() => {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(
      { locationId, groupSize, roomCapacity, groupNameStyle, folder, project }));
  }, [locationId, groupSize, roomCapacity, groupNameStyle, folder, project]);

  // То же для последнего результата записи — id сессии иначе теряется безвозвратно.
  useEffect(() => {
    if (write) localStorage.setItem(WRITE_KEY, JSON.stringify(write));
    else localStorage.removeItem(WRITE_KEY);
  }, [write]);

  // Состояние периода читается при его выборе: список импортных сессий должен пережить F5,
  // иначе кнопка удаления теряет объект, а сессия остаётся в базе.
  useEffect(() => {
    if (periodId == null) {
      setSessions([]);
      setImpact(null);
      return;
    }
    void refreshRollback(periodId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [periodId]);

  // Сверка и сведение одни и те же, откуда бы ни приехали файлы — из загрузки или с диска.
  const matching = report?.matching ?? folderReport?.matching ?? null;
  const merged = report?.merged ?? folderReport?.merged ?? null;
  const missingCount = matching?.sections
    .flatMap((section) => section.rows)
    .filter((row) => row.status === 'MISSING').length ?? 0;

  // Раздел-предусловие (сегодня это подразделения). Какой именно — говорит бэк признаком
  // `prerequisite`: «что от чего зависит» — знание о предметной области, а не о вёрстке.
  const prerequisite = matching?.sections.find((section) => section.prerequisite) ?? null;
  const prerequisiteMissing = prerequisite?.rows.filter((row) => row.status === 'MISSING').length ?? 0;

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

        {/* Параметры прогона — то, чего в файлах нет: период, локация, написание номера.
            Показываются ВСЕГДА, а не только при выбранных файлах: раньше локация жила внутри блока
            загрузки, и в режиме каталога её нельзя было выбрать вовсе — из-за чего аудитории молча
            не заводились («локация не выбрана — заводить некуда»). */}
        <div className="mt-4 flex flex-wrap items-center gap-2">
          {/* Показывается ВСЕГДА, в том числе когда локация одна. Пряталась она раньше как «выбирать
              не из чего» — но заведение требует явный id, и при спрятанном селекте локация
              оставалась пустой, а все аудитории молча пропускались («заводить некуда»). Одна
              локация — не повод не выбрать, это повод выбрать её за человека (см. эффект выше). */}
          <label className="inline-flex items-center gap-1.5 text-xs text-slate-500">
            <MapPin size={14} className="text-slate-400" />
            Локация
            <select
              value={locationId ?? ''}
              onChange={(e) => setLocationId(e.target.value === '' ? null : Number(e.target.value))}
              className={cn(
                'px-2 py-1.5 rounded-lg border text-xs font-medium bg-white',
                locationId == null ? 'border-red-300 text-red-700' : 'border-slate-200 text-slate-700',
              )}
              title="В файле локации нет: корпус «3» может быть в нескольких кампусах"
            >
              <option value="">не выбрана — аудитории не заведутся</option>
              {locations.map((location) => (
                <option key={location.id} value={location.id}>{location.name}</option>
              ))}
            </select>
          </label>
          {/* Тот же период, что в шапке: один селектор — одно значение, два вида на него.
              Раньше здесь был свой period-state, и импорт писал в один период, а «Расписание»
              показывало другой. */}
          <label className="inline-flex items-center gap-1.5 text-xs text-slate-500">
            <CalendarRange size={14} className="text-slate-400" />
            Период (общий с разделом «Расписание»)
            <select
              value={periodId ?? ''}
              onChange={(e) => setPeriodId(e.target.value === '' ? null : Number(e.target.value))}
              className="px-2 py-1.5 rounded-lg border border-slate-200 text-xs font-medium text-slate-700 bg-white"
              title="Тот же период, что в шапке: импорт пишет в него, и раздел «Расписание» показывает его же"
            >
              <option value="">не выбран</option>
              {periods.map((period) => (
                <option key={period.id} value={period.id}>{period.name}</option>
              ))}
            </select>
          </label>
          <label className="inline-flex items-center gap-1.5 text-xs text-slate-500">
            <Users size={14} className="text-slate-400" />
            Номер группы писать как
            <select
              value={groupNameStyle}
              onChange={(e) => setGroupNameStyle(e.target.value as GroupNameStyle)}
              className="px-2 py-1.5 rounded-lg border border-slate-200 text-xs font-medium text-slate-700 bg-white"
            >
              <option value="SLASH">101/1 — через косую черту</option>
              <option value="DASH">101-1 — через дефис</option>
            </select>
          </label>
          <span className="text-[11px] text-slate-400">
            «101/1» и «101-1» — <b>одна группа</b>: дефис приходит оттуда, где номер попал в имя файла
            («/» в именах запрещён). Выбор решает только, как назовём её мы.
          </span>
        </div>

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

      {/* Шаг 1 — подразделения. Отдельной карточкой, потому что это предусловие остальных: к ним
          крепятся и преподаватель, и группа, и комната, а незаведённая кафедра не отменяет
          заведение зависимых — она тихо оставляет их без привязки. */}
      {prerequisiteMissing > 0 && (files.length > 0 || folderReport != null) && (
        <Card bodyClassName="p-5">
          <div className="flex flex-wrap items-center gap-3">
            <div>
              <p className="font-black text-slate-900 text-sm">
                Шаг 1. Подразделения — {prerequisiteMissing}
              </p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                На них ссылаются преподаватели, группы и аудитории. Заведите дерево и посмотрите
                отчёт: спорный узел лучше разобрать до того, как на него сошлются сотни строк.
              </p>
            </div>
            <button
              type="button"
              onClick={() => void createOrgUnits()}
              disabled={creating}
              className="ml-auto inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-sm font-bold transition-colors"
            >
              {creating ? <Loader2 size={16} className="animate-spin" /> : <Network size={16} />}
              {creating ? 'Заводим…' : 'Завести подразделения'}
            </button>
          </div>
        </Card>
      )}

      {missingCount > 0 && (files.length > 0 || folderReport != null) && (
        <Card bodyClassName="p-5">
          <div className="flex flex-wrap items-center gap-3">
            <div>
              <p className="font-black text-slate-900 text-sm">
                Шаг 2. Завести остальное — {missingCount - prerequisiteMissing}
              </p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                Преподаватели, группы, потоки, дисциплины, аудитории. Плана и расписания не касается.
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
              disabled={creating || prerequisiteMissing > 0}
              title={prerequisiteMissing > 0
                ? 'Сначала шаг 1: пока подразделения не заведены, остальные заведутся без кафедры'
                : undefined}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-sm font-bold transition-colors"
            >
              {creating ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
              {creating ? 'Заводим…' : 'Завести'}
            </button>
          </div>

          {prerequisiteMissing > 0 && (
            <p className="mt-3 text-[11px] text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
              <b>Сначала шаг 1 — подразделений не заведено: {prerequisiteMissing}.</b> Пока их нет,
              преподаватели, группы и комнаты создадутся <b>без кафедры</b>: это не отказ, а тихо не
              проставленная привязка у сотен строк, которую потом искать по всему справочнику.
            </p>
          )}

          {locationId == null && (
            <p className="mt-3 text-[11px] text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
              <b>Локация не выбрана — аудитории не заведутся.</b> В файле локации нет, а корпус «3»
              законно существует в нескольких кампусах: завести комнату вслепую значит поставить
              занятия в физически другом здании. Выберите локацию в параметрах выше.
            </p>
          )}

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

      {/* Следующий шаг после «Завести»: во что выгрузка превратится в учебном плане. Кнопка
          отдельная, потому что считать план до заведения справочников бессмысленно. */}
      {merged && (
        <Card bodyClassName="p-5">
          <div className="flex flex-wrap items-center gap-3">
            <div>
              <p className="font-black text-slate-900 text-sm">Что получится в учебном плане</p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                Курс → тема → слот → назначение. Ничего не создаётся — это расчёт: заводится не одна
                строка, а четыре уровня, и каждая потом окажется под ссылкой размещения.
              </p>
            </div>
            <button
              type="button"
              onClick={() => void previewPlan()}
              disabled={planning || periodId == null}
              className="ml-auto inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-900 disabled:opacity-40 text-white text-sm font-bold transition-colors"
              title={periodId == null ? 'Выберите период: от него считается семестр курса' : undefined}
            >
              {planning ? <Loader2 size={16} className="animate-spin" /> : <ListTree size={16} />}
              {planning ? 'Считаем…' : periodId == null ? 'Выберите период' : 'Посчитать план'}
            </button>
            {/* Заведение — отдельным нажатием и только после расчёта: подтверждают то, что видели.
                Заведённые курс, слот и назначение окажутся под ссылкой размещения и станут
                неудаляемыми, поэтому «посчитать» и «завести» здесь не сливаются в одну кнопку. */}
            {plan && !planCreated && plan.resolved > 0 && (
              <button
                type="button"
                onClick={() => void createPlan()}
                disabled={planCreating || periodId == null}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 text-white text-sm font-bold transition-colors"
              >
                {planCreating ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
                {planCreating ? 'Заводим…' : 'Завести план'}
              </button>
            )}
          </div>
          {planCreated && (
            <p className="mt-3 text-[11px] text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
              <b>План заведён.</b> Числа ниже — что фактически создано; занятия из блокеров не
              заведены. Повторный запуск безопасен: заведённое считается «уже есть», дублей не будет.
            </p>
          )}
          {periodId == null && (
            <p className="mt-3 text-[11px] text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
              <b>Период не выбран</b> — семестр курса вычислить не из чего, расчёт недоступен.
              Выберите его в параметрах прогона вверху страницы.
            </p>
          )}
        </Card>
      )}

      {plan && <PlanCard report={plan} />}

      {/* Последний шаг: единственный, который трогает расписание. Отдельной карточкой, потому что
          он и обратим отдельно — сессия сносится, план переживает.

          Карточка живёт и БЕЗ загруженных файлов: результат последнего прогона восстанавливается из
          localStorage, и после перезагрузки страницы человек должен видеть, куда он записал
          расписание. Кнопка при этом прячется — записывать нечего. */}
      {(merged || write) && (
        <Card bodyClassName="p-5 space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <div>
              <p className="font-black text-slate-900 text-sm">Записать расписание</p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                План и размещения — в <b>новую</b> сессию. Живое расписание не двигается. Занятость
                и вместимость не проверяются: импорт фиксирует факт, а привезённую боль покажут
                датчики.
              </p>
            </div>
            {merged && (
              <button
                type="button"
                onClick={() => void writeSchedule()}
                disabled={writing || periodId == null}
                className="ml-auto inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-40 text-white text-sm font-bold transition-colors"
                title={periodId == null ? 'Выберите период' : undefined}
              >
                {writing ? <Loader2 size={16} className="animate-spin" /> : <CalendarRange size={16} />}
                {writing ? 'Пишем…' : periodId == null ? 'Выберите период' : 'Записать расписание'}
              </button>
            )}
          </div>

          {merged && (
            <label className="flex items-start gap-2 text-[11px] text-slate-600">
              <input
                type="checkbox"
                checked={project}
                onChange={(e) => setProject(e.target.checked)}
                className="mt-0.5"
              />
              <span>
                <b>Показать в обычной сетке</b> (спроецировать). Безопасно только в отдельном,
                экспериментальном периоде: в живом две сессии смешаются в сетке, а генерация снесёт
                строки импорта. Без галочки расписание видно доской раскладки и датчиками.
              </span>
            </label>
          )}

          {merged && locationId == null && (
            <p className="text-[11px] text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
              <b>Локация не выбрана</b> — комнаты не разрешатся, и все занятия встанут без аудиторий.
            </p>
          )}

          {write && (
            <div className="rounded-xl border border-blue-200 bg-blue-50/60 p-3 space-y-2">
              <p className="text-xs font-black text-blue-900">
                Записано в сессию «{write.sessionName}» — {write.placements} размещений
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                <Metric label="Без аудитории" value={write.withoutRoom} />
                <Metric label="Вне периода" value={write.outsidePeriod} />
                <Metric label="Не разрешено" value={write.notResolved} />
                <Metric label="Спроецировано" value={write.projected} />
              </div>

              {/* Главный вопрос после записи — «где мне это увидеть». Пока ответа не было на
                  экране, записанное расписание выглядело исчезнувшим. */}
              {write.projected > 0 ? (
                <p className="text-[11px] text-emerald-800 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
                  <b>Где смотреть:</b> раздел «Расписание» — период в шапке тот же, что здесь.
                  Занятия без аудитории помечены в сетке знаком «?».
                </p>
              ) : (
                <div className="flex flex-wrap items-center gap-2 text-[11px] text-amber-800 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                  <span>
                    <b>В сетке этого расписания пока нет</b> — оно записано, но не спроецировано.
                    Проецировать безопасно в отдельном (экспериментальном) периоде.
                  </span>
                  <button
                    type="button"
                    onClick={() => void projectSession(write.sessionId)}
                    disabled={projecting === write.sessionId}
                    className="ml-auto inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-amber-600 hover:bg-amber-700 disabled:opacity-40 text-white font-bold transition-colors"
                  >
                    {projecting === write.sessionId
                      ? <Loader2 size={13} className="animate-spin" /> : <Eye size={13} />}
                    Показать в сетке
                  </button>
                </div>
              )}

              <p className="text-[11px] text-blue-900/80">
                «Не разрешено» — занятия, не доехавшие до назначения: их нет в расписании вовсе,
                причины перечислены в блокерах плана выше. «Вне периода» не размещаются намеренно —
                у нас для этого есть родное состояние, неразмещённое занятие.
              </p>
            </div>
          )}
        </Card>
      )}

      {/* Откат — рядом с записью, а не «потом»: без него второй прогон невозможен, а первый
          заведомо кривой. */}
      {periodId != null && (sessions.length > 0 || (impact?.courses ?? 0) > 0) && (
        <Card bodyClassName="p-5 space-y-3">
          <div>
            <p className="font-black text-slate-900 text-sm">Импортные сессии периода</p>
            <p className="text-[11px] text-slate-500 mt-0.5">
              Список приходит с сервера, поэтому переживает перезагрузку: сессия опознаётся по своим
              размещениям. Откат — две разные операции. Снести <b>сессию</b> — исчезает расписание,
              план остаётся (годится, когда кривой оказалась раскладка). Снести <b>план периода</b> —
              исчезает и он, вместе со всеми размещениями периода.
            </p>
          </div>

          {sessions.length > 0 && (
            <ul className="space-y-1.5">
              {sessions.map((session) => (
                <li key={session.id} className="flex flex-wrap items-center gap-2 text-xs rounded-lg border border-slate-200 px-3 py-2">
                  <span className="font-bold text-slate-800">{session.name}</span>
                  <Badge variant="slate">{session.placements} размещений</Badge>
                  {/* Список приходит с сервера, поэтому переживает перезагрузку — и «показать в
                      сетке» работает для любого прошлого прогона, не только для текущего. */}
                  <button
                    type="button"
                    onClick={() => void projectSession(session.id)}
                    disabled={projecting === session.id}
                    className="ml-auto inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-50 disabled:opacity-40 font-bold transition-colors"
                    title="Записать read-модель этой сессии — расписание появится в разделе «Расписание»"
                  >
                    {projecting === session.id
                      ? <Loader2 size={13} className="animate-spin" /> : <Eye size={13} />}
                    Показать в сетке
                  </button>
                  <button
                    type="button"
                    onClick={() => void dropSession(session.id, session.placements)}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border border-red-200 text-red-700 hover:bg-red-50 font-bold transition-colors"
                  >
                    <Trash2 size={13} /> Снести сессию
                  </button>
                </li>
              ))}
            </ul>
          )}

          {impact && impact.courses > 0 && (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-red-200 bg-red-50/60 p-3">
              <p className="text-[11px] text-red-900">
                <b>Цена сноса плана:</b> курсов {impact.courses}, слотов {impact.slots}, назначений{' '}
                {impact.assignments}, занятий расписания {impact.placements}
                {impact.orphanStreams > 0 && <>, потоков без назначений {impact.orphanStreams}</>}.
                Справочники останутся.
              </p>
              <button
                type="button"
                onClick={() => void dropPlan()}
                disabled={rollingBack}
                className="ml-auto inline-flex items-center gap-2 px-3 py-1.5 rounded-xl bg-red-600 hover:bg-red-700 disabled:opacity-40 text-white text-xs font-bold transition-colors"
              >
                {rollingBack ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
                {rollingBack ? 'Сносим…' : 'Снести план периода'}
              </button>
            </div>
          )}
        </Card>
      )}

      {/* Сведение — выше сверки: сверка говорит «чего нет в справочниках», а оно — «хватает ли
          данных вообще». Без ответа на второй вопрос первый бессмысленен. */}
      {merged && <MergeCard report={merged} periodChosen={periodId != null} />}

      {matching?.sections.map((section) => (
        <MatchSectionCard
          key={section.title}
          section={section}
          units={unitList}
          unitsLoading={unitsLoading}
          chosenParent={orgUnitParents}
          onChooseParent={(source, name) => setOrgUnitParents((all) => {
            const next = { ...all };
            if (name == null) delete next[source];
            else next[source] = name;
            return next;
          })}
          chosen={orgUnits[section.title] ?? {}}
          onChoose={(source, unitId) => setOrgUnits((all) => {
            const forSection = { ...(all[section.title] ?? {}) };
            if (unitId == null) delete forSection[source];
            else forSection[source] = unitId;
            return { ...all, [section.title]: forSection };
          })}
        />
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

interface MatchSectionCardProps {
  section: ImportMatchSectionDto;
  /** Что человек проставил вручную: значение из отчёта → id подразделения. */
  chosen: Record<string, number>;
  onChoose: (source: string, unitId: number | null) => void;
  // Подразделения приходят сверху, а не грузятся здесь: карточек на экране шесть, и свой хук в
  // каждой означал бы шесть одинаковых запросов на один и тот же справочник.
  units: OrgUnitNode[];
  unitsLoading: boolean;
  /** Родители подразделений — по имени: спорного факультета может ещё не быть в базе. */
  chosenParent: Record<string, string>;
  onChooseParent: (source: string, name: string | null) => void;
}

const MatchSectionCard: React.FC<MatchSectionCardProps> = ({
  section, chosen, onChoose, units, unitsLoading, chosenParent, onChooseParent,
}) => {
  const missing = section.total - section.matched;
  const complete = missing === 0 && section.total > 0;
  // Показывать ли столбец «Входит в» и можно ли его править — говорит бэк, а не перечень
  // заголовков здесь: это классификация, и её место рядом со значениями (CONVENTIONS.md).
  const withOrgUnit = section.orgUnitColumn;

  // Производные строки (поток из одной группы) — по одной на каждую группу, то есть сотни. Они
  // дословно повторяют раздел «Группы» и вытесняют с экрана сводные потоки, ради которых раздел и
  // читают. Что здесь производное, решает бэк — фронт только сворачивает.
  const [showDerived, setShowDerived] = useState(false);
  const derived = section.rows.filter((row) => row.derived);
  const rows = showDerived ? section.rows : section.rows.filter((row) => !row.derived);

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
      ) : rows.length === 0 ? (
        <p className="text-xs text-slate-400">
          все {derived.length} строк(и) следуют из других разделов и решения не требуют
        </p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-100">
          <table className="w-full text-xs">
            <thead className="bg-slate-50 text-slate-500">
              <tr className="text-left">
                <th className="px-3 py-2 font-bold">В файле</th>
                <th className="px-3 py-2 font-bold">Что понял разбор</th>
                <th className="px-3 py-2 font-bold">У нас</th>
                {withOrgUnit && (
                  <th className="px-3 py-2 font-bold"
                      title="Во что входит: у преподавателя и группы — кафедра, у кафедры — факультет">
                    Входит в
                  </th>
                )}
                {section.prerequisite && (
                  <th className="px-3 py-2 font-bold" title="Что произойдёт при нажатии «Завести»">
                    При заведении
                  </th>
                )}
                <th className="px-3 py-2 font-bold">Замечание</th>
                <th className="px-3 py-2 font-bold" title="Файлы, где это встретилось">Файлы</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {rows.map((row) => (
                <tr
                  key={row.source}
                  className={cn('hover:bg-slate-50/50', STATUS[row.status].rowClass,
                    row.derived && 'text-slate-400')}
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
                  {withOrgUnit && (
                    <td className="px-3 py-2">
                      {section.prerequisite ? (
                        <ParentCell
                          row={row}
                          units={units}
                          loading={unitsLoading}
                          chosen={chosenParent[row.source] ?? null}
                          onChoose={(name) => onChooseParent(row.source, name)}
                        />
                      ) : (
                        <OrgUnitCell
                          row={row}
                          units={units}
                          loading={unitsLoading}
                          editable={section.orgUnitAssignable}
                          chosen={chosen[row.source] ?? null}
                          onChoose={(unitId) => onChoose(row.source, unitId)}
                        />
                      )}
                    </td>
                  )}
                  {section.prerequisite && (
                    <td className="px-3 py-2 whitespace-nowrap">
                      <CreationVerdict row={row} chosen={chosenParent[row.source] ?? null} />
                    </td>
                  )}
                  <td className="px-3 py-2 text-slate-500">{row.note ?? ''}</td>
                  <td className="px-3 py-2 whitespace-nowrap"><SourceFiles row={row} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {derived.length > 0 && (
        <button
          type="button"
          onClick={() => setShowDerived((shown) => !shown)}
          className="text-[11px] font-bold text-slate-400 hover:text-slate-600 transition-colors"
        >
          {showDerived
            ? `Свернуть производные строки (${derived.length})`
            : `Показать ещё ${derived.length} — они следуют из других разделов и заводятся вместе с ними`}
        </button>
      )}
    </Card>
  );
};

/**
 * Что случится со строкой при нажатии «Завести» — <b>до</b> нажатия.
 *
 * <p>Повод: спор каналов («разные факультеты в разных файлах: 7Ф, 10Ф») раньше всплывал строкой
 * «пропущено» уже <b>после</b> заведения, и ответить на вопрос было негде — оставалось заводить
 * узел руками в справочнике. Теперь и вопрос, и ответ стоят в одной строке отчёта.</p>
 *
 * <p>Правило простое и повторяет заведение: <b>нужен родитель</b>. Он есть из файла или выбран
 * человеком — заведём; спор не снят или родителя нет вовсе — пропустим.</p>
 */
const CreationVerdict: React.FC<{ row: ImportMatchRowDto; chosen: string | null }> = ({ row, chosen }) => {
  if (row.status === 'MATCHED') {
    return <span className="text-slate-400">уже есть</span>;
  }
  if (row.status === 'UNREADABLE') {
    return <span className="text-slate-400">не нужно</span>;
  }
  if (chosen != null) {
    return <span className="text-emerald-700 font-bold">заведём — родитель выбран</span>;
  }
  if (row.status === 'AMBIGUOUS') {
    return <span className="text-red-700 font-bold">пропустим — выберите родителя</span>;
  }
  return row.orgUnitHint
    ? <span className="text-emerald-700 font-bold">заведём</span>
    : <span className="text-amber-700 font-bold">пропустим — родитель неизвестен</span>;
};

/**
 * Родитель подразделения: выбор <b>по имени</b>, а не по строке справочника.
 *
 * <p>Повод — живой прогон: спор «разные факультеты в разных файлах: 7Ф, 10Ф» показывали, а выбрать
 * было не из чего — выпадающий список брал только заведённые узлы, а этих двух в базе ещё нет: их
 * создаёт этот же прогон. Вопрос был задан, ответить нечем.</p>
 *
 * <p>Поэтому в списке два источника: сначала <b>кандидаты из файлов</b> (те самые спорные), затем
 * справочник — на случай, когда прав не файл. Значение — имя: id у ещё не заведённого узла нет, а
 * заведение разрешит имя по тому же ключу, уже после создания факультетов.</p>
 */
const ParentCell: React.FC<{
  row: ImportMatchRowDto;
  units: OrgUnitNode[];
  loading: boolean;
  chosen: string | null;
  onChoose: (name: string | null) => void;
}> = ({ row, units, loading, chosen, onChoose }) => {
  if (row.status === 'MATCHED' || row.status === 'UNREADABLE') {
    return row.orgUnitHint
      ? <span className="text-slate-500">{row.orgUnitHint}</span>
      : <span className="text-slate-400">—</span>;
  }
  return (
    <select
      value={chosen ?? ''}
      onChange={(e) => onChoose(e.target.value ? e.target.value : null)}
      disabled={loading}
      className={cn(
        'px-2 py-1 border rounded-lg text-[11px] font-medium bg-white outline-none cursor-pointer',
        'focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 max-w-[200px]',
        chosen != null ? 'border-blue-300 text-blue-700 font-bold'
          : row.status === 'AMBIGUOUS' ? 'border-red-300 text-red-700'
          : row.orgUnitHint ? 'border-slate-200 text-slate-500' : 'border-amber-300 text-amber-700',
      )}
    >
      <option value="">
        {row.status === 'AMBIGUOUS'
          ? 'спор — выберите родителя'
          : row.orgUnitHint ? `как в файле: ${row.orgUnitHint}` : 'не определено — выбрать'}
      </option>
      {row.orgUnitOptions.length > 0 && (
        <optgroup label="названы файлами">
          {row.orgUnitOptions.map((name) => (
            <option key={`file-${name}`} value={name}>{name}</option>
          ))}
        </optgroup>
      )}
      <optgroup label="из справочника">
        {units.filter((unit) => unit.active).map((unit) => (
          <option key={unit.id} value={unit.name}>
            {' '.repeat(unit.depth * 4)}
            {unit.name}
          </option>
        ))}
      </optgroup>
    </select>
  );
};

/**
 * Во что входит строка: что вывел разбор — и выбор вручную там, где он не вывел ничего.
 *
 * <p>Отношение одно на три раздела: преподаватель и группа входят в кафедру, кафедра — в факультет.
 * Поэтому и столбец один. Править можно не везде: родитель подразделения берётся из шапки того же
 * файла, и ручной выбор там пока не заведён — столбец там только показывает.</p>
 *
 * <p>Ручная простановка возможна только у строк, которые <b>будут заведены</b> ({@code MISSING}):
 * у сопоставленной сущности подразделение уже своё, и менять его отсюда значило бы править
 * master-данные мимо справочника — то же правило, по которому импорт не переименовывает группы.</p>
 *
 * <p>Выбор человека сильнее вывода из файла и показывается явно: подсказка остаётся видна рядом,
 * чтобы было понятно, что именно перебивается.</p>
 */
const OrgUnitCell: React.FC<{
  row: ImportMatchRowDto;
  units: OrgUnitNode[];
  loading: boolean;
  /** Можно ли править. Сегодня — во всех разделах, где столбец есть. */
  editable: boolean;
  chosen: number | null;
  onChoose: (unitId: number | null) => void;
}> = ({ row, units, loading, editable, chosen, onChoose }) => {
  // Заводятся только несопоставленные и неоднозначные: у сопоставленной строки родитель уже свой,
  // и менять его отсюда значило бы править master-данные мимо справочника.
  const willBeCreated = row.status === 'MISSING' || row.status === 'AMBIGUOUS';
  if (!willBeCreated || !editable) {
    return row.orgUnitHint
      ? <span className="text-slate-500">{row.orgUnitHint}</span>
      : <span className="text-slate-400">—</span>;
  }
  return (
    <div className="space-y-1">
      <select
        value={chosen ?? ''}
        onChange={(e) => onChoose(e.target.value ? parseInt(e.target.value) : null)}
        disabled={loading}
        className={cn(
          'px-2 py-1 border rounded-lg text-[11px] font-medium bg-white outline-none cursor-pointer',
          'focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 max-w-[190px]',
          chosen != null ? 'border-blue-300 text-blue-700 font-bold'
            : row.status === 'AMBIGUOUS' ? 'border-red-300 text-red-700'
            : row.orgUnitHint ? 'border-slate-200 text-slate-500' : 'border-amber-300 text-amber-700',
        )}
      >
        <option value="">
          {row.status === 'AMBIGUOUS'
            ? 'спор — выберите родителя'
            : row.orgUnitHint ? `как в файле: ${row.orgUnitHint}` : 'не определено — выбрать'}
        </option>
        {units
          .filter((unit) => unit.active || unit.id === chosen)
          .map((unit) => (
            <option key={unit.id} value={unit.id}>
              {' '.repeat(unit.depth * 4)}
              {unit.name}
            </option>
          ))}
      </select>
      {chosen != null && row.orgUnitHint && (
        <p className="text-[10px] text-slate-400">вместо «{row.orgUnitHint}»</p>
      )}
    </div>
  );
};

/**
 * Откуда приехало значение — число файлов, а список разворачивается по требованию.
 *
 * <p>Без источника строка «в базе нет: 10073-19» непроверяема: в выгрузке полторы тысячи файлов, и
 * найти тот, где так написано, можно только перебором. Но и <b>списком в таблице это держать
 * нельзя</b>: имена длинные (с папкой подразделения), а строк в разделе сотни — колонка распирает
 * таблицу, уезжает за правый край и в итоге не видна вовсе. Поэтому в ячейке стоит счётчик, а имена
 * показываются во всплывающем списке.</p>
 *
 * <p>Всплывающий список — <b>{@code position: fixed}</b>, а не {@code absolute}: таблица обёрнута в
 * {@code overflow-x-auto}, и любой абсолютный потомок обрезался бы её краем.</p>
 */
const SourceFiles: React.FC<{ row: Pick<ImportMatchRowDto, 'source' | 'files' | 'fileCount'> }> = ({ row }) => {
  const [at, setAt] = useState<{ top: number; left: number } | null>(null);

  if (row.fileCount === 0) return <span className="text-slate-300">—</span>;

  const toggle = (e: React.MouseEvent<HTMLButtonElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    setAt(at ? null : {
      top: Math.min(rect.bottom + 4, window.innerHeight - 320),
      left: Math.min(rect.left, window.innerWidth - 360),
    });
  };

  return (
    <>
      <button
        type="button"
        onClick={toggle}
        title="Показать файлы, где это встретилось"
        className={cn(
          'inline-flex items-center gap-1 px-2 py-1 rounded-lg border text-[11px] font-bold transition-colors',
          at ? 'bg-blue-50 border-blue-200 text-blue-700' : 'bg-white border-slate-200 text-slate-500 hover:bg-slate-50',
        )}
      >
        <FileUp size={11} />
        {row.fileCount}
      </button>

      {at && (
        <>
          {/* Клик мимо закрывает — своего слоя у списка нет, и оставлять его висеть некому. */}
          <div className="fixed inset-0 z-40" onClick={() => setAt(null)} />
          <div
            style={{ top: at.top, left: at.left }}
            className="fixed z-50 w-[340px] max-h-72 overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-xl p-3"
          >
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
              «{row.source}» — файлов {row.fileCount}
              {row.fileCount > row.files.length && `, показаны первые ${row.files.length}`}
            </p>
            <ul className="space-y-0.5">
              {row.files.map((file) => (
                <li key={file} className="font-mono text-[11px] text-slate-600 break-all">{file}</li>
              ))}
            </ul>
          </div>
        </>
      )}
    </>
  );
};

// ============================================================================
// Учебный план
// ============================================================================

/**
 * Что импорт заведёт в плане и что уже есть.
 *
 * <p>Отчёт до записи — правило И-10, но здесь оно жёстче, чем со справочниками: заводится не одна
 * строка, а четыре уровня, и каждая потом окажется под ссылкой размещения, то есть станет
 * неудаляемой.</p>
 */
const PlanCard: React.FC<{ report: PlanReportDto }> = ({ report }) => {
  const { getSlotShort, getStudyShort } = useEnums();
  const unresolved = report.lessons - report.resolved;

  return (
    <Card bodyClassName="p-5 space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <ListTree size={16} className="text-blue-500" />
        <span className="font-black text-slate-900 text-sm">Учебный план по выгрузке</span>
        <Badge variant={unresolved === 0 ? 'emerald' : 'amber'}>
          разрешено {report.resolved} из {report.lessons}
        </Badge>
        {unresolved > 0 && <Badge variant="red">не разрешено: {unresolved}</Badge>}
        <span className="ml-auto text-[11px] text-slate-400">ничего не создано — это расчёт</span>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <Metric label="Курсов завести" value={report.coursesToCreate} />
        <Metric label="Тем завести" value={report.themesToCreate} />
        <Metric label="Слотов завести" value={report.slotsToCreate} />
        <Metric label="Назначений завести" value={report.assignmentsToCreate} />
      </div>

      <p className="text-[11px] text-slate-500">
        Уже есть: курсов {report.coursesExisting}, слотов {report.slotsExisting},
        назначений {report.assignmentsExisting}. Позиция слота взята из номера темы
        в {report.positionsByTheme} случаях
        {report.positionsByOrder > 0 && (
          <> и выведена хронологией в {report.positionsByOrder} — там, где темы в ячейке нет</>
        )}.
      </p>

      {report.blockers.length > 0 && (
        <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-3">
          <p className="flex items-center gap-1.5 text-xs font-black text-amber-800 uppercase tracking-wider mb-2">
            <AlertTriangle size={13} /> Почему не разрешилось — {report.blockers.length} причин(ы)
          </p>
          <ul className="space-y-1 max-h-64 overflow-y-auto">
            {report.blockers.map((blocker) => (
              <li key={blocker.message} className="text-xs text-amber-900 leading-relaxed pl-3 -indent-3">
                — {blocker.message}
                {blocker.count > 1 && <span className="font-bold"> × {blocker.count}</span>}
                {blocker.example && <span className="text-amber-700/70"> · например: {blocker.example}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {report.sample.length > 0 && (
        <div>
          <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
            Первые разрешённые занятия — во что именно они превратятся
          </p>
          <div className="overflow-x-auto rounded-xl border border-slate-100">
            <table className="w-full text-xs">
              <thead className="bg-slate-50 text-slate-500">
                <tr className="text-left">
                  <th className="px-3 py-2 font-bold">Дата</th>
                  <th className="px-3 py-2 font-bold">Пара</th>
                  <th className="px-3 py-2 font-bold">Дисциплина</th>
                  <th className="px-3 py-2 font-bold" title="Вычислен из года набора и года периода">Сем.</th>
                  <th className="px-3 py-2 font-bold">Вид</th>
                  <th className="px-3 py-2 font-bold" title="Из номера темы; иначе — хронологией">Поз.</th>
                  <th className="px-3 py-2 font-bold">Тема</th>
                  <th className="px-3 py-2 font-bold">Поток</th>
                  <th className="px-3 py-2 font-bold">Преподаватели</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {report.sample.map((row, i) => (
                  <tr key={i} className="hover:bg-slate-50/50">
                    <td className="px-3 py-2 whitespace-nowrap text-slate-700">{formatDate(row.date)}</td>
                    <td className="px-3 py-2 text-slate-500">{getSlotShort(row.slot)}</td>
                    <td className="px-3 py-2 text-slate-600">
                      {row.discipline}
                      {row.newCourse && <span className="ml-1 text-emerald-600 font-bold" title="курс будет заведён">+</span>}
                    </td>
                    <td className="px-3 py-2 text-slate-500">{row.semester}</td>
                    <td className="px-3 py-2 font-bold text-slate-800">{getStudyShort(row.kind)}</td>
                    <td className="px-3 py-2 text-slate-500">
                      {row.position}
                      {row.newSlot && <span className="ml-1 text-emerald-600 font-bold" title="слот будет заведён">+</span>}
                    </td>
                    <td className="px-3 py-2 text-slate-500">{row.theme ?? '—'}</td>
                    <td className="px-3 py-2 text-slate-600">{row.stream}</td>
                    <td className="px-3 py-2 text-slate-600">{row.educators.join('; ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Card>
  );
};

// ============================================================================
// Сведение разрезов
// ============================================================================

/**
 * Итог склейки: сколько занятий получилось и чего им не хватает для записи.
 *
 * <p>Карточка стоит выше сверки намеренно: сверка отвечает «чего нет в справочниках», а эта —
 * «хватает ли вообще данных». Каждое число тут не украшение, а препятствие следующего шага:
 * занятие без преподавателя не разрешится в назначение, без вида — не найдёт слот плана.</p>
 */
const MergeCard: React.FC<{ report: MergeReportDto; periodChosen: boolean }> = ({ report, periodChosen }) => (
  <Card bodyClassName="p-5 space-y-4">
    <div className="flex flex-wrap items-center gap-2">
      <Layers size={16} className="text-blue-500" />
      <span className="font-black text-slate-900 text-sm">Сведение разрезов</span>
      <Badge variant="blue">{report.lessons} занятий из {report.entries} ячеек</Badge>
      {report.firstDate && report.lastDate && (
        <Badge variant="slate">{formatRange(report.firstDate, report.lastDate)}</Badge>
      )}
      <span className="ml-auto text-[11px] text-slate-400">
        дата · пара · дисциплина, а внутри — по общей группе, комнате и преподавателю
      </span>
    </div>

    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
      <Metric label="Видит групповой" value={report.seenInGroupCut} />
      <Metric label="Видит аудиторный" value={report.seenInRoomCut} />
      <Metric label="Видит преподават." value={report.seenInEducatorCut} />
      <Metric label="Потеряно у группы" value={report.missedByGroupCut} />
      <Metric label="Вне периода" value={periodChosen ? report.outsidePeriod : '—'} />
      <Metric label="Без даты" value={report.undatedEntries} />
    </div>

    {/* Три числа, которые прямо мешают записи: их и надо доводить до нуля. */}
    <div className="grid sm:grid-cols-3 gap-2">
      <Gap
        label="Без преподавателя"
        value={report.withoutEducator}
        hint="не разрешатся в назначение: у дисциплины их несколько"
      />
      <Gap
        label="Без вида занятия"
        value={report.withoutKind}
        hint="не выбрать слот плана; вида нет в преподавательском разрезе"
      />
      <Gap
        label="Без темы"
        value={report.withoutTheme}
        hint="позиция слота берётся из номера темы, иначе — хронологией"
      />
    </div>

    {report.parallelSplit > 0 && (
      <p className="text-[11px] text-slate-600 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
        <b>Разведено параллельных занятий: {report.parallelSplit}.</b> Одна дисциплина в одно время
        шла несколькими занятиями — у разных групп, в разных комнатах, с разными преподавателями
        (полупотоки). Связи между ними нет ни по одной опоре, поэтому это разные занятия, а не поток:
        сложенные вместе, они требовали бы состава групп, которого не существует.
      </p>
    )}

    {report.coTaught > 0 && (
      <p className="text-[11px] text-amber-800 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
        <b>Двое ведущих на занятии: {report.coTaught}.</b> Их связывает общая комната или группа —
        значит занятие одно. Ведут ли вместе или один числится запасным, по файлу неотличимо:
        решает человек.
      </p>
    )}

    {!periodChosen && (
      <p className="text-[11px] text-slate-500">
        Период не выбран — занятия за его границами не считаются, и проверка «тот ли период» не идёт.
      </p>
    )}

    {report.findings.length > 0 && (
      <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-3">
        <p className="flex items-center gap-1.5 text-xs font-black text-amber-800 uppercase tracking-wider mb-2">
          <AlertTriangle size={13} /> Находки сведения — {report.findings.length}
        </p>
        <ul className="space-y-1 max-h-64 overflow-y-auto">
          {report.findings.map((finding) => (
            <li key={finding.message} className="text-xs text-amber-900 leading-relaxed pl-3 -indent-3">
              — {finding.message}
              {finding.count > 1 && <span className="font-bold"> × {finding.count}</span>}
              {finding.example && <span className="text-amber-700/70"> · например: {finding.example}</span>}
            </li>
          ))}
        </ul>
      </div>
    )}

    {report.sample.length > 0 && <MergedSample lessons={report.sample} />}
  </Card>
);

/** Число, которое должно стремиться к нулю: не метрика, а препятствие. */
const Gap: React.FC<{ label: string; value: number; hint: string }> = ({ label, value, hint }) => (
  <div className={cn('rounded-xl border px-3 py-2',
    value === 0 ? 'bg-emerald-50/60 border-emerald-100' : 'bg-amber-50/60 border-amber-100')}>
    <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">{label}</p>
    <p className={cn('text-lg font-black leading-tight', value === 0 ? 'text-emerald-700' : 'text-amber-800')}>
      {value}
    </p>
    <p className="text-[10px] text-slate-500 leading-snug mt-0.5">{hint}</p>
  </div>
);

/** Сведённые занятия «на глаз» — сверить с файлами, что склеилось именно то. */
const MergedSample: React.FC<{ lessons: MergedLessonDto[] }> = ({ lessons }) => {
  const { getSlotShort } = useEnums();

  return (
    <div>
      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
        Первые сведённые занятия — сверить глазом с файлами
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
              <th className="px-3 py-2 font-bold">Группы</th>
              <th className="px-3 py-2 font-bold">Аудитории</th>
              <th className="px-3 py-2 font-bold">Преподаватели</th>
              <th className="px-3 py-2 font-bold" title="Разрезы, в которых занятие видно">Разрезы</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50">
            {lessons.map((lesson, i) => (
              <tr key={i} className={cn('hover:bg-slate-50/50', lesson.outsidePeriod && 'bg-slate-50/60')}>
                <td className="px-3 py-2 whitespace-nowrap text-slate-700">
                  {formatDate(lesson.date)}
                  {lesson.outsidePeriod && <span className="text-slate-400"> · вне периода</span>}
                </td>
                <td className="px-3 py-2 text-slate-500">{getSlotShort(lesson.slot)}</td>
                <td className="px-3 py-2 font-bold text-slate-800">{lesson.kind ?? '—'}</td>
                <td className="px-3 py-2 text-slate-500">{lesson.theme ?? '—'}</td>
                <td className="px-3 py-2 text-slate-600" title={lesson.disciplineName ?? undefined}>
                  {lesson.discipline}
                </td>
                <td className="px-3 py-2 text-slate-600">{lesson.groups.join(', ') || '—'}</td>
                <td className="px-3 py-2 text-slate-500">{lesson.rooms.join(', ') || '—'}</td>
                <td className={cn('px-3 py-2',
                  lesson.educators.length === 0 ? 'text-amber-700 font-bold' : 'text-slate-600')}>
                  {lesson.educators.join('; ') || 'не определён'}
                </td>
                <td className="px-3 py-2 whitespace-nowrap">
                  {lesson.cuts.map((cut) => (
                    <span
                      key={cut}
                      className="inline-block mr-1 px-1.5 py-0.5 rounded bg-slate-100 text-[10px] font-bold text-slate-600"
                    >
                      {CUTS[cut].label.replace('разрез ', '')}
                    </span>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
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

/**
 * Итог заведения: что создано и что пропущено — пропуски здесь не менее важны.
 *
 * <p>Свёртка производных строк та же, что в сверке, и по той же причине: потоков из одной группы
 * заводится столько же, сколько групп, и они прячут всё остальное.</p>
 */
const CreationSectionCard: React.FC<{ section: ImportCreationReportDto['sections'][number] }> = ({ section }) => {
  const [showDerived, setShowDerived] = useState(false);
  const derived = section.rows.filter((row) => row.derived);
  const rows = showDerived ? section.rows : section.rows.filter((row) => !row.derived);

  return (
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
            <th className="px-3 py-2 font-bold" title="Файлы, где это встретилось">Файлы</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-50">
          {rows.map((row) => (
            <tr key={row.source} className={cn('hover:bg-slate-50/50', row.id == null && 'bg-amber-50/40',
              row.derived && 'text-slate-400')}>
              <td className="px-3 py-2 font-bold text-slate-800 whitespace-nowrap">{row.source}</td>
              <td className="px-3 py-2">
                {row.id == null
                  ? <span className="text-amber-700 font-bold">пропущено</span>
                  : <span className="text-slate-700">{row.name}</span>}
              </td>
              <td className="px-3 py-2 text-slate-500">{row.note ?? ''}</td>
              <td className="px-3 py-2 whitespace-nowrap"><SourceFiles row={row} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>

    {derived.length > 0 && (
      <button
        type="button"
        onClick={() => setShowDerived((shown) => !shown)}
        className="text-[11px] font-bold text-slate-400 hover:text-slate-600 transition-colors"
      >
        {showDerived
          ? `Свернуть производные строки (${derived.length})`
          : `Показать ещё ${derived.length} — потоки из одной группы, по одному на каждую`}
      </button>
    )}
  </Card>
  );
};

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
