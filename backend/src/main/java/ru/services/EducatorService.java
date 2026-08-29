package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.educator.EducatorCreateDto;
import ru.dto.educator.EducatorDto;
import ru.dto.educator.EducatorUpdateDto;
import ru.entity.Educator;
import ru.entity.OrgUnit;
import ru.mapper.EducatorMapper;
import ru.repository.EducatorRepository;
import ru.services.educator.EducatorCredentialsBinder;
import ru.services.orgunit.OrgUnitService;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import ru.exceptions.NotFoundException;

/**
 * Сервис для управления Преподавателями.
 */
@Service
@RequiredArgsConstructor
public class EducatorService {

    private final EducatorRepository educatorRepository;
    private final EducatorMapper educatorMapper;
    private final ProjectionMaintenance projectionMaintenance;
    private final OrgUnitService orgUnitService;
    private final EducatorCredentialsBinder credentialsBinder;

    // === ПУБЛИЧНЫЕ МЕТОДЫ (ДЛЯ API) ===

    /**
     * Создает нового преподавателя.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданного преподавателя.
     */
    @Transactional
    public EducatorDto createEducator(EducatorCreateDto createDto) {
        Educator newEducator = new Educator();
        newEducator.setName(createDto.name());
        newEducator.setPreferredDays(createDto.preferredDays());
        newEducator.setPreferredTimeSlots(createDto.preferredTimeSlots());
        newEducator.setCompactSchedule(createDto.compactSchedule());
        newEducator.setOrgUnit(resolveOrgUnit(createDto.orgUnitId()));
        credentialsBinder.bind(newEducator,
                createDto.specialRankId(), createDto.rankServiceId(),
                createDto.academicDegree(), createDto.scienceBranchId(), createDto.academicTitle());

        return educatorMapper.toDto(educatorRepository.save(newEducator));
    }

    /**
     * Обновляет данные существующего преподавателя.
     *
     * @param educatorId ID обновляемого преподавателя.
     * @param updateDto  DTO с новыми данными.
     * @return DTO обновленного преподавателя.
     */
    @Transactional
    public EducatorDto updateEducator(Integer educatorId, EducatorUpdateDto updateDto) {
        Educator educatorToUpdate = getEntityById(educatorId);

        educatorToUpdate.setName(updateDto.name());
        educatorToUpdate.setPreferredDays(updateDto.preferredDays());
        educatorToUpdate.setPreferredTimeSlots(updateDto.preferredTimeSlots());
        educatorToUpdate.setCompactSchedule(updateDto.compactSchedule());
        // null — открепить: смена подразделения read-модели не касается (его нет в schedule_view).
        educatorToUpdate.setOrgUnit(resolveOrgUnit(updateDto.orgUnitId()));
        // Регалии в проекцию тоже не денормализованы (см. CQRS_ARCHITECTURE, «Что в проекцию НЕ
        // кладут»): присвоение звания перепроекции не требует, подпись собирается на чтении.
        credentialsBinder.bind(educatorToUpdate,
                updateDto.specialRankId(), updateDto.rankServiceId(),
                updateDto.academicDegree(), updateDto.scienceBranchId(), updateDto.academicTitle());

        EducatorDto updated = educatorMapper.toDto(educatorRepository.save(educatorToUpdate));
        // Имя преподавателя лежит в read-модели снимком: без этого переименование не дошло бы
        // до сетки, отчётов и Excel — там остался бы прежний.
        projectionMaintenance.announce(ProjectionSource.EDUCATOR, educatorId);
        return updated;
    }

    /**
     * Находит преподавателя по ID.
     */
    @Transactional(readOnly = true)
    public Optional<EducatorDto> findById(Integer educatorId) {
        return educatorRepository.findById(educatorId).map(educatorMapper::toDto);
    }

    /**
     * Возвращает список всех преподавателей.
     */
    @Transactional(readOnly = true)
    public List<EducatorDto> findAll() {
        return educatorRepository.findAll().stream()
                .map(educatorMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Удаляет преподавателя по ID.
     */
    @Transactional
    public void deleteEducator(Integer educatorId) {
        if (!educatorRepository.existsById(educatorId)) {
            throw new NotFoundException("Преподаватель с id=" + educatorId + " не найден.");
        }
        // Цена удаления не называется заранее (общий долг CRUD-слоя, см. FOLLOWUPS: «Удаление
        // без предупреждения»). Образец решения — delete-impact у аудитории и назначения.
        educatorRepository.deleteById(educatorId);
    }


    // === СЛУЖЕБНЫЕ МЕТОДЫ (ДЛЯ ДРУГИх СЕРВИСОВ) ===

    /**
     * Находит сущность Educator по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public Educator getEntityById(Integer id) {
        return educatorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Преподаватель с id=" + id + " не найден."));
    }

    /**
     * Находит все сущности Educator по списку их ID.
     * Предназначен для использования AssignmentService.
     */
    @Transactional(readOnly = true)
    public List<Educator> getAllEntitiesByIds(List<Integer> ids) {
        List<Educator> educators = educatorRepository.findAllById(ids);
        if (educators.size() != ids.size()) {
            throw new NotFoundException("Один или несколько преподавателей из списка ID не найдены.");
        }
        return educators;
    }

    @Transactional(readOnly = true)
    public List<Educator> getAllEntities() {
        return educatorRepository.findAll();
    }

    /**
     * Подразделение по id; {@code null} — преподаватель не привязан ни к одному.
     */
    private OrgUnit resolveOrgUnit(Integer orgUnitId) {
        return orgUnitId == null ? null : orgUnitService.getEntityById(orgUnitId);
    }
}
