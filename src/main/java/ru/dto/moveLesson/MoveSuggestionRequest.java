package ru.dto.moveLesson;

import java.util.UUID;

public record MoveSuggestionRequest(UUID sessionId, // ID сессии расписания
                                    Integer lessonId,
                                    Integer rootEntityId,
                                    String rootEntityType // "EDUCATOR", "GROUP", "AUDITORIUM"
) {
}
