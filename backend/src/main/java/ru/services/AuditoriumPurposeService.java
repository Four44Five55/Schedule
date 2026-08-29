package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.dto.auditoriumPurpose.AuditoriumPurposeCreateDto;
import ru.dto.auditoriumPurpose.AuditoriumPurposeDto;
import ru.dto.auditoriumPurpose.AuditoriumPurposeUpdateDto;
import ru.entity.AuditoriumPurpose;
import ru.mapper.AuditoriumPurposeMapper;
import ru.repository.AuditoriumPurposeRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditoriumPurposeService {

    private final AuditoriumPurposeRepository auditoriumPurposeRepository;
    private final AuditoriumPurposeMapper auditoriumPurposeMapper;

    @Transactional
    public AuditoriumPurposeDto create(AuditoriumPurposeCreateDto createDto) {
        if (auditoriumPurposeRepository.existsByName(createDto.name())) {
            throw new DuplicateException("Назначение аудитории с названием '" + createDto.name() + "' уже существует.");
        }
        AuditoriumPurpose purpose = new AuditoriumPurpose();
        purpose.setName(createDto.name());
        return auditoriumPurposeMapper.toDto(auditoriumPurposeRepository.save(purpose));
    }

    @Transactional
    public AuditoriumPurposeDto update(Integer id, AuditoriumPurposeUpdateDto updateDto) {
        AuditoriumPurpose purposeToUpdate = getEntityById(id);
        if (auditoriumPurposeRepository.existsByName(updateDto.name())) {
            throw new DuplicateException("Назначение аудитории с названием '" + updateDto.name() + "' уже существует.");
        }
        purposeToUpdate.setName(updateDto.name());
        return auditoriumPurposeMapper.toDto(auditoriumPurposeRepository.save(purposeToUpdate));
    }

    @Transactional
    public void delete(Integer id) {
        if (!auditoriumPurposeRepository.existsById(id)) {
            throw new NotFoundException("Назначение аудитории с id=" + id + " не найдено.");
        }
        // Цена удаления не называется заранее (общий долг CRUD-слоя, см. FOLLOWUPS: «Удаление
        // без предупреждения»). Образец решения — delete-impact у аудитории и назначения.
        auditoriumPurposeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AuditoriumPurposeDto> findAll() {
        return auditoriumPurposeMapper.toDtoList(auditoriumPurposeRepository.findAll());
    }

    @Transactional(readOnly = true)
    public Optional<AuditoriumPurposeDto> findById(Integer id) {
        return auditoriumPurposeRepository.findById(id).map(auditoriumPurposeMapper::toDto);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===
    @Transactional(readOnly = true)
    public AuditoriumPurpose getEntityById(Integer id) {
        return auditoriumPurposeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Назначение аудитории с id=" + id + " не найдено."));
    }
}
