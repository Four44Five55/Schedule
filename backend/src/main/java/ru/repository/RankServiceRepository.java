package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.dictionary.RankService;

import java.util.List;

/** Справочник родов службы к специальному званию («юстиции», «внутренней службы»). */
@Repository
public interface RankServiceRepository extends JpaRepository<RankService, Integer> {

    List<RankService> findAllByOrderBySortOrderAscNameAsc();
}
