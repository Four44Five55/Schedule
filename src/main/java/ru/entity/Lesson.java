package ru.entity;

import lombok.NoArgsConstructor;
import ru.abstracts.AbstractLesson;

import java.util.stream.Collectors;

/**
 * Конкретная реализация "заявки на занятие".
 * Используется в алгоритме для представления одного планируемого события.
 */
@NoArgsConstructor
public class Lesson extends AbstractLesson {
    @Override
    public String toString() {
        String courseName = (disciplineCourse != null && disciplineCourse.getDiscipline() != null)
                ? disciplineCourse.getDiscipline().getAbbreviation()
                : "N/A";
        String position = (curriculumSlot != null)
                ? curriculumSlot.getPosition().toString()
                : "N/A";
        String themeLesson = (curriculumSlot != null)
                ? curriculumSlot.getThemeLesson().getThemeNumber()
                : "N/A";
        String groupsName;
        if (studyStream != null && studyStream.getGroups() != null && !studyStream.getGroups().isEmpty()) {
            groupsName = studyStream.getGroups().stream()
                    .map(Group::getName)
                    .collect(Collectors.joining(", "));
        } else {
            groupsName = "N/A";
        }

        return String.format("%s, %s/T.%s, (%s), [%s]",
                courseName,
                getKindOfStudy().getAbbreviationName(),
                themeLesson,
                position,
                groupsName);

    }
}
