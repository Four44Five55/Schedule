package ru.services.distribution;

import ru.entity.*;
import ru.enums.TimeSlotPair;
import ru.services.CurriculumSlotService;
import ru.services.SlotChainService;
import ru.services.solver.model.ScheduleGrid;
import ru.utils.ListLessonsHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DistributionDisciplineUniform {
   /* ScheduleGrid scheduleGrid;
    List<Lesson> lessons;
    List<Educator> educators;
    List<GroupCombination> groupCombinations;
    SlotChainService slotChainService;
    CurriculumSlotService curriculumSlotService;
    List<Lesson> distributedLessons = new ArrayList<>();


    public DistributionDisciplineUniform(ScheduleGrid scheduleGrid, List<Lesson> lessons, List<Educator> educators, List<GroupCombination> groupCombinationList, SlotChainService slotChainService, CurriculumSlotService curriculumSlotService) {
        this.scheduleGrid = scheduleGrid;
        this.lessons = lessons;
        this.educators = educators;
        this.groupCombinations = groupCombinationList;
        this.slotChainService = slotChainService;
        this.curriculumSlotService = curriculumSlotService;
    }

    public void distributeUniformLessons() {
        for (Educator educator : educators) {
            distributeUniformLessonsForEducator(educator);
        }
    }

    *//**
     * Распределение занятий для конкретного преподавателя
     *//*
    private void distributeUniformLessonsForEducator(Educator educator) {
        ListLessonsHelper lessonsHelper = new ListLessonsHelper(curriculumSlotService, slotChainService);
        List<CellForLesson> cellForLessons = scheduleGrid.getAvailableCells(groupCombinations, List.of(educator));
        ;

        List<Lesson> currentLessons = lessonsHelper.changeOrderLessons(ListLessonsHelper.distributeExam(lessons));


        List<LocalDate> uniqueDates = cellForLessons.stream()
                .map(CellForLesson::getDate)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());


        Map<LocalDate, List<Lesson>> disLesson = ListLessonsHelper.distributeLessons(currentLessons, uniqueDates);
        moveLessonsInGeneralSchedule(disLesson);
    }

    *//**
     * Метод записи в общее расписание занятий данных из нагрузки преподавателя
     **//*
    private void moveLessonsInGeneralSchedule(Map<LocalDate, List<Lesson>> distributedLessons) {
        for (Map.Entry<LocalDate, List<Lesson>> entry : distributedLessons.entrySet()) {
            LocalDate date = entry.getKey();
            List<Lesson> lessons = entry.getValue();
            if (lessons.size() == 3) {
                for (int i = 0; i < 3; i++) {
                    scheduleGrid.addLessonToCell(new CellForLesson(date, TimeSlotPair.values()[i]), lessons.get(i));
                }
            } else if (lessons.size() == 2) {
                for (int i = 0; i < 2; i++) {
                    scheduleGrid.addLessonToCell(new CellForLesson(date, TimeSlotPair.values()[i + 1]), lessons.get(i));
                }
            } else if (lessons.size() == 1) {
                scheduleGrid.addLessonToCell(new CellForLesson(date, TimeSlotPair.values()[1]), lessons.getFirst());
            }
        }
    }
*/

}
