package ru.entity.logicSchema;

public class ThemeLesson {
    private int id;
    private String themeNumber;
    private String title;

    public ThemeLesson() {
    }

    public ThemeLesson(int id, String themeNumber, String title) {
        this.id = id;
        this.themeNumber = themeNumber;
        this.title = title;
    }

    public String getThemeNumber() {
        return themeNumber;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
