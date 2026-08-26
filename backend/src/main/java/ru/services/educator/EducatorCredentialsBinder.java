package ru.services.educator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.entity.Educator;
import ru.enums.AcademicDegree;
import ru.enums.AcademicTitle;
import ru.repository.RankServiceRepository;
import ru.repository.ScienceBranchRepository;
import ru.repository.SpecialRankRepository;
import ru.exceptions.NotFoundException;

/**
 * Проставляет регалии на преподавателя, разрешая id справочников в сущности.
 *
 * <p>Отдельный компонент, а не пять полей и десяток строк в {@code EducatorService}: тот и так
 * отвечает за жизненный цикл преподавателя, а «разрешить ссылки на справочники» — своя маленькая
 * ответственность, одинаковая при создании и правке. Заодно {@code EducatorService} не обзаводится
 * тремя репозиториями, которые ему больше нигде не нужны.</p>
 *
 * <p><b>Никаких проверок полноты:</b> любое поле может быть {@code null} — и это не «неполные
 * данные», а законное «не указано». {@code null} при правке означает «снять значение».</p>
 *
 * <p>Связность звания и рода службы (служба без звания бессмысленна) здесь <b>не</b> запрещается:
 * это правило показа, и живёт оно в форматтере, который просто не печатает службу без звания.
 * Запрет на вводе заблокировал бы промежуточное состояние формы без всякой пользы.</p>
 */
@Component
@RequiredArgsConstructor
public class EducatorCredentialsBinder {

    private final SpecialRankRepository specialRankRepository;
    private final RankServiceRepository rankServiceRepository;
    private final ScienceBranchRepository scienceBranchRepository;

    public void bind(Educator educator,
                     Integer specialRankId,
                     Integer rankServiceId,
                     AcademicDegree academicDegree,
                     Integer scienceBranchId,
                     AcademicTitle academicTitle) {

        educator.setSpecialRank(specialRankId == null ? null
                : specialRankRepository.findById(specialRankId)
                .orElseThrow(() -> notFound("специальное звание", specialRankId)));

        educator.setRankService(rankServiceId == null ? null
                : rankServiceRepository.findById(rankServiceId)
                .orElseThrow(() -> notFound("род службы", rankServiceId)));

        educator.setScienceBranch(scienceBranchId == null ? null
                : scienceBranchRepository.findById(scienceBranchId)
                .orElseThrow(() -> notFound("отрасль науки", scienceBranchId)));

        educator.setAcademicDegree(academicDegree);
        educator.setAcademicTitle(academicTitle);
    }

    private NotFoundException notFound(String what, Integer id) {
        return new NotFoundException("Значение справочника «" + what + "» с id=" + id + " не найдено.");
    }
}
