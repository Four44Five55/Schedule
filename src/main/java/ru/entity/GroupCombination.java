package ru.entity;

import ru.abstracts.AbstractAuditorium;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GroupCombination {
    private List<Group> groups = new ArrayList<>();
    private AbstractAuditorium auditorium;

    public GroupCombination() {
    }

    public GroupCombination(List<Group> groups) {
        this.groups = groups;
        this.auditorium = calculateCapacityAuditorium(this.getAllAuditoriums());
    }

    public GroupCombination(Group group) {
        this.groups.add(group);
    }

    public void add(Group group) {
        this.groups.add(group);
    }

    public void delete(Group group) {
        this.groups.remove(group);
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public AbstractAuditorium getAuditorium() {
        return auditorium;
    }

    /**
     * Возвращает список всех аудиторий, связанных с группами в этой комбинации.
     * Если у группы нет аудитории (auditorium == null), она не включается в результат.
     *
     * @return Список аудиторий (без дубликатов)
     */
    public List<AbstractAuditorium> getAllAuditoriums() {
        return groups.stream()
                .map(Group::getAuditorium)  // Получаем аудиторию из каждой группы
                .filter(Objects::nonNull)   // Исключаем null (если аудитория не назначена)
                .distinct()                 // Убираем дубликаты
                .collect(Collectors.toList());
    }

    private AbstractAuditorium calculateCapacityAuditorium(List<AbstractAuditorium> availableAuditoriums) {
        if (availableAuditoriums == null || availableAuditoriums.isEmpty()) {
            return null;
        }
        // Суммарный размер всех групп в комбинации
        int totalStudents = groups.stream()
                .mapToInt(Group::getSize)
                .sum();

        return availableAuditoriums.stream()
                .filter(auditorium -> auditorium.getCapacity() >= totalStudents)
                .min(Comparator.comparingInt(AbstractAuditorium::getCapacity)) // самая маленькая подходящая
                .orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupCombination that = (GroupCombination) o;
        return Objects.equals(groups, that.groups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groups);
    }

    @Override
    public String toString() {
        return "GroupComb: " +
                groups;
    }
}

