package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.feature.FeatureCreateDto;
import ru.dto.feature.FeatureDto;
import ru.dto.feature.FeatureUpdateDto;
import ru.entity.Feature;
import ru.mapper.FeatureMapper;
import ru.repository.FeatureRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final FeatureMapper featureMapper;

    @Transactional
    public FeatureDto create(FeatureCreateDto createDto) {
        if (featureRepository.existsByNameOrCode(createDto.name(), createDto.code())) {
            throw new IllegalStateException("Оснащение с таким названием или кодом уже существует.");
        }
        Feature feature = new Feature();
        feature.setName(createDto.name());
        feature.setCode(createDto.code());
        return featureMapper.toDto(featureRepository.save(feature));
    }

    @Transactional
    public FeatureDto update(Integer id, FeatureUpdateDto updateDto) {
        Feature featureToUpdate = findEntityById(id);
        // TODO: Добавить более сложную проверку на уникальность при обновлении
        featureToUpdate.setName(updateDto.name());
        featureToUpdate.setCode(updateDto.code());
        return featureMapper.toDto(featureRepository.save(featureToUpdate));
    }

    @Transactional
    public void delete(Integer id) {
        if (!featureRepository.existsById(id)) {
            throw new EntityNotFoundException("Оснащение с id=" + id + " не найдено.");
        }
        // TODO: Добавить проверку, не используется ли оснащение в Auditorium или CurriculumSlot
        featureRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findAll() {
        return featureMapper.toDtoList(featureRepository.findAll());
    }

    @Transactional(readOnly = true)
    public Optional<FeatureDto> findById(Integer id) {
        return featureRepository.findById(id).map(featureMapper::toDto);
    }

    // --- СЛУЖЕБНЫЕ МЕТОДЫ ---

    @Transactional(readOnly = true)
    public Feature findEntityById(Integer id) {
        return featureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Оснащение с id=" + id + " не найдено."));
    }

    @Transactional(readOnly = true)
    public List<Feature> findAllEntitiesByIds(List<Integer> ids) {
        List<Feature> features = featureRepository.findAllById(ids);
        if (features.size() != ids.size()) {
            throw new EntityNotFoundException("Одно или несколько оснащений из списка ID не найдены.");
        }
        return features;
    }
}
