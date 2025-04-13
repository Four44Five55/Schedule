package ru.abstracts;

import ru.entity.*;
import ru.enums.KindOfStudy;

import java.util.ArrayList;
import java.util.List;

abstract public class AbstractLesson {
    protected Discipline discipline;
    protected KindOfStudy kindOfStudy;
    protected List<Educator> educators = new ArrayList<>();
    protected List<GroupCombination> groupCombinations = new ArrayList<>();
    protected AbstractAuditorium auditorium;

    public AbstractLesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator,GroupCombination groupCombinations) {
        super();
        this.discipline = discipline;
        this.kindOfStudy = kindOfStudy;
        this.educators.add(educator);
        this.groupCombinations.add(groupCombinations);
    }

    public List<GroupCombination> getGroups() {
        return groupCombinations;
    }

    public void addGroup(GroupCombination groupCombination) {
        this.groupCombinations.add(groupCombination);
    }

    public void setGroups(List<GroupCombination> groupCombinations) {
        this.groupCombinations = groupCombinations;
    }


    public void addEducator(Educator educator) {
        this.educators.add(educator);
    }

    public Discipline getDiscipline() {
        return discipline;
    }

    public KindOfStudy getKindOfStudy() {
        return kindOfStudy;
    }

    public List<Educator> getEducators() {
        return educators;
    }

    public AbstractAuditorium getAuditorium() {
        return auditorium;
    }

    public void setAuditorium(AbstractAuditorium auditorium) {
        this.auditorium = auditorium;
    }

    @Override
    public String toString() {
        return "dis: " + discipline +
                ",kind: " + kindOfStudy.getAbbreviationName() +
                 ", educ: " + educators+
                ", "+ groupCombinations;
    }
}
