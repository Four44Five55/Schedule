package ru.services.solver.genetic;

import org.springframework.stereotype.Component;

/**
 * Конфигурация весов и параметров генетического алгоритма.
 */
@Component
public class SolverConfig {

    // --- Параметры алгоритма ---
    public int getPopulationSize() {
        return 100;
    }

    public int getMaxGenerations() {
        return 1000;
    }

    // --- Штрафы (Hard Constraints) ---
    // Штрафы должны быть отрицательными и большими

    public double getHardConstraintPenalty() {
        return -100.0;
    }

    // Если занятие попало на "заблокированный" слот (отпуск, выходной)
    public double getStaticConstraintPenalty() {
        return -1000.0;
    }

    // Если не удалось подобрать аудиторию
    public double getMissingAuditoriumPenalty() {
        return -50.0;
    }

    // --- Бонусы/Штрафы (Soft Constraints - пока заглушки) ---
    public double getWindowPenalty() {
        return -1.0;
    }

    /**
     * Штраф за нарушение хронологии.
     * Например, если Лекция 2 стоит раньше Лекции 1.
     * Это Мягкое ограничение (Soft Constraint), поэтому штраф меньше, чем за накладки (-100).
     */
    public double getOrderConstraintPenalty() {
        return -10.0;
    }
}
