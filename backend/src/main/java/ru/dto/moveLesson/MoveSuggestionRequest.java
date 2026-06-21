package ru.dto.moveLesson;

import java.util.UUID;

public record MoveSuggestionRequest(UUID sessionId, // ID сессии расписания
                                    UUID placementId, // UUID размещения занятия (надёжный уникальный ключ)
                                    Integer rootEntityId,
                                    String rootEntityType // "EDUCATOR", "GROUP", "AUDITORIUM"
) {
}
