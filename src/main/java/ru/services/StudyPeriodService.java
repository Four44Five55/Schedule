package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.studyPeriod.StudyPeriodCreateDto;
import ru.dto.studyPeriod.StudyPeriodDto;
import ru.dto.studyPeriod.StudyPeriodUpdateDto;
import ru.entity.StudyPeriod;
import ru.mapper.StudyPeriodMapper;
import ru.repository.StudyPeriodRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyPeriodService {

    private final StudyPeriodRepository studyPeriodRepository;
    private final StudyPeriodMapper studyPeriodMapper;

    @Transactional
    public StudyPeriodDto createStudyPeriod(StudyPeriodCreateDto createDto) {
        if (createDto.startDate().isAfter(createDto.endDate())) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания.");
        }
        if (studyPeriodRepository.existsByStudyYearAndPeriodType(createDto.studyYear(), createDto.periodType())) {
            throw new IllegalStateException("Учебный период для года " + createDto.studyYear() + " и типа " + createDto.periodType() + " уже существует.");
        }

        StudyPeriod newPeriod = new StudyPeriod();
        newPeriod.setName(createDto.name());
        newPeriod.setStudyYear(createDto.studyYear());
        newPeriod.setPeriodType(createDto.periodType());
        newPeriod.setStartDate(createDto.startDate());
        newPeriod.setEndDate(createDto.endDate());

        return studyPeriodMapper.toDto(studyPeriodRepository.save(newPeriod));
    }

    @Transactional
    public StudyPeriodDto updateStudyPeriod(Integer id, StudyPeriodUpdateDto updateDto) {
        StudyPeriod periodToUpdate = findEntityById(id);

        if (updateDto.startDate().isAfter(updateDto.endDate())) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания.");
        }

        // Проверяем уникальность, если пара год/тип изменилась
        if (periodToUpdate.getStudyYear() != updateDto.studyYear() || periodToUpdate.getPeriodType() != updateDto.periodType()) {
            studyPeriodRepository.findByStudyYearAndPeriodType(updateDto.studyYear(), updateDto.periodType()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalStateException("Учебный период для года " + updateDto.studyYear() + " и типа " + updateDto.periodType() + " уже существует.");
                }
            });
        }

        periodToUpdate.setName(updateDto.name());
        periodToUpdate.setStudyYear(updateDto.studyYear());
        periodToUpdate.setPeriodType(updateDto.periodType());
        periodToUpdate.setStartDate(updateDto.startDate());
        periodToUpdate.setEndDate(updateDto.endDate());

        return studyPeriodMapper.toDto(studyPeriodRepository.save(periodToUpdate));
    }

    @Transactional(readOnly = true)
    public List<StudyPeriodDto> findAll() {
        return studyPeriodRepository.findAll().stream()
                .map(studyPeriodMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<StudyPeriodDto> findById(Integer id) {
        return studyPeriodRepository.findById(id).map(studyPeriodMapper::toDto);
    }

    @Transactional
    public void deleteStudyPeriod(Integer id) {
        if (!studyPeriodRepository.existsById(id)) {
            throw new EntityNotFoundException("Учебный период с id=" + id + " не найден.");
        }
        // TODO: Добавить проверку, не используется ли период в DisciplineCourse, перед удалением.
        studyPeriodRepository.deleteById(id);
    }

    // --- СЛУЖЕБНЫЙ МЕТОД ---

    @Transactional(readOnly = true)
    public StudyPeriod findEntityById(Integer id) {
        return studyPeriodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Учебный период с id=" + id + " не найден."));
    }
}
