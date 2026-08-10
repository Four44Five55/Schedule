package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.dictionary.SpecialRank;

import java.util.List;

/** Справочник специальных (воинских) званий. Порядок — по старшинству ({@code sortOrder}). */
@Repository
public interface SpecialRankRepository extends JpaRepository<SpecialRank, Integer> {

    List<SpecialRank> findAllByOrderBySortOrderAscNameAsc();
}
