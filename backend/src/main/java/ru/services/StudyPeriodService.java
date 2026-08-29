package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.exceptions.RuleViolationException;
import ru.dto.studyPeriod.PeriodSignatureDto;
import ru.dto.studyPeriod.StudyPeriodCreateDto;
import ru.dto.studyPeriod.StudyPeriodDto;
import ru.dto.studyPeriod.StudyPeriodUpdateDto;
import ru.entity.StudyPeriod;
import ru.mapper.StudyPeriodMapper;
import ru.repository.StudyPeriodRepository;

import java.time.LocalDate;
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
            throw new RuleViolationException("Дата начала не может быть позже даты окончания.");
        }
        if (studyPeriodRepository.existsByStudyYearAndPeriodType(createDto.studyYear(), createDto.periodType())) {
            throw new DuplicateException("Учебный период для года " + createDto.studyYear() + " и типа " + createDto.periodType() + " уже существует.");
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
        StudyPeriod periodToUpdate = getEntityById(id);

        if (updateDto.startDate().isAfter(updateDto.endDate())) {
            throw new RuleViolationException("Дата начала не может быть позже даты окончания.");
        }

        // Проверяем уникальность, если пара год/тип изменилась
        if (periodToUpdate.getStudyYear() != updateDto.studyYear() || periodToUpdate.getPeriodType() != updateDto.periodType()) {
            studyPeriodRepository.findByStudyYearAndPeriodType(updateDto.studyYear(), updateDto.periodType()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new DuplicateException("Учебный период для года " + updateDto.studyYear() + " и типа " + updateDto.periodType() + " уже существует.");
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

    /**
     * Сохраняет подпись под расписанием периода (должность, регалии, ФИО подписанта).
     *
     * <p>Отдельная операция, а не поля в {@link #updateStudyPeriod}: подпись правят перед выгрузкой,
     * а не при заведении периода, и требовать ради неё даты и тип периода незачем.</p>
     *
     * <p>Пустые строки приводятся к {@code null}: «не заполнено» и «заполнено пустым» — одно и то же
     * состояние, и бланк не должен различать их при отрисовке блока.</p>
     */
    @Transactional
    public StudyPeriodDto updateSignature(Integer id, PeriodSignatureDto dto) {
        StudyPeriod period = getEntityById(id);
        period.setSignerPosition(trimToNull(dto.signerPosition()));
        period.setSignerCredentials(trimToNull(dto.signerCredentials()));
        period.setSignerName(trimToNull(dto.signerName()));
        return studyPeriodMapper.toDto(studyPeriodRepository.save(period));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    /**
     * Получить активный учебный период (содержит сегодняшнюю дату).
     *
     * @return Optional с активным периодом или пустой, если активного периода нет
     */
    @Transactional(readOnly = true)
    public Optional<StudyPeriodDto> findActivePeriod() {
        return studyPeriodRepository.findActivePeriod(LocalDate.now())
                .map(studyPeriodMapper::toDto);
    }

    @Transactional
    public void deleteStudyPeriod(Integer id) {
        if (!studyPeriodRepository.existsById(id)) {
            throw new NotFoundException("Учебный период с id=" + id + " не найден.");
        }
        // Цена удаления не называется заранее (общий долг CRUD-слоя, см. FOLLOWUPS: «Удаление
        // без предупреждения»). Образец решения — delete-impact у аудитории и назначения.
        studyPeriodRepository.deleteById(id);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    @Transactional(readOnly = true)
    public StudyPeriod getEntityById(Integer id) {
        return studyPeriodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Учебный период с id=" + id + " не найден."));
    }
}
