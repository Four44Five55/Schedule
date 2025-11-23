package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Location;

@Repository
public interface LocationRepository extends JpaRepository<Location, Integer> {
}
