package ru.dto.moveLesson;

public record MoveSuggestionRequest(Integer lessonId,
                                    Integer rootEntityId,
                                    String rootEntityType // "EDUCATOR", "GROUP", "AUDITORIUM"
) {
}
