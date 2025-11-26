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
import ru.repository.GroupRepository;
import ru.repository.StudyStreamRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления Учебными потоками (StudyStream).
 */
@Service
@RequiredArgsConstructor
public class StudyStreamService {

    private final StudyStreamRepository studyStreamRepository;
    private final GroupRepository groupRepository; // Нужен для поиска групп по ID
    private final StudyStreamMapper studyStreamMapper;

    /**
     * Создает новый учебный поток.
     *
     * @param createDto DTO с данными для создания.
     * @return DTO созданного потока.
     */
    @Transactional
    public StudyStreamDto createStudyStream(StudyStreamCreateDto createDto) {
        // Проверяем, не занято ли имя
        if (studyStreamRepository.existsByName(createDto.name())) {
            throw new IllegalStateException("Поток с названием '" + createDto.name() + "' уже существует.");
        }

        // Находим все сущности групп по их ID
        List<Group> groups = groupRepository.findAllById(createDto.groupIds());
        if (groups.size() != createDto.groupIds().size()) {
            throw new EntityNotFoundException("Одна или несколько групп из списка ID не найдены.");
        }

        // Создаем и наполняем новую сущность
        StudyStream newStream = new StudyStream();
        newStream.setName(createDto.name());
        newStream.setSemester(createDto.semester());
        newStream.setGroups(new HashSet<>(groups));

        StudyStream savedStream = studyStreamRepository.save(newStream);
        return studyStreamMapper.toDto(savedStream);
    }

    /**
     * Обновляет существующий учебный поток.
     * Позволяет менять название, семестр и состав групп.
     *
     * @param streamId  ID обновляемого потока.
     * @param updateDto DTO с новыми данными.
     * @return DTO обновленного потока.
     */
    @Transactional
    public StudyStreamDto updateStudyStream(Integer streamId, StudyStreamUpdateDto updateDto) {
        StudyStream streamToUpdate = studyStreamRepository.findById(streamId)
                .orElseThrow(() -> new EntityNotFoundException("Поток с id=" + streamId + " не найден."));

        // Находим новые группы для потока
        List<Group> newGroups = groupRepository.findAllById(updateDto.groupIds());
        if (newGroups.size() != updateDto.groupIds().size()) {
            throw new EntityNotFoundException("Одна или несколько групп из нового списка ID не найдены.");
        }

        // Обновляем поля
        streamToUpdate.setName(updateDto.name());
        streamToUpdate.setSemester(updateDto.semester());
        streamToUpdate.setGroups(new HashSet<>(newGroups));

        StudyStream updatedStream = studyStreamRepository.save(streamToUpdate);
        return studyStreamMapper.toDto(updatedStream);
    }

    /**
     * Находит поток по его ID.
     *
     * @param streamId ID потока.
     * @return Optional с DTO потока.
     */
    @Transactional(readOnly = true)
    public Optional<StudyStreamDto> findById(Integer streamId) {
        return studyStreamRepository.findById(streamId).map(studyStreamMapper::toDto);
    }

    /**
     * Возвращает список всех существующих потоков.
     *
     * @return Список DTO потоков.
     */
    @Transactional(readOnly = true)
    public List<StudyStreamDto> findAll() {
        return studyStreamMapper.toDtoList(studyStreamRepository.findAll());
    }

    /**
     * Удаляет учебный поток.
     *
     * @param streamId ID удаляемого потока.
     */
    @Transactional
    public void deleteStudyStream(Integer streamId) {
        // Проверяем, не используется ли этот поток в каких-либо назначениях (Assignment)
        // Это более правильная проверка, чем просто existsById
        // if (assignmentRepository.existsByStudyStreamId(streamId)) {
        //     throw new IllegalStateException("Нельзя удалить поток, так как он используется в назначениях.");
        // }
        // Пока этой проверки нет, делаем простую
        if (!studyStreamRepository.existsById(streamId)) {
            throw new EntityNotFoundException("Поток с id=" + streamId + " не найден.");
        }

        studyStreamRepository.deleteById(streamId);
    }
}
