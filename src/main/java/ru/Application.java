package ru;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.services.*;
import ru.services.solver.ScheduleWorkspace;

import java.sql.SQLException;
import java.util.List;

@SpringBootApplication
public class Application {
    public static void main(String[] args) throws SQLException {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Координирует генерацию расписания и последующий экспорт в Excel для всех сущностей.
     */
    @Bean
    public CommandLineRunner commandLineRunner(ScheduleGenerationService generationService,
                                               ExcelExportService exportService,
                                               EducatorService educatorService,
                                               GroupService groupService,
                                               AuditoriumService auditoriumService) {
        return args -> {
            System.out.println("Запускаем генерацию расписания...");
            List<Integer> courses = List.of(704, 705, 701, 702, 707, 703, 706, 708, 709, 710);

            ScheduleWorkspace generatedWorkspace = generationService.generateForCourseList(courses);
            System.out.println("Генерация расписания завершена.");
            if (generatedWorkspace == null) {
                System.err.println("Не удалось сгенерировать расписание. Экспорт отменен.");
                return; // Выходим, если расписание не создано
            }

            // --- ЭТАП 2: ЭКСПОРТ РАСПИСАНИЙ В EXCEL ---
            System.out.println("\nЗапускаем экспорт расписаний в Excel...");

            // Экспорт для каждого преподавателя
            educatorService.getAllEntities().forEach(educator ->
                    exportService.exportScheduleForEntity(generatedWorkspace, educator, educator.getName(), "Общее расписание")
            );

            // Экспорт для каждой группы
            groupService.getAllEntities().forEach(group ->
                    exportService.exportScheduleForEntity(generatedWorkspace, group, group.getName(), "Общее расписание")
            );

            // Экспорт для каждой аудитории
            auditoriumService.getAllEntities().forEach(auditorium ->
                    exportService.exportScheduleForEntity(generatedWorkspace, auditorium, auditorium.getName(), "Общее расписание")
            );

            System.out.println("\nРабота приложения завершена. Результаты находятся в папке 'output'.");
        };
    }
}
