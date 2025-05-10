package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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

    // Двунаправленная связь с CurriculumSlot
    @OneToMany(
            mappedBy = "themeLesson",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<CurriculumSlot> curriculumSlots = new ArrayList<>();

    public ThemeLesson(String themeNumber, String title) {
        this.themeNumber = themeNumber;
        this.title = title;
    }

    /**
     * Добавляет слот учебного плана и устанавливает двунаправленную связь
     */
    public void addCurriculumSlot(CurriculumSlot slot) {
        curriculumSlots.add(slot);
        slot.setThemeLesson(this);
    }

    /**
     * Удаляет слот учебного плана и разрывает связь
     */
    public void removeCurriculumSlot(CurriculumSlot slot) {
        curriculumSlots.remove(slot);
        slot.setThemeLesson(null);
    }

    // equals и hashCode (без учета связей!)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThemeLesson)) return false;
        ThemeLesson that = (ThemeLesson) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
