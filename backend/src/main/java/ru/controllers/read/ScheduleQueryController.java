package ru.controllers.read;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.dto.PeriodReadinessDto;
import ru.dto.ScheduleResultDto;
import ru.entity.read.ScheduleView;
import ru.services.ScheduleResponseService;
import ru.services.generation.GenerationScope;
import ru.services.generation.GenerationScopeResolver;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller для CQRS Query Side.
 *
 * <p>Оптимизирован для скорости чтения расписания.</p>
 * <ul>
 *   <li>Использует индексированные запросы</li>
 *   <li>Возвращает денормализованные данные (без JOIN на клиенте)</li>
 *   <li>Подходит для read-heavy workload (95% запросов)</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/bliki/QueryResponsibilitySeparation.html">CQRS Pattern</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule/query")
@RequiredArgsConstructor
public class ScheduleQueryController {

    private final ru.repository.read.ScheduleViewRepository viewRepository;
    private final ScheduleResponseService responseService;
    // Резолвер набора генерации: даёт «всего занятий к размещению» без запуска распределения.
    private final GenerationScopeResolver scopeResolver;
    // Аналитика качества расписания преподавателей (компактность + равномерность) для дашборда.
    private final ru.services.EducatorScheduleReportService educatorScheduleReportService;

    /**
     * GET /api/schedule/query/student/{streamId}?start=X&end=Y
     *
     * <p>Расписание для студента (группы) на период.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/student/123?start=2025-01-11&end=2025-01-17</pre>
     *
     * <p>Используется когда студент открывает своё расписание на неделю/семестр.</p>
     *
     * @param streamId ID потока/подгруппы
     * @param start Начальная дата (формат: YYYY-MM-DD)
     * @param end Конечная дата (формат: YYYY-MM-DD)
     * @return Список занятий, отсортированный по дате и времени
     */
    @GetMapping("/student/{streamId}")
    public List<ScheduleView> getStudentSchedule(
            @PathVariable Integer streamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        log.info("Query: Student schedule for streamId={}, period={} to {}", streamId, start, end);
        return viewRepository.findByStudentGroup(streamId, start, end);
    }

    /**
     * GET /api/schedule/query/educator/{educatorId}?date=X
     *
     * <p>Расписание для преподавателя на конкретную дату.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/educator/456?date=2025-01-12</pre>
     *
     * <p>Используется когда преподаватель открывает расписание на завтра.</p>
     *
     * @param educatorId ID преподавателя
     * @param date Дата (формат: YYYY-MM-DD)
     * @return Список занятий на эту дату, отсортированный по времени
     */
    @GetMapping("/educator/{educatorId}")
    public List<ScheduleView> getEducatorSchedule(
            @PathVariable Integer educatorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Query: Educator schedule for educatorId={}, date={}", educatorId, date);
        return viewRepository.findByEducatorAndDate(educatorId, date);
    }

    /**
     * GET /api/schedule/query/educator/{educatorId}/period?start=X&end=Y
     *
     * <p>Расписание для преподавателя на период (неделя/месяц).</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/educator/456/period?start=2025-01-11&end=2025-01-17</pre>
     *
     * @param educatorId ID преподавателя
     * @param start Начальная дата (формат: YYYY-MM-DD)
     * @param end Конечная дата (формат: YYYY-MM-DD)
     * @return Список занятий, отсортированный по дате и времени
     */
    @GetMapping("/educator/{educatorId}/period")
    public List<ScheduleView> getEducatorSchedulePeriod(
            @PathVariable Integer educatorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        log.info("Query: Educator schedule for educatorId={}, period={} to {}", educatorId, start, end);
        return viewRepository.findByEducatorAndPeriod(educatorId, start, end);
    }

    /**
     * GET /api/schedule/query/auditorium/{auditoriumId}?date=X
     *
     * <p>Расписание в аудитории на дату.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/auditorium/789?date=2025-01-12</pre>
     *
     * <p>Используется для проверки свободности аудитории.</p>
     *
     * @param auditoriumId ID аудитории
     * @param date Дата (формат: YYYY-MM-DD)
     * @return Список занятий в аудитории, отсортированный по времени
     */
    @GetMapping("/auditorium/{auditoriumId}")
    public List<ScheduleView> getAuditoriumSchedule(
            @PathVariable Integer auditoriumId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Query: Auditorium schedule for auditoriumId={}, date={}", auditoriumId, date);
        return viewRepository.findByAuditoriumAndDate(auditoriumId, date);
    }

    /**
     * GET /api/schedule/query/reports/auditorium-utilization?start=X&end=Y
     *
     * <p>Отчёт по загруженности аудиторий.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/reports/auditorium-utilization?start=2025-01-11&end=2025-01-17</pre>
     *
     * <p>Возвращает: [{"auditoriumId": 789, "count": 15, "date": "2025-01-11"}, ...]</p>
     *
     * @param start Начальная дата (формат: YYYY-MM-DD)
     * @param end Конечная дата (формат: YYYY-MM-DD)
     * @return Список кортежей [ID аудитории, количество занятий, дата]
     */
    @GetMapping("/reports/auditorium-utilization")
    public List<Object[]> getAuditoriumUtilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        log.info("Query: Auditorium utilization report for period={} to {}", start, end);
        return viewRepository.countByAuditoriumAndDate();
    }

    /**
     * GET /api/schedule/query/reports/educator-load?start=X&end=Y
     *
     * <p>Отчёт по загруженности преподавателей.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/reports/educator-load?start=2025-01-11&end=2025-01-17</pre>
     *
     * <p>Возвращает: [{"educatorId": 456, "educatorName": "Иванов", "count": 20, "date": "2025-01-11"}, ...]</p>
     *
     * @param start Начальная дата (формат: YYYY-MM-DD)
     * @param end Конечная дата (формат: YYYY-MM-DD)
     * @return Список кортежей [ID, имя, количество занятий, дата]
     */
    @GetMapping("/reports/educator-load")
    public List<Object[]> getEducatorLoad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        log.info("Query: Educator load report for period={} to {}", start, end);
        return viewRepository.countByEducatorAndDate(start, end);
    }

    /**
     * GET /api/schedule/query/discipline/{abbr}
     *
     * <p>Все занятия для конкретной дисциплины.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/discipline/МаТе</pre>
     *
     * @param abbr Аббревиатура дисциплизы
     * @return Список занятий
     */
    @GetMapping("/discipline/{abbr}")
    public List<ScheduleView> getByDiscipline(
            @PathVariable String abbr
    ) {
        log.info("Query: Schedule for discipline={}", abbr);
        return viewRepository.findByDisciplineAbbr(abbr);
    }

    /**
     * GET /api/schedule/query/kind/{kind}
     *
     * <p>Все занятия определённого типа.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/kind/LECTURE</pre>
     *
     * @param kind Тип занятия (LECTURE, PRACTICE, LAB, etc.)
     * @return Список занятий
     */
    @GetMapping("/kind/{kind}")
    public List<ScheduleView> getByKindOfStudy(
            @PathVariable String kind
    ) {
        log.info("Query: Schedule for kind={}", kind);
        return viewRepository.findByKindOfStudy(kind);
    }

    /**
     * GET /api/schedule/query/check-auditorium?auditoriumId=X&date=Y&slot=Z
     *
     * <p>Проверить свободность аудитории.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/check-auditorium?auditoriumId=789&date=2025-01-12&slot=FIRST</pre>
     *
     * @param auditoriumId ID аудитории
     * @param date Дата (формат: YYYY-MM-DD)
     * @param slot Временной слот (FIRST, SECOND)
     * @return true если свободна, false если занята
     */
    @GetMapping("/check-auditorium")
    public boolean checkAuditoriumFree(
            @RequestParam Integer auditoriumId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String slot
    ) {
        log.info("Query: Check auditorium {} for {} at {}", auditoriumId, date, slot);
        return viewRepository.isAuditoriumFree(auditoriumId, date, slot);
    }

    /**
     * GET /api/schedule/query/all?start=X&end=Y
     *
     * <p>Получить все занятия за период.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>GET /api/schedule/query/all?start=2025-01-11&end=2025-07-31</pre>
     *
     * <p>Используется для загрузки существующего расписания на фронтенд.</p>
     *
     * @param start Начальная дата (формат: YYYY-MM-DD)
     * @param end Конечная дата (формат: YYYY-MM-DD)
     * @return ScheduleResultDto с занятиями и сеткой для отображения
     */
    @GetMapping("/all")
    public ScheduleResultDto getAllSchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        log.info("Query: All schedule for period={} to {}", start, end);

        // 1. Получаем все занятия за период из Query Side
        List<ScheduleView> views = viewRepository.findByPeriod(start, end);

        // 2. Строим сетку из views
        Map<String, List<ru.dto.ScheduledLessonDto>> grid = responseService.buildGridFromViews(views);

        // 3. Плоский список всех занятий
        List<ru.dto.ScheduledLessonDto> allLessons = grid.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // 4. Формируем ответ
        return new ScheduleResultDto(
                "loaded",
                allLessons,
                grid,
                allLessons.size(),
                0,
                start.toString(),
                end.toString(),
                grid.size(),
                allLessons.size()
        );
    }

    /**
     * GET /api/schedule/query/readiness?periodId=X
     *
     * <p>Готовность расписания периода: сколько занятий <b>всего</b> надо разместить,
     * сколько уже размещено и сколько не размещено. «Всего» query-сторона сама не знает
     * (в {@code /all} unplacedCount захардкожен в 0), поэтому берём его из того же набора,
     * что идёт в генерацию — {@link GenerationScope#lessons()} — но БЕЗ запуска
     * распределения. Резолвер навигирует ленивые ассоциации (слоты/назначения), поэтому
     * метод read-only транзакционный.</p>
     *
     * @param periodId учебный период
     * @return total / placed / unplaced (при периоде без курсов — нули)
     */
    @GetMapping("/readiness")
    @Transactional(readOnly = true)
    public PeriodReadinessDto getPeriodReadiness(@RequestParam Integer periodId) {
        try {
            GenerationScope scope = scopeResolver.resolve(periodId, null);
            int total = scope.lessons().size();

            // Размещено = уникальных placement в проекции за даты периода
            // (одно занятие = один placement, даже если обслуживает несколько групп).
            long placed = viewRepository
                    .findByPeriod(scope.period().getStartDate(), scope.period().getEndDate())
                    .stream()
                    .map(ScheduleView::getPlacementId)
                    .distinct()
                    .count();

            int unplaced = Math.max(0, total - (int) placed);
            return new PeriodReadinessDto(total, (int) placed, unplaced);
        } catch (IllegalStateException e) {
            // Период без курсов — размещать нечего.
            log.info("Readiness: период id={} без курсов, нули", periodId);
            return new PeriodReadinessDto(0, 0, 0);
        }
    }

    /**
     * GET /api/schedule/query/reports/educator-quality?periodId=X
     *
     * <p>Качество расписания преподавателей за период: компактность (окна/одиночные/лишние
     * дни → штраф) + равномерность (субботние пары и отклонение). Считается из
     * {@code schedule_view} (без запуска солвера). Сводные метрики компактности — по
     * преподавателям с флагом {@code compact_schedule}; список включает всех ведущих
     * (компактные первыми, затем по убыванию штрафа).</p>
     *
     * @param periodId учебный период
     * @return сводка + детализация по преподавателям
     */
    @GetMapping("/reports/educator-quality")
    public ru.dto.PeriodScheduleQualityDto getEducatorQuality(@RequestParam Integer periodId) {
        log.info("Query: Educator schedule quality report for periodId={}", periodId);
        return educatorScheduleReportService.compute(periodId);
    }
}
