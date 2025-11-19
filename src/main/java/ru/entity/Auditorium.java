package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.logicSchema.AuditoriumPool;
import ru.inter.IMaterialEntity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "auditorium")
@Getter
@Setter
@NoArgsConstructor
public class Auditorium implements IMaterialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id")
    private Building building;

    @ManyToMany(mappedBy = "auditoriums")
    private Set<AuditoriumPool> pools = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Auditorium that = (Auditorium) o;
        return capacity == that.capacity && Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(building, that.building) && Objects.equals(pools, that.pools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, capacity, building, pools);
    }

}
