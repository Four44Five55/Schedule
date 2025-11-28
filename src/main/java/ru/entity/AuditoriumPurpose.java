package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auditorium_purpose")
@Getter
public class AuditoriumPurpose {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Setter
    @Column(unique = true, nullable = false)
    private String name;
}
