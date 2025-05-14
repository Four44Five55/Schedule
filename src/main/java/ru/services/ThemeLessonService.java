package ru.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.logicSchema.ThemeLesson;
import ru.repositories.ThemeLessonRepository;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ThemeLessonService {
    private final ThemeLessonRepository repository;

    public ThemeLesson create(ThemeLesson themeLesson) {
        return repository.save(themeLesson);
    }

    public ThemeLesson getById(Integer id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Discipline не найдена: id=" + id));
    }

    public ThemeLesson update(Integer id, ThemeLesson updatedTheme) {
        ThemeLesson existing = getById(id);
        existing.setThemeNumber(updatedTheme.getThemeNumber());
        existing.setTitle(updatedTheme.getTitle());
        return repository.save(existing);
    }

    public void delete(Integer id) {
        repository.deleteById(id);
    }

    public List<ThemeLesson> getThemeLessons() {
        repository.findAll();
        return repository.findAll();
    }
}
