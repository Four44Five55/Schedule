package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "feature")
@Getter
public class Feature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Setter
    @Column(unique = true, nullable = false)
    private String name;
    @Setter
    @Column(unique = true, nullable = false)
    private String code;
}