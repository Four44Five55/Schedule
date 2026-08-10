package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.dictionary.ScienceBranch;

import java.util.List;

/** Справочник отраслей науки для учёной степени («технические» → к.т.н.). */
@Repository
public interface ScienceBranchRepository extends JpaRepository<ScienceBranch, Integer> {

    List<ScienceBranch> findAllByOrderBySortOrderAscNameAsc();
}
