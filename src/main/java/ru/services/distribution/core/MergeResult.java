package ru.services.distribution.core;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс для хранения результатов объединения расписаний
 */
public class MergeResult {
    private final List<SuccessfulMerge> successfulMerges = new ArrayList<>();
    private final List<MergeConflict> conflicts = new ArrayList<>();
    private int totalLessons = 0;
    private int addedLessons = 0;

    public void addSuccessfulMerge(CellForLesson cell, AbstractLesson lesson) {
        successfulMerges.add(new SuccessfulMerge(cell, lesson));
    }

    public void addConflict(CellForLesson cell, AbstractLesson lesson, String reason) {
        conflicts.add(new MergeConflict(cell, lesson, reason));
    }

    public List<SuccessfulMerge> getSuccessfulMerges() {
        return successfulMerges;
    }

    public List<MergeConflict> getConflicts() {
        return conflicts;
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public int getSuccessCount() {
        return successfulMerges.size();
    }

    public int getConflictCount() {
        return conflicts.size();
    }

    public void setTotalLessons(int total) {
        this.totalLessons = total;
    }

    public void setAddedLessons(int added) {
        this.addedLessons = added;
    }

    public int getTotalLessons() {
        return totalLessons;
    }

    public int getAddedLessons() {
        return addedLessons;
    }

    /**
     * Информация об успешно добавленном занятии
     */
    public record SuccessfulMerge(CellForLesson cell, AbstractLesson lesson) {}

    /**
     * Информация о конфликте при добавлении занятия
     */
    public record MergeConflict(CellForLesson cell, AbstractLesson lesson, String reason) {}
} 