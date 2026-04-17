package ru.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Feature;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, Integer> {
    boolean existsByNameOrCode(String name, String code);
}
