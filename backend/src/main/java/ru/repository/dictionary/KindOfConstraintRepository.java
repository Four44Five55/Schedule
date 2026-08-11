package ru.repository.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.entity.dictionary.KindOfConstraint;

import java.util.List;
import java.util.Optional;

/** Справочник видов ограничений. Ключ строковый — см. {@link KindOfConstraint}. */
public interface KindOfConstraintRepository extends JpaRepository<KindOfConstraint, String> {

    List<KindOfConstraint> findAllByOrderBySortOrderAscNameAsc();

    Optional<KindOfConstraint> findByNameIgnoreCase(String name);

    Optional<KindOfConstraint> findByShortNameIgnoreCase(String shortName);
}
