package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.studyStream.StudyStreamCreateDto;
import ru.dto.studyStream.StudyStreamDto;
import ru.dto.studyStream.StudyStreamUpdateDto;
import ru.entity.Group;
import ru.entity.logicSchema.StudyStream;
import ru.mapper.StudyStreamMapper;
import ru.repository.StudyStreamRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для управления Учебными потоками (StudyStream).
 */
@Service
@RequiredArgsConstructor
public class StudyStreamService {

    private final StudyStreamRepository studyStreamRepository;
    private final GroupService groupService;
    private final StudyStreamMapper studyStreamMapper;

    /**
     * Создает новый учебный поток.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданного потока.
     */
    @Transactional
    public StudyStreamDto createStudyStream(StudyStreamCreateDto createDto) {
        if (studyStreamRepository.existsByName(createDto.name())) {
            throw new IllegalStateException("Поток с названием '" + createDto.name() + "' уже существует.");
        }

        // Используем GroupService для получения сущностей
        List<Group> groups = groupService.findAllEntitiesByIds(createDto.groupIds());

        StudyStream newStream = new StudyStream();
        newStream.setName(createDto.name());
        newStream.setSemester(createDto.semester());
        newStream.setGroups(new HashSet<>(groups));

        StudyStream savedStream = studyStreamRepository.save(newStream);
        return studyStreamMapper.toDto(savedStream);
    }

    /**
     * Обновляет существующий учебный поток.
     *
     * @param streamId  ID обновляемого потока.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленного потока.
     */
    @Transactional
    public StudyStreamDto updateStudyStream(Integer streamId, StudyStreamUpdateDto updateDto) {
        StudyStream streamToUpdate = studyStreamRepository.findById(streamId)
                .orElseThrow(() -> new EntityNotFoundException("Поток с id=" + streamId + " не найден."));

        // Используем GroupService для получения сущностей
        List<Group> newGroups = groupService.findAllEntitiesByIds(updateDto.groupIds());

        streamToUpdate.setName(updateDto.name());
        streamToUpdate.setSemester(updateDto.semester());
        streamToUpdate.setGroups(new HashSet<>(newGroups));

        StudyStream updatedStream = studyStreamRepository.save(streamToUpdate);
        return studyStreamMapper.toDto(updatedStream);
    }

    @Transactional(readOnly = true)
    public Optional<StudyStreamDto> findById(Integer streamId) {
        // Здесь можно использовать @EntityGraph в репозитории для жадной загрузки
        return studyStreamRepository.findById(streamId).map(studyStreamMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<StudyStreamDto> findAll() {
        // Примечание: этот метод вызовет N+1 запросов для загрузки групп.
        // Для оптимизации нужно будет создать метод с @EntityGraph.
        return studyStreamRepository.findAll().stream()
                .map(studyStreamMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteStudyStream(Integer streamId) {
        if (!studyStreamRepository.existsById(streamId)) {
            throw new EntityNotFoundException("Поток с id=" + streamId + " не найден.");
        }
        studyStreamRepository.deleteById(streamId);
    }

    // --- СЛУЖЕБНЫЙ МЕТОД ---

    @Transactional(readOnly = true)
    public StudyStream findEntityById(Integer id) {
        return studyStreamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Поток с id=" + id + " не найден."));
    }
}
