package ru.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Assignment;
import ru.entity.Auditorium;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.SessionStatus;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration тесты для CQRS Command Side.
 *
 * <p>Проверяет:</p>
 * <ul>
 *   <li>Создание таблиц schedule_session, lesson_placement</li>
 *   <li>Optimistic Locking (@Version)</li>
 *   <li>Аудит изменений (created_by, updated_by)</li>
 *   <li>Связи между сущностями</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommandSideIntegrationTest {

    @Autowired
    private ScheduleSessionRepository sessionRepo;

    @Autowired
    private LessonPlacementRepository placementRepo;

    private ScheduleSession testSession;

    @BeforeEach
    void setUp() {
        // Создаём тестовую сессию
        testSession = new ScheduleSession("Тестовая сессия", "test-user");
        testSession = sessionRepo.save(testSession);
    }

    @Test
    @DisplayName("Command Side: Таблица schedule_session создана")
    void testScheduleSessionTableExists() {
        // Assert
        assertThat(testSession.getId()).isNotNull();
        assertThat(testSession.getName()).isEqualTo("Тестовая сессия");
        assertThat(testSession.getStatus()).isEqualTo(SessionStatus.INITIALIZED);
        assertThat(testSession.getCreatedBy()).isEqualTo("test-user");
        assertThat(testSession.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Command Side: Optimistic Locking работает")
    void testOptimisticLocking() {
        // Arrange
        Long version1 = testSession.getVersion();

        // Act (первое обновление)
        testSession.updateStatus(SessionStatus.GENERATING, "user1");
        sessionRepo.save(testSession);

        // Assert
        ScheduleSession reloaded = sessionRepo.findById(testSession.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(version1 + 1); // Version increased!
    }

    @Test
    @DisplayName("Command Side: Конфликт при параллельном редактировании")
    void testConcurrentEditConflict() {
        // Arrange
        UUID sessionId = testSession.getId();

        // Act (загружаем две копии)
        ScheduleSession session1 = sessionRepo.findById(sessionId).orElseThrow();
        ScheduleSession session2 = sessionRepo.findById(sessionId).orElseThrow();

        // Пользователь 1 изменяет
        session1.updateStatus(SessionStatus.GENERATING, "user1");
        sessionRepo.save(session1);

        // Assert (пользователь 2 пытается изменить - должен быть конфликт)
        assertThatThrownBy(() -> {
            session2.updateStatus(SessionStatus.FINAL, "user2");
            sessionRepo.save(session2); // ❌ Conflict!
        }).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Command Side: Аудит изменений работает")
    void testAuditFields() {
        // Arrange
        String createdBy = "creator";
        String updatedBy = "updater";

        // Act (создание)
        ScheduleSession session = new ScheduleSession("Test", createdBy);
        session = sessionRepo.save(session);

        // Assert (создание)
        assertThat(session.getCreatedBy()).isEqualTo(createdBy);
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getUpdatedBy()).isEqualTo(createdBy); // При создании = createdBy
        assertThat(session.getUpdatedAt()).isEqualTo(session.getCreatedAt());

        // Act (обновление)
        session.updateStatus(SessionStatus.GENERATING, updatedBy);
        session = sessionRepo.save(session);

        // Assert (обновление)
        assertThat(session.getUpdatedBy()).isEqualTo(updatedBy);
        assertThat(session.getUpdatedAt()).isAfter(session.getCreatedAt());
    }

    @Test
    @DisplayName("Command Side: Связь Session ↔ Placements")
    void testSessionPlacementsRelation() {
        // Arrange
        Assignment assignment = createMockAssignment();
        LessonPlacement placement = new LessonPlacement(
            assignment,
            LocalDate.of(2025, 1, 11),
            ru.enums.TimeSlotPair.FIRST,
            testSession,
            "test-user"
        );

        // Act
        testSession.addPlacement(placement);
        sessionRepo.save(testSession);

        // Assert
        ScheduleSession reloaded = sessionRepo.findById(testSession.getId()).orElseThrow();
        assertThat(reloaded.getPlacements()).hasSize(1);
        assertThat(reloaded.getPlacements().iterator().next().getId()).isEqualTo(placement.getId());
    }

    @Test
    @DisplayName("Command Side: Cascade удаление работает")
    void testCascadeDelete() {
        // Arrange
        Assignment assignment = createMockAssignment();
        LessonPlacement placement = new LessonPlacement(
            assignment,
            LocalDate.of(2025, 1, 11),
            ru.enums.TimeSlotPair.FIRST,
            testSession,
            "test-user"
        );
        testSession.addPlacement(placement);
        sessionRepo.save(testSession);

        UUID placementId = placement.getId();
        assertThat(placementRepo.existsById(placementId)).isTrue();

        // Act (удаляем сессию)
        sessionRepo.delete(testSession);

        // Assert (placement должен удалиться каскадно)
        assertThat(placementRepo.existsById(placementId)).isFalse();
    }

    @Test
    @DisplayName("Command Side: LessonPlacement аудит работает")
    void testLessonPlacementAudit() {
        // Arrange
        Assignment assignment = createMockAssignment();
        String user = "test-user";

        LessonPlacement placement = new LessonPlacement(
            assignment,
            LocalDate.of(2025, 1, 11),
            ru.enums.TimeSlotPair.FIRST,
            testSession,
            user
        );

        // Assert (создание)
        assertThat(placement.getCreatedBy()).isEqualTo(user);
        assertThat(placement.getCreatedAt()).isNotNull();
        assertThat(placement.getUpdatedBy()).isEqualTo(user);
        assertThat(placement.getUpdatedAt()).isNotNull();

        // Act (обновление)
        placement.updatePlacement(
            LocalDate.of(2025, 1, 12),
            ru.enums.TimeSlotPair.SECOND,
            new HashSet<>(),
            "another-user"
        );
        placementRepo.save(placement);

        // Assert (обновление)
        assertThat(placement.getUpdatedBy()).isEqualTo("another-user");
        assertThat(placement.getUpdatedAt()).isAfter(placement.getCreatedAt());
    }

    @Test
    @DisplayName("Command Side: Поиск placements по сессии")
    void testFindPlacementsBySession() {
        // Arrange
        Assignment assignment = createMockAssignment();
        LessonPlacement placement1 = new LessonPlacement(
            assignment,
            LocalDate.of(2025, 1, 11),
            ru.enums.TimeSlotPair.FIRST,
            testSession,
            "user"
        );
        LessonPlacement placement2 = new LessonPlacement(
            assignment,
            LocalDate.of(2025, 1, 12),
            ru.enums.TimeSlotPair.SECOND,
            testSession,
            "user"
        );
        testSession.addPlacement(placement1);
        testSession.addPlacement(placement2);
        sessionRepo.save(testSession);

        // Act
        List<LessonPlacement> placements = placementRepo.findBySessionId(testSession.getId());

        // Assert
        assertThat(placements).hasSize(2);
    }

    @Test
    @DisplayName("Command Side: Query с lock работает")
    void testFindByIdWithLock() {
        // Act
        Optional<ScheduleSession> withLock = sessionRepo.findByIdWithLock(testSession.getId());

        // Assert
        assertThat(withLock).isPresent();
        assertThat(withLock.get().getId()).isEqualTo(testSession.getId());
    }

    // ========== Helper Methods ==========

    private Assignment createMockAssignment() {
        // В реальном проекте здесь нужно загрузить или создать mock Assignment
        // Для теста используем null (потребуется @Nullable в LessonPlacement)
        return null; // TODO: Создать mock Assignment
    }
}
