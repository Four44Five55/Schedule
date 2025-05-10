package ru.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.Discipline;
import ru.repositories.DisciplineRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DisciplineService {
    private final DisciplineRepository disciplineRepository;
    private final EntityManager entityManager;
    // Создание с проверками
    public Discipline createDiscipline(String name, String abbreviation) {
        if (disciplineRepository.existsByName(name)) {
            throw new IllegalArgumentException("Дисциплина с названием '" + name + "' уже существует");
        }
        if (disciplineRepository.existsByAbbreviation(abbreviation)) {
            throw new IllegalArgumentException("Аббревиатура '" + abbreviation + "' уже используется");
        }

        return disciplineRepository.save(new Discipline(name, abbreviation));
    }

    // Получение по ID
/*    @Transactional
    public Discipline getById(Integer id) {
        return disciplineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Дисциплина с ID " + id + " не найдена"));
    }*/
    @Transactional
    public Discipline getById(Integer id) {
        System.out.println("Поиск дисциплины с ID: " + id);
        Optional<Discipline> result = disciplineRepository.findById(id);

        if (result.isPresent()) {
            System.out.println("Запись найдена: " + result.get());
            return result.get();
        } else {
            System.out.println("Запись не найдена, проверяем через EntityManager...");
            Discipline emResult = entityManager.find(Discipline.class, id);
            if (emResult != null) {
                System.out.println("EntityManager нашел запись: " + emResult);
                return emResult;
            }
            throw new EntityNotFoundException("Дисциплина с ID " + id + " не найдена");
        }
    }

    // Получение по названию
    @Transactional
    public Discipline getByName(String name) {
        return disciplineRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Дисциплина '" + name + "' не найдена"));
    }

    // Проверка существования по названию
    @Transactional
    public boolean existsByName(String name) {
        return disciplineRepository.existsByName(name);
    }

    // Получение всех дисциплин
    @Transactional
    public List<Discipline> getAll() {
        return disciplineRepository.findAll();
    }

    // Удаление
    public void delete(Integer id) {
        if (!disciplineRepository.existsById(id)) {
            throw new EntityNotFoundException("Нельзя удалить - дисциплина с ID " + id + " не найдена");
        }
        disciplineRepository.deleteById(id);
    }
    @Transactional
    public void testDirectFind() {
        Discipline d = entityManager.find(Discipline.class, 1);
        System.out.println("Найдена: " + d); // Должно вывести данные

        // Альтернативный вариант
        List<Discipline> all = entityManager.createQuery("SELECT d FROM Discipline d", Discipline.class)
                .getResultList();
        System.out.println("Все записи: " + all); // Должна быть запись с id=1
    }
    @Transactional
    public void fullDiagnostic() {
        System.out.println("=== Начало диагностики ===");

        // 1. Проверка через EntityManager
        Discipline emDiscipline = entityManager.find(Discipline.class, 1);
        System.out.println("Результат EntityManager.find(): " + emDiscipline);

        // 2. Проверка через Repository
        Optional<Discipline> repoDiscipline = disciplineRepository.findById(1);
        System.out.println("Результат Repository.findById(): " + repoDiscipline.orElse(null));

        // 3. Проверка всех записей
        List<Discipline> all = disciplineRepository.findAll();
        System.out.println("Все записи в репозитории: " + all);

        // 4. Проверка SQL запросом
        List<Discipline> sqlResults = entityManager.createNativeQuery("SELECT * FROM discipline WHERE id = 1", Discipline.class)
                .getResultList();
        System.out.println("Результат прямого SQL запроса: " + sqlResults);

        System.out.println("=== Конец диагностики ===");
    }
}
