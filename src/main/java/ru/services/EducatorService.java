package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.educator.EducatorCreateDto;
import ru.dto.educator.EducatorDto;
import ru.dto.educator.EducatorUpdateDto;
import ru.entity.Educator;
import ru.mapper.EducatorMapper;
import ru.repository.EducatorRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для управления Преподавателями.
 */
@Service
@RequiredArgsConstructor
public class EducatorService {

    private final EducatorRepository educatorRepository;
    private final EducatorMapper educatorMapper;

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
        Educator educatorToUpdate = findEntityById(educatorId);

        educatorToUpdate.setName(updateDto.name());
        educatorToUpdate.setPreferredDays(updateDto.preferredDays());
        educatorToUpdate.setPreferredTimeSlots(updateDto.preferredTimeSlots());

        return educatorMapper.toDto(educatorRepository.save(educatorToUpdate));
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
            throw new EntityNotFoundException("Преподаватель с id=" + educatorId + " не найден.");
        }
        // TODO: Добавить проверку, не назначен ли преподаватель на какие-либо 'Assignment', перед удалением.
        educatorRepository.deleteById(educatorId);
    }


    // === СЛУЖЕБНЫЕ МЕТОДЫ (ДЛЯ ДРУГИх СЕРВИСОВ) ===

    /**
     * Находит сущность Educator по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public Educator findEntityById(Integer id) {
        return educatorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Преподаватель с id=" + id + " не найден."));
    }

    /**
     * Находит все сущности Educator по списку их ID.
     * Предназначен для использования AssignmentService.
     */
    @Transactional(readOnly = true)
    public List<Educator> findAllEntitiesByIds(List<Integer> ids) {
        List<Educator> educators = educatorRepository.findAllById(ids);
        if (educators.size() != ids.size()) {
            throw new EntityNotFoundException("Один или несколько преподавателей из списка ID не найдены.");
        }
        return educators;
    }
}
