package ru.abstracts;

import ru.entity.*;
import ru.enums.KindOfStudy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

abstract public class AbstractLesson {
    protected Discipline discipline;
    protected KindOfStudy kindOfStudy;
    protected List<Educator> educators = new ArrayList<>();
    protected List<GroupCombination> groupCombinations = new ArrayList<>();
    protected List<AbstractAuditorium> auditoriums = new ArrayList<>();

    public AbstractLesson() {
    }

    public AbstractLesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator, List<GroupCombination> groupCombinations) {
        super();
        this.discipline = discipline;
        this.kindOfStudy = kindOfStudy;
        this.educators.add(educator);
        this.groupCombinations.addAll(groupCombinations);
        this.auditoriums.add(GroupCombination.calculateCapacityAuditoriumForCombinations(groupCombinations));
    }
    public AbstractLesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator, GroupCombination groupCombinations) {
        super();
        this.discipline = discipline;
        this.kindOfStudy = kindOfStudy;
        this.educators.add(educator);
        this.groupCombinations.add(groupCombinations);
        this.auditoriums.add(groupCombinations.getAuditorium());
    }

    public List<AbstractMaterialEntity> getAllMaterialEntity() {
        List<AbstractMaterialEntity> entities = new ArrayList<>();
        Optional.ofNullable(educators).ifPresent(entities::addAll);
        List<Group> groups = new ArrayList<>();
        for (GroupCombination groupCombination : groupCombinations) {
            groups.addAll(groupCombination.getGroups());
        }
        Optional.of(groups).ifPresent(entities::addAll);
        Optional.ofNullable(auditoriums).ifPresent(entities::addAll);

        return entities;
    }


    /**
     * Проверяет, используется ли сущность в этом занятии
     *
     * @param entity проверяемая сущность (аудитория, преподаватель, группа)
     * @return true если сущность используется в занятии
     */
    public boolean isEntityUsed(AbstractMaterialEntity entity) {
        // Проверка аудитории
        if (entity instanceof AbstractAuditorium) {
            return this.auditoriums.contains(entity);
        }

        // Проверка преподавателя
        if (entity instanceof Educator) {
            return this.educators.contains(entity);
        }

        // Проверка группы
        if (entity instanceof Group) {
            return this.groupCombinations.stream()
                    .anyMatch(comb -> comb.getGroups().contains(entity));
        }

        return false;
    }

    public List<GroupCombination> getGroupsCombinations() {
        return groupCombinations;
    }

    public List<Group> getGroups() {
        return groupCombinations.stream()         // Преобразуем список GroupCombination в поток
                .filter(Objects::nonNull)        // Игнорируем null-комбинации
                .map(GroupCombination::getGroups) // Преобразуем каждую комбинацию в список групп
                .filter(Objects::nonNull)        // Игнорируем комбинации с null-списком групп
                .flatMap(List::stream)           // Объединяем все группы в один поток
                .collect(Collectors.toList());   // Собираем в список
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

    public List<AbstractAuditorium> getAuditorium() {
        return auditoriums;
    }

    public void addAuditorium(AbstractAuditorium auditorium) {
        this.auditoriums.add(auditorium);
    }

    @Override
    public String toString() {
        return "dis: " + discipline +
                ",kind: " + kindOfStudy.getAbbreviationName() +
                ", educ: " + educators +
                ", " + groupCombinations;
    }
}
