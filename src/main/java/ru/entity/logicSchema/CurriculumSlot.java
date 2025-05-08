package ru.entity.logicSchema;

import ru.enums.KindOfStudy;

public class CurriculumSlot {
    private int id;
    private KindOfStudy kindOfStudy;
    private ThemeLesson themeLesson;


    public CurriculumSlot() {
    }

    public CurriculumSlot(int id, KindOfStudy kindOfStudy, ThemeLesson themeLesson) {
        this.id = id;
        this.kindOfStudy = kindOfStudy;
        this.themeLesson = themeLesson;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public KindOfStudy getKindOfStudy() {
        return kindOfStudy;
    }

    public void setKindOfStudy(KindOfStudy kindOfStudy) {
        this.kindOfStudy = kindOfStudy;
    }

    public ThemeLesson getThemeLesson() {
        return themeLesson;
    }

    public void setThemeLesson(ThemeLesson themeLesson) {
        this.themeLesson = themeLesson;
    }
}
