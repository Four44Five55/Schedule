package ru.services.educator.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import ru.entity.dictionary.ScienceBranch;
import ru.repository.EducatorRepository;
import ru.repository.ScienceBranchRepository;

import java.util.List;

/** Справочник отраслей науки: всё общее — в {@link EducatorDictionaryPort}. */
@Component
@RequiredArgsConstructor
public class ScienceBranchPort extends EducatorDictionaryPort<ScienceBranch> {

    private final ScienceBranchRepository scienceBranchRepository;
    private final EducatorRepository educatorRepository;

    @Override
    public EducatorDictionaryKind kind() {
        return EducatorDictionaryKind.SCIENCE_BRANCH;
    }

    @Override
    protected JpaRepository<ScienceBranch, Integer> repository() {
        return scienceBranchRepository;
    }

    @Override
    protected ScienceBranch newEntry() {
        return new ScienceBranch();
    }

    @Override
    protected List<ScienceBranch> findAllOrdered() {
        return scienceBranchRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Override
    public long countEducators(Integer id) {
        return educatorRepository.countByScienceBranchId(id);
    }
}
