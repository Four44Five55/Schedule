package ru.entity.read;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-optimized view для расписания (CQRS Query Side).
 *
 * <p>Денормализованные данные для быстрых SELECT запросов без JOIN.
 * Индексы на основных полях для мгновенного доступа.</p>
 *
 * <p>Используется для:</p>
 * <ul>
 *   <li>Студенты: расписание своей группы</li>
 *   <li>Преподаватели: расписание на дату</li>
 *   <li>Администрация: отчёты, статистика</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/bliki/QueryResponsibilitySeparation.html">CQRS Pattern</a>
 */
@Entity
@Table(name = "schedule_view")
@Getter
public class ScheduleView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ========== Индексированные поля для быстрых запросов ==========

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "educator_id")
    private Integer educatorId;

    @Column(name = "study_stream_id")
    private Integer studyStreamId;

    @Column(name = "auditorium_id")
    private Integer auditoriumId;

    // ========== Денормализованные данные (избегаем JOIN) ==========

    // Дисциплина
    @Column(name = "discipline_name")
    private String disciplineName;

    @Column(name = "discipline_abbr")
    private String disciplineAbbr;

    // Преподаватель
    @Column(name = "educator_name")
    private String educatorName;

    // Группа
    @Column(name = "group_name")
    private String groupName;

    // Тип занятия
    @Setter
    @Column(name = "kind_of_study")
    private String kindOfStudy;

    // Время
    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false)
    private TimeSlotPair timeSlot;

    // Аудитория
    @Column(name = "auditorium_name")
    private String auditoriumName;

    // Тема занятия
    @Column(name = "theme_number")
    private String themeNumber;

    @Column(name = "theme_title")
    private String themeTitle;

    // Слот учебного плана — для определения сцепок (SlotChain связывает CurriculumSlot).
    @Column(name = "curriculum_slot_id")
    private Integer curriculumSlotId;

    // ========== Метаданные для синхронизации ==========

    @Column(name = "placement_id")
    private UUID placementId; // Ссылка на LessonPlacement (Command Side)

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    // ========== Конструкторы ==========

    /**
     * Конструктор по умолчанию для JPA (генерирует id автоматически).
     */
    public ScheduleView() {
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Конструктор с UUID (для обратной совместимости).
     */
    public ScheduleView(UUID id) {
        this.id = id;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Полный конструктор для создания из Lesson + Cell (при синхронизации).
     *
     * @param placementId   ID размещения (для связи с Command Side)
     * @param scheduledDate Дата занятия
     * @param timeSlot      Временной слот
     */
    public ScheduleView(UUID placementId, LocalDate scheduledDate, TimeSlotPair timeSlot) {
        this.id = placementId;
        this.placementId = placementId;
        this.scheduledDate = scheduledDate;
        this.timeSlot = timeSlot;
        this.lastUpdated = LocalDateTime.now();
    }

    // ========== Setters (для обновления при синхронизации) ==========

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setTimeSlot(TimeSlotPair timeSlot) {
        this.timeSlot = timeSlot;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setEducator(Integer educatorId, String educatorName) {
        this.educatorId = educatorId;
        this.educatorName = educatorName;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setGroup(Integer studyStreamId, String groupName) {
        this.studyStreamId = studyStreamId;
        this.groupName = groupName;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setAuditorium(Integer auditoriumId, String auditoriumName) {
        this.auditoriumId = auditoriumId;
        this.auditoriumName = auditoriumName;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setDiscipline(String disciplineName, String disciplineAbbr) {
        this.disciplineName = disciplineName;
        this.disciplineAbbr = disciplineAbbr;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setTheme(String themeNumber, String themeTitle) {
        this.themeNumber = themeNumber;
        this.themeTitle = themeTitle;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setCurriculumSlotId(Integer curriculumSlotId) {
        this.curriculumSlotId = curriculumSlotId;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setPlacementId(UUID placementId) {
        this.placementId = placementId;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Устанавливает время последнего обновления.
     * <p>Используется при синхронизации с Command Side.</p>
     *
     * @param lastUpdated Время последнего обновления
     */
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleView that = (ScheduleView) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ScheduleView{" +
                "id=" + id +
                ", date=" + scheduledDate +
                ", slot=" + timeSlot +
                ", discipline='" + disciplineAbbr + '\'' +
                ", educator='" + educatorName + '\'' +
                ", group='" + groupName + '\'' +
                ", auditorium='" + auditoriumName + '\'' +
                '}';
    }
}
