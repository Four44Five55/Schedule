package ru.mapper.command;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.dto.ScheduledLessonDto;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.read.ScheduleView;
import ru.entity.write.LessonPlacement;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ScheduledLessonMapper {

    @Mapping(target = "id", expression = "java(lesson.getCurriculumSlot().getId())")
    @Mapping(target = "date", source = "cell.date")
    @Mapping(target = "timeSlotPair", source = "cell.timeSlotPair")
    @Mapping(target = "disciplineName", expression = "java(lesson.getDisciplineCourse().getDiscipline().getName())")
    @Mapping(target = "disciplineAbbreviation", expression = "java(lesson.getDisciplineCourse().getDiscipline().getAbbreviation())")
    @Mapping(target = "kindOfStudy", expression = "java(lesson.getKindOfStudy())")
    @Mapping(target = "kindOfStudyName", expression = "java(lesson.getKindOfStudy().getFullName())")
    @Mapping(target = "kindOfStudyAbbr", expression = "java(lesson.getKindOfStudy().getAbbreviationName())")
    @Mapping(target = "position", expression = "java(lesson.getCurriculumSlot().getPosition())")
    @Mapping(target = "themeNumber", expression = "java(lesson.getCurriculumSlot().getThemeLesson() != null ? lesson.getCurriculumSlot().getThemeLesson().getThemeNumber() : null)")
    @Mapping(target = "themeTitle", expression = "java(lesson.getCurriculumSlot().getThemeLesson() != null ? lesson.getCurriculumSlot().getThemeLesson().getTitle() : null)")
    @Mapping(target = "streamName", expression = "java(lesson.getStudyStream() != null ? lesson.getStudyStream().getName() : null)")
    @Mapping(target = "educatorIds", expression = "java(lesson.getEducators() != null ? lesson.getEducators().stream().map(e -> e.getId()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "educatorNames", expression = "java(lesson.getEducators() != null ? lesson.getEducators().stream().map(e -> e.getName()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "groupNames", expression = "java(lesson.getStudyStream() != null && lesson.getStudyStream().getGroups() != null ? lesson.getStudyStream().getGroups().stream().map(g -> g.getName()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "auditoriumIds", expression = "java(lesson.getAssignedAuditoriums() != null ? lesson.getAssignedAuditoriums().stream().map(a -> a.getId()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "auditoriumNames", expression = "java(lesson.getAssignedAuditoriums() != null ? lesson.getAssignedAuditoriums().stream().map(a -> a.getName()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "placementId", ignore = true)
    @Mapping(target = "curriculumSlotId", expression = "java(lesson.getCurriculumSlot() != null ? lesson.getCurriculumSlot().getId() : null)")
    // In-memory Lesson не несёт признака пина — на выходе генерации всё незакреплённое/сгенерированное.
    @Mapping(target = "locked", expression = "java(false)")
    @Mapping(target = "source", expression = "java(\"GENERATED\")")
    ScheduledLessonDto toDto(Lesson lesson, CellForLesson cell);

    // id (Integer) на read-пути не несёт смысла — идентификация занятия идёт по
    // placementId (UUID). Раньше маппился из curriculumSlotId, которого нет в schedule_view.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "date", source = "scheduledDate")
    @Mapping(target = "timeSlotPair", source = "timeSlot")
    @Mapping(target = "disciplineName", source = "disciplineName")
    @Mapping(target = "disciplineAbbreviation", source = "disciplineAbbr")
    @Mapping(target = "kindOfStudy", expression = "java(view.getKindOfStudy() != null ? ru.enums.KindOfStudy.valueOf(view.getKindOfStudy()) : null)")
    @Mapping(target = "kindOfStudyName", expression = "java(view.getKindOfStudy() != null ? ru.enums.KindOfStudy.valueOf(view.getKindOfStudy()).getFullName() : null)")
    @Mapping(target = "kindOfStudyAbbr", expression = "java(view.getKindOfStudy() != null ? ru.enums.KindOfStudy.valueOf(view.getKindOfStudy()).getAbbreviationName() : null)")
    @Mapping(target = "position", expression = "java(0)")
    @Mapping(target = "educatorIds", expression = "java(view.getEducatorId() != null ? java.util.List.of(view.getEducatorId()) : java.util.Collections.emptyList())")
    @Mapping(target = "educatorNames", expression = "java(view.getEducatorName() != null ? java.util.List.of(view.getEducatorName()) : java.util.Collections.emptyList())")
    @Mapping(target = "streamName", expression = "java(view.getGroupName())")
    @Mapping(target = "groupNames", expression = "java(view.getGroupName() != null ? java.util.List.of(view.getGroupName()) : java.util.Collections.emptyList())")
    @Mapping(target = "auditoriumIds", expression = "java(view.getAuditoriumId() != null ? java.util.List.of(view.getAuditoriumId()) : java.util.Collections.emptyList())")
    @Mapping(target = "auditoriumNames", expression = "java(view.getAuditoriumName() != null ? java.util.List.of(view.getAuditoriumName()) : java.util.Collections.emptyList())")
    @Mapping(target = "placementId", expression = "java(view.getPlacementId() != null ? view.getPlacementId().toString() : null)")
    @Mapping(target = "curriculumSlotId", source = "curriculumSlotId")
    @Mapping(target = "locked", source = "locked")
    @Mapping(target = "source", source = "source")
    ScheduledLessonDto toDto(ScheduleView view);

    /**
     * ✅ ГИБРИДНЫЙ МЕТОД: Конвертация из LessonPlacement (Command Side).
     *
     * <p>Используется для немедленного возврата данных фронтенду после генерации,
     * минуя ожидание синхронизации Query Side.</p>
     *
     * <p>Извлекает данные из placement.assignment (курс, преподаватели, группа)
     * и placement (дата, время, аудитории).</p>
     */
    @Mapping(target = "id", expression = "java(placement.getId() != null ? placement.getId().hashCode() : 0)")
    @Mapping(target = "date", source = "scheduledDate")
    @Mapping(target = "timeSlotPair", source = "scheduledSlot")
    @Mapping(target = "disciplineName", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null && placement.getAssignment().getCurriculumSlot().getDisciplineCourse() != null && placement.getAssignment().getCurriculumSlot().getDisciplineCourse().getDiscipline() != null ? placement.getAssignment().getCurriculumSlot().getDisciplineCourse().getDiscipline().getName() : null)")
    @Mapping(target = "disciplineAbbreviation", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null && placement.getAssignment().getCurriculumSlot().getDisciplineCourse() != null && placement.getAssignment().getCurriculumSlot().getDisciplineCourse().getDiscipline() != null ? placement.getAssignment().getCurriculumSlot().getDisciplineCourse().getDiscipline().getAbbreviation() : null)")
    @Mapping(target = "kindOfStudy", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null ? placement.getAssignment().getCurriculumSlot().getKindOfStudy() : null)")
    @Mapping(target = "kindOfStudyName", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null && placement.getAssignment().getCurriculumSlot().getKindOfStudy() != null ? placement.getAssignment().getCurriculumSlot().getKindOfStudy().getFullName() : null)")
    @Mapping(target = "kindOfStudyAbbr", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null && placement.getAssignment().getCurriculumSlot().getKindOfStudy() != null ? placement.getAssignment().getCurriculumSlot().getKindOfStudy().getAbbreviationName() : null)")
    @Mapping(target = "position", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null ? placement.getAssignment().getCurriculumSlot().getPosition() : 0)")
    @Mapping(target = "themeNumber", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null && placement.getAssignment().getCurriculumSlot().getThemeLesson() != null ? placement.getAssignment().getCurriculumSlot().getThemeLesson().getThemeNumber() : null)")
    @Mapping(target = "themeTitle", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null && placement.getAssignment().getCurriculumSlot().getThemeLesson() != null ? placement.getAssignment().getCurriculumSlot().getThemeLesson().getTitle() : null)")
    @Mapping(target = "streamName", expression = "java(placement.getAssignment() != null && placement.getAssignment().getStudyStream() != null ? placement.getAssignment().getStudyStream().getName() : null)")
    @Mapping(target = "educatorIds", expression = "java(placement.getAssignment() != null && placement.getAssignment().getEducators() != null ? placement.getAssignment().getEducators().stream().map(e -> e.getId()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "educatorNames", expression = "java(placement.getAssignment() != null && placement.getAssignment().getEducators() != null ? placement.getAssignment().getEducators().stream().map(e -> e.getName()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "groupNames", expression = "java(placement.getAssignment() != null && placement.getAssignment().getStudyStream() != null && placement.getAssignment().getStudyStream().getGroups() != null ? placement.getAssignment().getStudyStream().getGroups().stream().map(g -> g.getName()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "auditoriumIds", expression = "java(placement.getAssignedAuditoriums() != null ? placement.getAssignedAuditoriums().stream().map(a -> a.getId()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "auditoriumNames", expression = "java(placement.getAssignedAuditoriums() != null ? placement.getAssignedAuditoriums().stream().map(a -> a.getName()).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
    @Mapping(target = "placementId", expression = "java(placement.getId() != null ? placement.getId().toString() : null)")
    @Mapping(target = "curriculumSlotId", expression = "java(placement.getAssignment() != null && placement.getAssignment().getCurriculumSlot() != null ? placement.getAssignment().getCurriculumSlot().getId() : null)")
    @Mapping(target = "locked", source = "locked")
    @Mapping(target = "source", expression = "java(placement.getSource() != null ? placement.getSource().name() : \"GENERATED\")")
    ScheduledLessonDto toDto(LessonPlacement placement);
}