package ru.utils;

import ru.entity.logicSchema.DisciplineCurriculum;

public class CurriculumPrinter {

    public static void print(DisciplineCurriculum curriculum) {
        System.out.println("=== Учебный план ===");
        System.out.printf("Дисциплина: %s (%s)\n",
                curriculum.getDiscipline().getName(),
                curriculum.getDiscipline().getAbbreviation());

        System.out.println("\nЗанятия:");
        curriculum.getCurriculumSlots().forEach(slot -> {
            String themeInfo = slot.getThemeLesson() != null ?
                    String.format("Тема %s: %s",
                            slot.getThemeLesson().getThemeNumber(),
                            slot.getThemeLesson().getTitle()) :
                    "Контрольное мероприятие";

            System.out.printf("- %-25s (%s)\n",
                    slot.getKindOfStudy().getFullName(),
                    themeInfo);
        });
    }
}

