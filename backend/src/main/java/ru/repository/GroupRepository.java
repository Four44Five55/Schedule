package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Сколько групп числят домашней любую аудиторию из набора. Для удаления КОРПУСА: его аудитории
     * уходят каскадом, и у этих групп {@code base_auditorium_id} обнулится (SET NULL).
     */
    long countByBaseAuditoriumIdIn(java.util.Collection<Integer> auditoriumIds);

    /**
     * Сколько групп закреплено за подразделением. Для цены удаления: FK
     * {@code groups.org_unit_id → org_unit ON DELETE RESTRICT}.
     */
    long countByOrgUnitId(Integer orgUnitId);

    /**
     * Id групп любого из подразделений набора — вход для фильтра «расписание кафедры».
     * Вложенность разворачивает {@code OrgUnitScopeResolver}, см.
     * {@code EducatorRepository.findIdsByOrgUnitIdIn}.
     *
     * <p>Класс указан полным именем намеренно: {@code GROUP} — зарезервированное слово JPQL
     * ({@code GROUP BY}), и короткое {@code from Group g} зависит от снисходительности парсера.</p>
     */
    @Query("select g.id from ru.entity.Group g where g.orgUnit.id in :orgUnitIds")
    java.util.List<Integer> findIdsByOrgUnitIdIn(
            @Param("orgUnitIds") java.util.Collection<Integer> orgUnitIds);
}
