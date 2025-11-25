package ru.entity.factories;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.abstracts.AbstractLesson;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.GroupCombination;
import ru.entity.Lesson;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.entity.logicSchema.StudyStream;
import ru.enums.KindOfStudy;
import ru.repository.StudyStreamRepository;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component // Делаем его Spring-компонентом
@RequiredArgsConstructor
public class LessonFactory {
/*
    private final StudyStreamRepository studyStreamRepository; // Нужен для получения групп
    private final EducatorAssignmentService educatorAssignmentService; // Условный сервис назначения преподавателей

    *//**
     * Создает список "заявок на занятия" (Lesson) на основе учебного плана курса.
     * @param disciplineCourse Курс, для которого генерируются занятия.
     * @return Список объектов Lesson.
     *//*
    public List<Lesson> createLessonsForCourse(DisciplineCourse disciplineCourse) {
        List<Lesson> lessons = new ArrayList<>();

        // curriculumSlots уже отсортированы по position благодаря @OrderBy
        for (CurriculumSlot slot : disciplineCourse.getCurriculumSlots()) {

            // Если для слота действует правило разделения на подгруппы
            if ("BY_SIZE_15".equals(slot.getSplitRule())) {
                // Получаем поток, привязанный к слоту
                StudyStream mainStream = slot.getStudyStream();

                // Для каждой группы в потоке, которая требует деления...
                for (Group group : mainStream.getGroups()) {
                    if (group.getSize() > 15) {
                        // ...создаем ДВА или больше занятий
                        // Здесь логика деления на подгруппы и назначения разных преподавателей
                        // lessons.addAll(createSplitLessonsForGroup(slot, group));
                    } else {
                        // lessons.add(createSingleLessonFor(slot, new StudyStream(List.of(group))));
                    }
                }
            } else { // Обычное занятие, без деления
                Lesson lesson = new Lesson();
                lesson.setDisciplineCourse(slot.getDisciplineCourse());
                lesson.setCurriculumSlot(slot); // Ссылка на исходный слот плана

                // Копируем требования к ресурсам
                lesson.setStudyStream(slot.getStudyStream());
                lesson.setRequiredAuditorium(slot.getRequiredAuditorium());
                lesson.setPriorityAuditorium(slot.getPriorityAuditorium());
                lesson.setAllowedAuditoriumPool(slot.getAllowedAuditoriumPool());

                // Назначаем преподавателя
                Educator assignedEducator = educatorAssignmentService.findEducatorFor(slot);
                lesson.setEducators(Set.of(assignedEducator));

                lessons.add(lesson);
            }
        }
        return lessons;
    }*/
}
