package ru.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.read.ScheduleView;
import ru.repository.read.ScheduleViewRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration тесты для CQRS Query Side.
 *
 * <p>Проверяет:</p>
 * <ul>
 *   <li>Создание таблиц schedule_view</li>
 *   <li>Работу индексов</li>
 *   <li>Запросы для студентов, преподавателей, аудиторий</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuerySideIntegrationTest {

    @Autowired
    private ScheduleViewRepository viewRepository;

    @Test
    @DisplayName("Query Side: Таблица schedule_view создана")
    void testScheduleViewTableExists() {
        // Arrange & Act
        long countBefore = viewRepository.count();

        // Создаём тестовую view
        ScheduleView view = new ScheduleView(
            java.util.UUID.randomUUID(),
            LocalDate.of(2025, 1, 11),
            ru.enums.TimeSlotPair.FIRST
        );
        view.setEducator(1, "Иванов И.И.");
        view.setGroup(2, "ИБ-21");
        view.setDiscipline("Математический анализ", "МаТе");
        view.setAuditorium(3, "Аудитория 301");

        viewRepository.save(view);

        // Assert
        long countAfter = viewRepository.count();
        assertThat(countAfter).isEqualTo(countBefore + 1);
    }

    @Test
    @DisplayName("Query Side: Индексы работают (поиск по группе)")
    void testIndexesWork() {
        // Arrange
        Integer streamId = 123;
        LocalDate start = LocalDate.of(2025, 1, 11);
        LocalDate end = LocalDate.of(2025, 1, 17);

        // Создаём тестовые данные
        for (int i = 0; i < 10; i++) {
            ScheduleView view = new ScheduleView(
                java.util.UUID.randomUUID(),
                start.plusDays(i),
                ru.enums.TimeSlotPair.FIRST
            );
            view.setGroup(streamId, "Группа " + i);
            viewRepository.save(view);
        }

        // Act
        long startTime = System.currentTimeMillis();
        List<ScheduleView> result = viewRepository.findByStudentGroup(streamId, start, end);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(result).hasSize(10);
        assertThat(endTime - startTime).isLessThan(50); // < 50ms с индексами
    }

    @Test
    @DisplayName("Query Side: Поиск по преподавателю")
    void testFindByEducator() {
        // Arrange
        Integer educatorId = 456;
        LocalDate date = LocalDate.of(2025, 1, 12);

        ScheduleView view = new ScheduleView(
            java.util.UUID.randomUUID(),
            date,
            ru.enums.TimeSlotPair.SECOND
        );
        view.setEducator(educatorId, "Петров П.П.");
        view.setGroup(123, "ИБ-21");
        viewRepository.save(view);

        // Act
        List<ScheduleView> result = viewRepository.findByEducatorAndDate(educatorId, date);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEducatorName()).isEqualTo("Петров П.П.");
    }

    @Test
    @DisplayName("Query Side: Проверка свободности аудитории")
    void testAuditoriumFree() {
        // Arrange
        Integer auditoriumId = 789;
        LocalDate date = LocalDate.of(2025, 1, 12);
        String slot = "FIRST";

        // Act (пока нет занятий - должна быть свободна)
        boolean isFree = viewRepository.isAuditoriumFree(auditoriumId, date, slot);

        // Assert
        assertThat(isFree).isTrue();

        // Добавляем занятие
        ScheduleView view = new ScheduleView(
            java.util.UUID.randomUUID(),
            date,
            ru.enums.TimeSlotPair.FIRST
        );
        view.setAuditorium(auditoriumId, "Аудитория 301");
        viewRepository.save(view);

        // Act (теперь занята)
        boolean isFreeAfter = viewRepository.isAuditoriumFree(auditoriumId, date, slot);

        // Assert
        assertThat(isFreeAfter).isFalse();
    }

    @Test
    @DisplayName("Query Side: Агрегация по аудиториям")
    void testAuditoriumAggregation() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 1, 12);

        // Создаём несколько занятий в одной аудитории
        for (int i = 0; i < 5; i++) {
            ScheduleView view = new ScheduleView(
                java.util.UUID.randomUUID(),
                date,
                ru.enums.TimeSlotPair.FIRST
            );
            view.setAuditorium(789, "Аудитория 301");
            view.setGroup(100 + i, "Группа " + i);
            viewRepository.save(view);
        }

        // Act
        List<Object[]> result = viewRepository.countByAuditoriumAndDate();

        // Assert
        assertThat(result).isNotEmpty();
        // Найдём запись по аудитории 789
        Object[] auditoriumStats = result.stream()
            .filter(row -> row[0].equals(789))
            .findFirst()
            .orElse(null);

        assertThat(auditoriumStats).isNotNull();
        assertThat(auditoriumStats[1]).isEqualTo(5L); // 5 занятий
    }

    @Test
    @DisplayName("Query Side: Синхронизация через placement_id")
    void testPlacementIdSync() {
        // Arrange
        java.util.UUID placementId = java.util.UUID.randomUUID();

        ScheduleView view = new ScheduleView(
            placementId,
            LocalDate.of(2025, 1, 11),
            ru.enums.TimeSlotPair.FIRST
        );
        view.setPlacementId(placementId);
        viewRepository.save(view);

        // Act
        java.util.Optional<ScheduleView> found = viewRepository.findByPlacementId(placementId);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(placementId);
    }

    @Test
    @DisplayName("Query Side: Удаление по placement_id")
    void testDeleteByPlacementId() {
        // Arrange
        java.util.UUID placementId = java.util.UUID.randomUUID();

        ScheduleView view = new ScheduleView(
            placementId,
            LocalDate.of(2025, 1, 11),
            ru.enums.TimeSlotPair.FIRST
        );
        view.setPlacementId(placementId);
        viewRepository.save(view);

        long countBefore = viewRepository.count();

        // Act
        viewRepository.deleteByPlacementId(placementId);

        // Assert
        long countAfter = viewRepository.count();
        assertThat(countAfter).isEqualTo(countBefore - 1);
    }
}
