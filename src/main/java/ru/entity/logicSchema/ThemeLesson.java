package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ThemeLesson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String themeNumber;

    private String title;


    public ThemeLesson(String themeNumber, String title) {
        this.themeNumber = themeNumber;
        this.title = title;
    }

    @Override
    public String toString() {
        return "ThemeLesson{" +
                "id=" + id +
                ", themeNumber='" + themeNumber + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
