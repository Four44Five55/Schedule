package ru.services;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import ru.abstracts.AbstractLesson;
import ru.entity.*;
import ru.enums.KindOfConstraints;
import ru.enums.TimeSlotPair;
import ru.inter.IMaterialEntity;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.model.SchedulableResource;
import ru.services.solver.model.ScheduleGrid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelExportService {
    /**
     * Основной публичный метод для экспорта расписания одной сущности.
     *
     * @param workspace    Сгенерированное расписание.
     * @param entity       Сущность (преподаватель, группа, аудитория), для которой делается экспорт.
     * @param subDirectory Подпапка для сохранения (например, "educators", "groups").
     */
    public void exportScheduleForEntity(ScheduleWorkspace workspace, IMaterialEntity entity, String entityName, String subDirectory) {

        // Загрузка шаблона из ресурсов
        try (InputStream is = ExcelExportService.class.getClassLoader().getResourceAsStream("templates/template.xlsx")) {
            if (is == null) {
                throw new IOException("Шаблон не найден в ресурсах!");
            }

            // Создание книги из шаблона
            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheet("Расписание"); // Используем существующий лист

            if (sheet == null) {
                throw new IOException("Лист 'Расписание' не найден в шаблоне!");
            }

            // Заполняем лист данными
            fillSheetWithSchedule(sheet, workbook, workspace, entity);
            // Сохраняем файл
            saveWorkbookToFile(workbook, entityName, subDirectory);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fillSheetWithSchedule(Sheet sheet, Workbook workbook, ScheduleWorkspace workspace, IMaterialEntity entity) {
        ScheduleGrid scheduleGrid = workspace.getGrid();
        int startRow = 6;
        int startColumn = 3;

        int row = startRow;
        int column = startColumn;

        for (LocalDate checkedDate = scheduleGrid.getStartDate(); !checkedDate.isAfter(scheduleGrid.getEndDate()); checkedDate = checkedDate.plusDays(1)) {
            if (checkedDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                column++;
                row = startRow;
                continue;
            }

            getCell(sheet, row, column).setCellValue(checkedDate);
            applyDateFormat(getCell(sheet, row, column), "dd", workbook);
            row++;

            for (TimeSlotPair timeSlotPair : TimeSlotPair.values()) {
                if (!ScheduleDaysSlotsConfig.isSlotAvailable(checkedDate, timeSlotPair)) {
                    row += 3;
                    continue; // Пропускаем запрещённые слоты
                }

                CellForLesson cell = CellForLessonFactory.getCell(checkedDate, timeSlotPair);
                SchedulableResource resource = workspace.getResourceManager().getResource(entity);
                // ИСПРАВЛЕНА ОШИБКА: убрана дублирующая строка
                Lesson lesson = resource.getLessonInCell(cell);

                if (lesson != null) {
                    List<String> listDataLesson = getListDataLessonForEntity(entity, lesson);
                    getCell(sheet, row, column).setCellValue(listDataLesson.get(0));
                    row++;
                    getCell(sheet, row, column).setCellValue(listDataLesson.get(1));
                    row++;
                    getCell(sheet, row, column).setCellValue(listDataLesson.get(2));
                    row++;
                } else {
                    Optional<KindOfConstraints> constraintOpt = resource.getConstraint(cell);
                    if (constraintOpt.isPresent()) {
                        KindOfConstraints constraint = constraintOpt.get();
                        row++;
                        getCell(sheet, row, column).setCellValue(constraint.getAbbreviationName());
                        row += 2;
                    }
                    // Предполагаем, что интерфейс IMaterialEntity имеет такой метод

                    else {
                        row += 3;
                    }
                }
            }
        }

        // Авторазмер колонок
        int widthInUnits = (int) (45 * 256 / 7.0);
        for (int i = 0; i < column + 1; i++) {
            sheet.setColumnWidth(i, widthInUnits);
        }
    }

    private List<String> getListDataLessonForEntity(IMaterialEntity entity, AbstractLesson lesson) {
        List<String> listData = new ArrayList<>();
        if (entity instanceof Educator) {
            listData.add(lesson.getDisciplineCourse().getDiscipline().getAbbreviation());//Дисциплина
            listData.add(lesson.getStudyStream().getGroups().stream()
                    .map(Group::getName)
                    .collect(Collectors.joining(", ")));//Список групп занятия
            listData.add(lesson.getAssignedAuditoriums().stream()
                    .map(Auditorium::getName)
                    .distinct()
                    .collect(Collectors.joining(", ")));//Аудитория проведения занятия

        } else if (entity instanceof Group) {
            listData.add(lesson.getKindOfStudy().getAbbreviationName() + lesson.getCurriculumSlot().getThemeLesson().getThemeNumber());//Тема занятия
            listData.add(lesson.getDisciplineCourse().getDiscipline().getAbbreviation());//Дисциплина
            listData.add(lesson.getAssignedAuditoriums().stream()
                    .map(Auditorium::getName)
                    .distinct()
                    .collect(Collectors.joining(", ")));//Аудитория проведения занятия
        } else if (entity instanceof Auditorium) {
            listData.add(lesson.getKindOfStudy().getAbbreviationName() + lesson.getCurriculumSlot().getThemeLesson().getThemeNumber());//Тема занятия
            listData.add(lesson.getStudyStream().getGroups().stream()
                    .map(Group::getName)
                    .collect(Collectors.joining(", ")));//Список групп занятия
            listData.add(lesson.getDisciplineCourse().getDiscipline().getAbbreviation());//Дисциплина
        } else {
            throw new IllegalArgumentException("Неизвестный тип сущности: " + entity.getClass());
        }
        return listData;
    }

    /**
     * Сохраняет книгу Excel в файл.
     *
     * @param workbook   книга Excel
     * @param entityName имя сущности для названия файла
     * @throws IOException если произошла ошибка при сохранении
     */
    private void saveWorkbookToFile(Workbook workbook, String entityName, String subDirectory) throws IOException {
        String correctSubDirectory = subDirectory + File.separator + entityName + ".xlsx";
        String filePath = Paths.get("output", correctSubDirectory).toString();
        boolean success = createDirectoriesForFile(filePath);
        if (success) {
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
                System.out.println("Файл успешно создан: " + filePath);
            }
        } else {
            System.out.println("Не удалось создать директории для файла");
        }
    }

    /**
     * Создание директории для сохранения файла
     *
     * @param filePath путь к файлу
     * @return Boolean
     */
    private boolean createDirectoriesForFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Path parentDir = path.getParent(); // Путь к родительской директории

            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Ошибка при создании директорий для файла: " + e.getMessage());
            return false;
        }
    }

    /**
     * Получает ячейку по номеру строки и столбца.
     * Если ячейка или строка не существуют, они создаются.
     *
     * @param sheet  Лист Excel.
     * @param row    Номер строки (начинается с 0).
     * @param column Номер столбца (начинается с 0).
     * @return Ячейка.
     */

    public Cell getCell(Sheet sheet, int row, int column) {
        Row rowIndex = sheet.getRow(row);
        if (rowIndex == null) {
            rowIndex = sheet.createRow(row);
        }
        Cell cell = rowIndex.getCell(column);
        if (cell == null) {
            cell = rowIndex.createCell(column);
        }
        return cell;
    }

    /**
     * Применяет форматирование даты к ячейке.
     *
     * @param cell     Ячейка, к которой нужно применить форматирование.
     * @param format   Формат даты (например, "dd.MM.yyyy").
     * @param workbook Рабочая книга (Workbook), используемая для создания стиля.
     */

    public void applyDateFormat(Cell cell, String format, Workbook workbook) {
        // Создаем стиль для ячейки
        CellStyle dateCellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat(format));

        // Применяем стиль к ячейке
        cell.setCellStyle(dateCellStyle);
    }
}
