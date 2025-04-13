package ru.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GroupCombination {
    private List<Group> groups = new ArrayList<>();

    public GroupCombination() {
    }

    public GroupCombination(List<Group> groups) {
        this.groups = groups;
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

