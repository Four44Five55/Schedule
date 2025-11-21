package ru.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.Discipline;
import ru.repository.DisciplineRepository;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DisciplineService {
    private final DisciplineRepository repository;


    public Discipline create(Discipline discipline) {
        return repository.save(discipline);
    }

    public Discipline getById(Integer id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Discipline не найдена: id=" + id));
    }

    public Discipline update(Integer id, Discipline updatedDiscipline) {
        Discipline existing = getById(id);
        existing.setName(updatedDiscipline.getName());
        existing.setAbbreviation(updatedDiscipline.getAbbreviation());
        return repository.save(existing);
    }

    public void delete(Integer id) {
        repository.deleteById(id);
    }

    public List<Discipline> getDisciplines() {
        repository.findAll();
        return repository.findAll();
    }
}
