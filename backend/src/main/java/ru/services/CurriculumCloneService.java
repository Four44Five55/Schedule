package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.disciplineCourse.CourseCloneRequestDto;
import ru.dto.disciplineCourse.DisciplineCourseDto;
import ru.entity.StudyPeriod;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.SlotChain;
import ru.mapper.DisciplineCourseMapper;
import ru.repository.CurriculumSlotRepository;
import ru.repository.DisciplineCourseRepository;
import ru.repository.SlotChainRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Копирование учебного плана: глубокая копия курса (со слотами и сцепками) из одного
 * периода в другой. Выделено в отдельный сервис (SRP) — это самостоятельная операция,
 * не относящаяся к обычному CRUD курсов в {@link DisciplineCourseService}.
 *
 * <p>Это не GoF Prototype (полиморфный {@code clone()} на сущности): копирование
 * управляемых JPA-сущностей делаем контролируемо в сервисе-копировщике. Источник слотов
 * сейчас один — курс прошлого периода; шов оставлен на уровне методов, чтобы позже без
 * переписывания добавить копирование из шаблона ({@code curriculum_template_slot}).</p>
 */
@Service
@RequiredArgsConstructor
public class CurriculumCloneService {

    private final DisciplineCourseRepository disciplineCourseRepository;
    private final CurriculumSlotRepository curriculumSlotRepository;
    private final SlotChainRepository slotChainRepository;
    private final StudyPeriodService studyPeriodService;
    private final DisciplineCourseMapper disciplineCourseMapper;

    /**
     * Копирует выбранные курсы-источники в целевой период. Операция атомарна: если хотя бы
     * один курс уже существует в целевом периоде (дубль по discipline+semester) —
     * откатывается всё (atomic-fail), частично скопированных состояний не остаётся.
     */
    @Transactional
    public List<DisciplineCourseDto> cloneCourses(CourseCloneRequestDto request) {
        StudyPeriod target = studyPeriodService.getEntityById(request.targetPeriodId());

        List<DisciplineCourseDto> result = new ArrayList<>();
        for (Integer sourceId : request.sourceCourseIds()) {
            DisciplineCourse source = disciplineCourseRepository.findById(sourceId)
                    .orElseThrow(() -> new EntityNotFoundException("Курс-источник с id=" + sourceId + " не найден."));
            result.add(disciplineCourseMapper.toDto(cloneOne(source, target)));
        }
        return result;
    }

    /**
     * Глубокая копия одного курса в целевой период: курс → слоты → сцепки (с ремапом).
     */
    private DisciplineCourse cloneOne(DisciplineCourse source, StudyPeriod target) {
        // Дубль по уникальности (discipline, period, semester) — atomic-fail.
        if (disciplineCourseRepository.existsByDisciplineIdAndStudyPeriodIdAndSemester(
                source.getDiscipline().getId(), target.getId(), source.getSemester())) {
            throw new IllegalStateException("Курс «" + source.getDiscipline().getName() + "», семестр "
                    + source.getSemester() + " уже существует в периоде «" + target.getName() + "».");
        }

        DisciplineCourse copy = disciplineCourseRepository.save(
                new DisciplineCourse(source.getDiscipline(), target, source.getSemester()));

        // Слоты: глубокая копия + карта старый→новый для последующего ремапа сцепок.
        // (Источник слотов — прошлый курс; здесь точка расширения под TemplateSource.)
        Map<Integer, CurriculumSlot> slotRemap = new HashMap<>();
        for (CurriculumSlot src : curriculumSlotRepository.findByDisciplineCourseIdOrderByPosition(source.getId())) {
            CurriculumSlot saved = curriculumSlotRepository.save(copySlot(src, copy));
            slotRemap.put(src.getId(), saved);
        }

        // Сцепки внутрикурсовые: пересоздаём на новых слотах через карту ремапа.
        for (SlotChain chain : slotChainRepository.findByCourseId(source.getId())) {
            CurriculumSlot newA = slotRemap.get(chain.getSlotA().getId());
            CurriculumSlot newB = slotRemap.get(chain.getSlotB().getId());
            slotChainRepository.save(new SlotChain(newA, newB));
        }

        // Assignment (потоки/преподаватели) намеренно НЕ копируем — меняются по периодам.
        return copy;
    }

    /**
     * Копия слота: скаляры + те же ссылки на тему/аудитории/пул (тема глобальна — общая ссылка).
     * Новый id/курс проставляются здесь; позиции сохраняются как у источника.
     */
    private CurriculumSlot copySlot(CurriculumSlot src, DisciplineCourse targetCourse) {
        CurriculumSlot dst = new CurriculumSlot();
        dst.setDisciplineCourse(targetCourse);
        dst.setPosition(src.getPosition());
        dst.setKindOfStudy(src.getKindOfStudy());
        // Где сдаётся аттестация — часть замысла плана, а не свойство периода: клон обязан её
        // перенести, иначе экзамен в новом семестре молча уедет в учебное время.
        dst.setAssessmentWindow(src.getAssessmentWindow());
        dst.setThemeLesson(src.getThemeLesson());
        dst.setRequiredAuditorium(src.getRequiredAuditorium());
        dst.setPriorityAuditorium(src.getPriorityAuditorium());
        dst.setAllowedAuditoriumPool(src.getAllowedAuditoriumPool());
        return dst;
    }
}
