package ru.entity.logicSchema;

import ru.entity.Discipline;

import java.util.List;

public class DisciplineCurriculum {
    private int id;
    private Discipline discipline;
    private List<CurriculumSlot> curriculumSlots;

    private ChainManager chainManager;

    public DisciplineCurriculum() {
    }

    public DisciplineCurriculum(int id, Discipline discipline, List<CurriculumSlot> curriculumSlots,ChainManager chainManager) {
        this.id = id;
        this.discipline = discipline;
        this.curriculumSlots = curriculumSlots;
        this.chainManager = chainManager;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Discipline getDiscipline() {
        return discipline;
    }

    public void setDiscipline(Discipline discipline) {
        this.discipline = discipline;
    }

    public List<CurriculumSlot> getCurriculumSlots() {
        return curriculumSlots;
    }

    public void setCurriculumSlots(List<CurriculumSlot> curriculumSlots) {
        this.curriculumSlots = curriculumSlots;
    }

    public ChainManager getChainManager() {
        return chainManager;
    }

    public void setChainManager(ChainManager chainManager) {
        this.chainManager = chainManager;
    }
}
