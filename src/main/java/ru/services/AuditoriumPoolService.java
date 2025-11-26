package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.auditoriumPool.AuditoriumPoolCreateDto;
import ru.dto.auditoriumPool.AuditoriumPoolDto;
import ru.dto.auditoriumPool.AuditoriumPoolUpdateDto;
import ru.entity.Auditorium;
import ru.entity.logicSchema.AuditoriumPool;
import ru.mapper.AuditoriumPoolMapper;
import ru.repository.AuditoriumPoolRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditoriumPoolService {

    private final AuditoriumPoolRepository auditoriumPoolRepository;
    private final AuditoriumService auditoriumService;
    private final AuditoriumPoolMapper auditoriumPoolMapper;

    @Transactional
    public AuditoriumPoolDto createPool(AuditoriumPoolCreateDto createDto) {
        if (auditoriumPoolRepository.existsByName(createDto.name())) {
            throw new IllegalStateException("Пул аудиторий с названием '" + createDto.name() + "' уже существует.");
        }

        AuditoriumPool newPool = new AuditoriumPool();
        newPool.setName(createDto.name());
        newPool.setDescription(createDto.description());

        if (createDto.auditoriumIds() != null && !createDto.auditoriumIds().isEmpty()) {
            List<Auditorium> auditoriums = auditoriumService.findAllEntitiesByIds(createDto.auditoriumIds());
            newPool.setAuditoriums(new HashSet<>(auditoriums));
        }

        return auditoriumPoolMapper.toDto(auditoriumPoolRepository.save(newPool));
    }

    @Transactional
    public AuditoriumPoolDto updatePool(Integer poolId, AuditoriumPoolUpdateDto updateDto) {
        AuditoriumPool poolToUpdate = auditoriumPoolRepository.findById(poolId)
                .orElseThrow(() -> new EntityNotFoundException("Пул аудиторий с id=" + poolId + " не найден."));

        poolToUpdate.setName(updateDto.name());
        poolToUpdate.setDescription(updateDto.description());

        // Полностью заменяем состав аудиторий в пуле
        poolToUpdate.getAuditoriums().clear();
        if (updateDto.auditoriumIds() != null && !updateDto.auditoriumIds().isEmpty()) {
            List<Auditorium> newAuditoriums = auditoriumService.findAllEntitiesByIds(updateDto.auditoriumIds());
            poolToUpdate.setAuditoriums(new HashSet<>(newAuditoriums));
        }

        return auditoriumPoolMapper.toDto(auditoriumPoolRepository.save(poolToUpdate));
    }

    @Transactional(readOnly = true)
    public Optional<AuditoriumPoolDto> findById(Integer poolId) {
        return auditoriumPoolRepository.findWithAuditoriumsById(poolId).map(auditoriumPoolMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<AuditoriumPoolDto> findAll() {
        return auditoriumPoolRepository.findAll().stream()
                .map(auditoriumPoolMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletePool(Integer poolId) {
        if (!auditoriumPoolRepository.existsById(poolId)) {
            throw new EntityNotFoundException("Пул аудиторий с id=" + poolId + " не найден.");
        }
        // TODO: Добавить проверку, не используется ли пул в CurriculumSlot, перед удалением.
        auditoriumPoolRepository.deleteById(poolId);
    }

    // --- Служебный метод ---
    @Transactional(readOnly = true)
    public AuditoriumPool findEntityById(Integer id) {
        return auditoriumPoolRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Пул аудиторий с id=" + id + " не найден."));
    }
}