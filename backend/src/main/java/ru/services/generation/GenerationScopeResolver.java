package ru.services.generation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.dto.disciplineCourse.DisciplineCourseDto;
import ru.entity.Lesson;
import ru.entity.StudyPeriod;
import ru.entity.logicSchema.DisciplineCourse;
import ru.services.DisciplineCourseService;
import ru.services.StudyPeriodService;
import ru.services.factories.LessonFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Разрешает запрос генерации «на учебный период» в конкретную {@link GenerationScope}.
 *
 * <p>Ответственность (SRP): по {@code studyPeriodId} и опциональному поднабору курсов
 * собрать период, его курсы (с проверкой принадлежности периоду) и плоский список занятий.
 * Извлекает логику выбора периода/дат из {@code ScheduleGenerationService}, чтобы солвер
 * получал явный, единственный источник дат и занятий (DIP/OCP: новые виды области —
 * например, по потоку — добавляются здесь, не трогая солвер).</p>
 *
 * <p>Транзакция: вызывается из {@code @Transactional}-метода сервиса, поэтому ленивые
 * ассоциации (период курса, слоты, назначения) навигируются в активной сессии.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationScopeResolver {

    private final StudyPeriodService studyPeriodService;
    private final DisciplineCourseService disciplineCourseService;
    private final LessonFactory lessonFactory;

    /**
     * @param studyPeriodId период генерации (обязателен)
     * @param courseIds     опциональный поднабор курсов периода; если пуст/{@code null} —
     *                      берутся все курсы периода
     * @return область генерации (период + занятия)
     * @throws IllegalArgumentException если период не задан или курс не относится к периоду
     * @throws IllegalStateException    если в периоде нет курсов для генерации
     */
    public GenerationScope resolve(Integer studyPeriodId, List<Integer> courseIds) {
        if (studyPeriodId == null) {
            throw new IllegalArgumentException("Не указан учебный период для генерации (studyPeriodId)");
        }

        StudyPeriod period = studyPeriodService.getEntityById(studyPeriodId);

        List<Integer> effectiveCourseIds = resolveCourseIds(studyPeriodId, courseIds);
        if (effectiveCourseIds.isEmpty()) {
            throw new IllegalStateException(
                    "Для периода '" + period.getName() + "' (id=" + studyPeriodId + ") нет курсов для генерации");
        }

        List<Lesson> lessons = buildLessons(effectiveCourseIds);
        log.info("Область генерации: период '{}' [{} — {}], курсов: {}, занятий: {}",
                period.getName(), period.getStartDate(), period.getEndDate(),
                effectiveCourseIds.size(), lessons.size());

        return new GenerationScope(period, lessons);
    }

    /**
     * Либо все курсы периода (если поднабор не задан), либо переданный поднабор
     * с валидацией принадлежности периоду.
     */
    private List<Integer> resolveCourseIds(Integer studyPeriodId, List<Integer> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return disciplineCourseService.findAllByStudyPeriod(studyPeriodId).stream()
                    .map(DisciplineCourseDto::id)
                    .toList();
        }

        for (Integer courseId : courseIds) {
            DisciplineCourse course = disciplineCourseService.getEntityById(courseId);
            if (!course.getStudyPeriod().getId().equals(studyPeriodId)) {
                throw new IllegalArgumentException(
                        "Курс id=" + courseId + " не относится к периоду id=" + studyPeriodId);
            }
        }
        return courseIds;
    }

    /**
     * Собирает занятия по курсам, сортируя внутри курса по позиции слота
     * (как и прежняя логика в сервисе).
     */
    private List<Lesson> buildLessons(List<Integer> courseIds) {
        List<Lesson> lessons = new ArrayList<>();
        for (Integer courseId : courseIds) {
            List<Lesson> courseLessons = lessonFactory.createLessonsForCourse(courseId);
            courseLessons.sort(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()));
            lessons.addAll(courseLessons);
        }
        return lessons;
    }
}
