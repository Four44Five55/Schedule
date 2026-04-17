package ru.services.solver.model;

import lombok.Getter;
import lombok.Setter;
import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Lesson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ген (Gene) — атомарная единица планирования в генетическом алгоритме.
 *
 * <p>Представляет собой группу занятий, которые ОБЯЗАНЫ стоять в одном и том же
 * временном слоте (например, параллельные подгруппы или потоковая лекция).
 * Алгоритм перемещает весь Ген целиком, что гарантирует целостность параллельных занятий.</p>
 */
@Getter
@Setter
public class Gene {

    /**
     * Уникальный ID гена (для отладки и equals).
     */
    private final String id;

    /**
     * Список занятий, входящих в этот ген.
     * Они всегда двигаются вместе.
     */
    private final List<Lesson> lessons;

    /**
     * Текущий назначенный временной слот.
     * Может быть null, если ген еще не размещен.
     */
    private CellForLesson assignedSlot;

    /**
     * Список назначенных аудиторий.
     * Позиция в списке соответствует позиции урока в списке lessons (или логике распределения).
     */
    private List<Auditorium> assignedAuditoriums;

    /**
     * Флаг "Закреплено".
     * Если true, мутация не имеет права трогать этот ген.
     */
    private boolean pinned = false;

    // --- Конструкторы ---

    public Gene(List<Lesson> lessons) {
        this.id = UUID.randomUUID().toString();
        this.lessons = new ArrayList<>(lessons); // Защитная копия
        this.assignedAuditoriums = new ArrayList<>();
    }

    /**
     * Конструктор копирования (для клонирования Генома).
     * Важно: сами объекты Lesson не клонируем (они ссылочные),
     * но состояние (slot, auditoriums) копируем.
     */
    public Gene(Gene other) {
        this.id = other.id;
        this.lessons = new ArrayList<>(other.lessons); // Ссылки на те же уроки
        this.assignedSlot = other.assignedSlot; // Ссылка на тот же слот (Cell immutable/cached)
        this.assignedAuditoriums = new ArrayList<>(other.assignedAuditoriums); // Копия списка
        this.pinned = other.pinned;
    }

    /**
     * Создает глубокую копию гена.
     */
    public Gene copy() {
        return new Gene(this);
    }

    @Override
    public String toString() {
        return "Gene{" +
                "slot=" + assignedSlot +
                ", lessonsCount=" + lessons.size() +
                '}';
    }
}