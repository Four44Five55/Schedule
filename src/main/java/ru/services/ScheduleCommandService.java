package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dto.command.ScheduleSessionDto;
import ru.entity.write.ScheduleSession;
import ru.mapper.command.ScheduleSessionMapper;
import ru.repository.write.ScheduleSessionRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleCommandService {
    private final ScheduleSessionRepository repository;
    private final ScheduleSessionMapper sessionMapper;

    public ScheduleSessionDto getSession(UUID id) {
        ScheduleSession session = repository.findById(id).orElseThrow();
        return sessionMapper.toDto(session);
    }
}
