package ru.abstracts;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Auditorium;
import ru.entity.Educator;
import ru.entity.logicSchema.AuditoriumPool;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCourse;
import ru.entity.logicSchema.StudyStream;
import ru.enums.KindOfStudy;

import java.util.*;

/**
 * Базовый класс, представляющий "заявку на занятие" или уже размещенное занятие.
 * Является POJO и используется алгоритмом решателя. Не является JPA-сущностью.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class AbstractLesson {

     /**
     * Ссылка на курс (Дисциплина + Семестр), к которому относится это занятие.
     */
    protected DisciplineCourse disciplineCourse;

    /**
     * Ссылка на конкретный слот в учебном плане. Является уникальным идентификатором "замысла" занятия.
     */
    protected CurriculumSlot curriculumSlot;


    /**
     * Преподаватели, ведущие это занятие. Может быть несколько для параллельных подгрупп или совместных лекций.
     */
    protected Set<Educator> educators = new HashSet<>();

    /**
     * Поток/подгруппа, для которой предназначено это занятие.
     */
    protected StudyStream studyStream;

    /**
     * Аудитории, УЖЕ НАЗНАЧЕННЫЕ для этого занятия алгоритмом.
     * Изначально это поле пустое. Заполняется после успешного размещения.
     */
    protected List<Auditorium> assignedAuditoriums = new ArrayList<>();

    // --- Требования к ресурсам (копируются из CurriculumSlot для удобства) ---

    protected Auditorium requiredAuditorium;
    protected Auditorium priorityAuditorium;
    protected AuditoriumPool allowedAuditoriumPool;


    /**
     * Уникальный ID для группы параллельных занятий.
     * Занятия с одинаковым parallelGroupId должны быть размещены в одном временном слоте.
     * Может быть null, если занятие не является частью параллельного блока.
     */
    protected String parallelGroupId;


    // --- Полезные методы ---

    public KindOfStudy getKindOfStudy() {
        return curriculumSlot != null ? curriculumSlot.getKindOfStudy() : null;
    }

    /**
     * Определяет, является ли это занятие частью "сцепки" неразрывных занятий.
     * (Логика будет зависеть от того, как мы будем работать со SlotChain)
     */
    public boolean isChained() {
        // TODO: Реализовать, если понадобится, через SlotChainService
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AbstractLesson that = (AbstractLesson) o;
        return Objects.equals(curriculumSlot, that.curriculumSlot) && Objects.equals(educators, that.educators) && Objects.equals(studyStream, that.studyStream);
    }

    @Override
    public int hashCode() {
        return Objects.hash(curriculumSlot, educators, studyStream);
    }

    @Override
    public String toString() {
        String courseName = (disciplineCourse != null && disciplineCourse.getDiscipline() != null)
                ? disciplineCourse.getDiscipline().getAbbreviation()
                : "N/A";
        String streamName = (studyStream != null) ? studyStream.getName() : "N/A";
        return String.format("%s %s (%s)", getKindOfStudy(), courseName, streamName);
    }
}

