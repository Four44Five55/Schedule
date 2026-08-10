package ru.services.educator.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import ru.entity.dictionary.SpecialRank;
import ru.repository.EducatorRepository;
import ru.repository.SpecialRankRepository;

import java.util.List;

/** Справочник специальных званий: всё общее — в {@link EducatorDictionaryPort}. */
@Component
@RequiredArgsConstructor
public class SpecialRankPort extends EducatorDictionaryPort<SpecialRank> {

    private final SpecialRankRepository specialRankRepository;
    private final EducatorRepository educatorRepository;

    @Override
    public EducatorDictionaryKind kind() {
        return EducatorDictionaryKind.SPECIAL_RANK;
    }

    @Override
    protected JpaRepository<SpecialRank, Integer> repository() {
        return specialRankRepository;
    }

    @Override
    protected SpecialRank newEntry() {
        return new SpecialRank();
    }

    @Override
    protected List<SpecialRank> findAllOrdered() {
        return specialRankRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Override
    public long countEducators(Integer id) {
        return educatorRepository.countBySpecialRankId(id);
    }
}
