package ru.repository.constraints;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.entity.constraints.AuditoriumConstraint;

@Repository
public interface AuditoriumConstraintRepository extends JpaRepository<AuditoriumConstraint, Integer> {

    /** Сколько ограничений размечено этим видом — цена удаления строки справочника. */
    long countByKindOfConstraint_Code(String code);

    /** Использования всех видов сразу — чтобы справочник не делал COUNT на каждую строку. */
    @Query("SELECT c.kindOfConstraint.code AS code, COUNT(c) AS count FROM AuditoriumConstraint c GROUP BY c.kindOfConstraint.code")
    java.util.List<KindUsageCount> countGroupedByKind();
}
