package ru.entity.logicSchema;

import jakarta.persistence.*;
import ru.entity.Group;

import java.util.HashSet;
import java.util.Set;

@Entity
public class StudyStream {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany
    @JoinTable(
            name = "stream_groups",
            joinColumns = @JoinColumn(name = "stream_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    private Set<Group> groups = new HashSet<>();
}