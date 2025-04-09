package ru.abstracts;

import ru.entity.CellForLesson;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.Group;
import ru.enums.KindOfStudy;

import java.util.ArrayList;
import java.util.List;

abstract public class AbstractLesson  {
    protected CellForLesson cellForLesson;
    protected KindOfStudy kindOfStudy;
    protected Discipline discipline;
    protected AbstractAuditorium auditorium;
    protected List<Educator> educators = new ArrayList<>();
    protected List<Group> groups = new ArrayList<>();

    public AbstractLesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator) {
        super();
        this.discipline = discipline;
        this.kindOfStudy = kindOfStudy;
        this.educators.add(educator);
    }

    public List<Group> getGroups() {
        return groups;
    }
    public void addGroup(Group group) {
        this.groups.add(group);
    }
    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public void setCellForLesson(CellForLesson cellForLesson) {
        this.cellForLesson = cellForLesson;
    }

    public void addEducator(Educator educator) {
        this.educators.add(educator);
    }

    public CellForLesson getCellForLesson() {
        return cellForLesson;
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
        return "kind: " + kindOfStudy +
                ", dis: " + discipline +
                ", educ: " + educators;
    }
}
