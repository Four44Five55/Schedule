package ru.services.educator.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import ru.entity.dictionary.RankService;
import ru.repository.EducatorRepository;
import ru.repository.RankServiceRepository;

import java.util.List;

/** Справочник родов службы: всё общее — в {@link EducatorDictionaryPort}. */
@Component
@RequiredArgsConstructor
public class RankServicePort extends EducatorDictionaryPort<RankService> {

    private final RankServiceRepository rankServiceRepository;
    private final EducatorRepository educatorRepository;

    @Override
    public EducatorDictionaryKind kind() {
        return EducatorDictionaryKind.RANK_SERVICE;
    }

    @Override
    protected JpaRepository<RankService, Integer> repository() {
        return rankServiceRepository;
    }

    @Override
    protected RankService newEntry() {
        return new RankService();
    }

    @Override
    protected List<RankService> findAllOrdered() {
        return rankServiceRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Override
    public long countEducators(Integer id) {
        return educatorRepository.countByRankServiceId(id);
    }
}
