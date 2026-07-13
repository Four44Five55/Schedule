package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.Group;

import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Integer> {
    /**
     * Проверяет, существует ли группа с указанным названием.
     * Spring Data JPA автоматически сгенерирует реализацию для этого метода.
     * Запрос будет выглядеть примерно так: "SELECT COUNT(*) > 0 FROM groups WHERE name = ?"
     * Это эффективнее, чем загружать всю сущность для проверки.
     *
     * @param name Название группы для проверки.
     * @return true, если группа существует, иначе false.
     */
    boolean existsByName(String name);

    /**
     * Также может понадобиться метод для поиска по имени, если нужно проверять
     * уникальность при обновлении.
     */
    Optional<Group> findByName(String name);

    /**
     * Сколько групп числят эту аудиторию домашней. При удалении аудитории ссылка обнуляется
     * ({@code base_auditorium_id ON DELETE SET NULL}) — не потеря данных, но пользователя
     * стоит предупредить: базовая аудитория участвует в подборе комнаты при генерации.
     */
    long countByBaseAuditoriumId(Integer auditoriumId);
}
