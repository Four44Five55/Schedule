package ru.services.importing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.StudyPeriod;
import ru.repository.StudyPeriodRepository;
import ru.services.importing.GroupNumberDecoder.SuffixStyle;
import ru.services.importing.ScheduleMerger.PeriodBounds;

import java.util.List;
import ru.exceptions.NotFoundException;

/**
 * Сведение разрезов с оглядкой на выбранный период.
 *
 * <p>Тонкий слой поверх чистой {@link ScheduleMerger}: единственное, чего той не хватает, — границы
 * периода, а они лежат в базе. Сам разбор и сведение базы не касаются вовсе и покрываются
 * быстрыми тестами.</p>
 *
 * <p><b>Период — параметр человека, а не догадка из файла</b> (то же решение, что с локацией, И-17).
 * В шапке напечатан только учебный год и «осенний/весенний»; какой именно {@code study_period}
 * имеется в виду и какие у него границы — знает тот, кто запускает импорт. Зато напечатанный год
 * работает проверкой: расхождение с годом периода — находка, а не молчаливый сдвиг всего
 * расписания (И-2).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportMergeService {

    private final StudyPeriodRepository periodRepository;

    /**
     * Сводит разобранные файлы и отдаёт отчёт.
     *
     * @param sheets   разобранные файлы любых разрезов
     * @param periodId период импорта; {@code null} — период не выбран, выбросы за его границы не
     *                 считаются (и об этом сказано в отчёте нулём, а не выдуманным числом)
     * @param style    написание номера группы
     * @throws NotFoundException если периода с таким id нет —
     *         это ошибка запроса, а не сбой
     */
    @Transactional(readOnly = true)
    public MergeReport merge(List<ParsedSheet> sheets, Integer periodId, SuffixStyle style) {
        MergeReport report = ScheduleMerger.merge(sheets, style, bounds(periodId)).report();
        log.info("Сведение: занятий {} из {} ячеек; без преподавателя {}, без вида {}, "
                        + "потеряно групповым разрезом {}, вне периода {}",
                report.lessons(), report.entries(), report.withoutEducator(), report.withoutKind(),
                report.missedByGroupCut(), report.outsidePeriod());
        return report;
    }

    /** Границы выбранного периода либо {@code null}, если период не назван. */
    public PeriodBounds bounds(Integer periodId) {
        if (periodId == null) {
            return null;
        }
        StudyPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new NotFoundException("Периода с id " + periodId + " нет"));
        return new PeriodBounds(period.getStudyYear(), period.getStartDate(), period.getEndDate());
    }
}
