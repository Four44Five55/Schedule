package ru.entity.logicSchema;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Auditorium;

import java.util.HashSet;
import java.util.Set;

/**
 * Сущность, представляющая "пул" или группу аудиторий.
 * Например, "Большие лекционные", "Компьютерные классы" и т.д.
 */
@Entity
@Table(name = "auditorium_pool")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriumPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название пула, должно быть уникальным.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Описание назначения пула.
     */
    @Column(name = "description")
    private String description;

    /**
     * Связь "многие-ко-многим" с аудиториями.
     * Определяет, какие конкретные аудитории входят в этот пул.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "auditorium_pool_mapping", // Название связующей таблицы
            joinColumns = @JoinColumn(name = "pool_id"), // Внешний ключ на эту сущность (AuditoriumPool)
            inverseJoinColumns = @JoinColumn(name = "auditorium_id") // Внешний ключ на связанную сущность (Auditorium)
    )
    private Set<Auditorium> auditoriums = new HashSet<>();

    public AuditoriumPool(String name, String description) {
        this.name = name;
        this.description = description;
    }


    public void addAuditorium(Auditorium auditorium) {
        this.auditoriums.add(auditorium);
        auditorium.getPools().add(this);
    }

    public void removeAuditorium(Auditorium auditorium) {
        this.auditoriums.remove(auditorium);
        auditorium.getPools().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditoriumPool that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
