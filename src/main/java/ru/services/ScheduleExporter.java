package ru.services;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.abstracts.AbstractLesson;
import ru.abstracts.AbstractMaterialEntity;
import ru.entity.*;
import ru.entity.factories.CellForLessonFactory;
import ru.enums.TimeSlotPair;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ScheduleExporter {
//TODO изменить добавление данных по аудитории

    private static List<String> getListDataLessonForEntity(AbstractMaterialEntity entity, AbstractLesson lesson) {
        List<String> listData = new ArrayList<>();

        if (entity instanceof Educator) {
            listData.add(lesson.getDiscipline().getName());//Дисциплина
            listData.add(lesson.getGroups().stream()
                    .map(Group::getName)
                    .collect(Collectors.joining(", ")));//Список групп занятия
            listData.add(String.valueOf(lesson.getAuditorium().stream()
                    .map(AbstractMaterialEntity::getName)
                    .distinct()
                    .collect(Collectors.toList())));//Аудитория проведения занятия

            //listData.add("120-3");//Аудитория проведения занятия
        } else if (entity instanceof Group) {
            listData.add(lesson.getKindOfStudy().getAbbreviationName());//Тема занятия
            listData.add(lesson.getDiscipline().getName());//Дисциплина
            listData.add(String.valueOf(lesson.getAuditorium().stream()
                    .map(AbstractMaterialEntity::getName)
                    .distinct()
                    .collect(Collectors.toList())));//Аудитория проведения занятия
        } else if (entity instanceof Auditorium) {
            listData.add(lesson.getKindOfStudy().getAbbreviationName());//Тема занятия
            listData.add(lesson.getGroups().stream()
                    .map(Group::getName)
                    .collect(Collectors.joining(", ")));//Список групп занятия
            listData.add(lesson.getDiscipline().getName());//Дисциплина
        } else {
            throw new IllegalArgumentException("Неизвестный тип сущности: " + entity.getClass());
        }

        return listData;
    }

    /**
     * Экспортирует данные из AEntityWithScheduleGrid в Excel-файл.
     *
     * @param entity     сущность имеющая scheduleGrid
     * @param entityName имя сущности для названия файла
     */

    public static void exportToExcel(ScheduleGrid scheduleGrid, AbstractMaterialEntity entity, String entityName) {

        // Загрузка шаблона из ресурсов
        try (InputStream is = ScheduleExporter.class.getClassLoader().getResourceAsStream("templates/template.xlsx")) {
            if (is == null) {
                throw new IOException("Шаблон не найден в ресурсах!");
            }

            // Создание книги из шаблона
            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheet("Расписание"); // Используем существующий лист

            if (sheet == null) {
                throw new IOException("Лист 'Расписание' не найден в шаблоне!");
            }

            int startRow = 6;
            int startColumn = 3;

            int row = startRow;
            int column = startColumn;

            for (LocalDate checkedDate = scheduleGrid.getStartDate(); !checkedDate.isAfter(scheduleGrid.getEndDate()); checkedDate = checkedDate.plusDays(1)) {
                if (!(checkedDate.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                    getCell(sheet, row, column).setCellValue(checkedDate);//запись даты
                    // Применяем форматирование к ячейке
                    applyDateFormat(getCell(sheet, row, column), "dd", workbook);
                    row++;
                    for (TimeSlotPair timeSlotPair : TimeSlotPair.values()) {

                        if (!ScheduleDaysSlotsConfig.isSlotAvailable(checkedDate, timeSlotPair)) {
                            row += 3;
                            continue; // Пропускаем запрещённые слоты
                        }

                        CellForLesson cell = CellForLessonFactory.getCellByDateAndSlot(checkedDate, timeSlotPair);

                        List<AbstractLesson> lessonList = scheduleGrid.getLessonsUsingEntity(entity, cell);
                        AbstractLesson lesson = null;
                        if (!lessonList.isEmpty()) {
                            lesson = lessonList.getFirst();
                        }


                        if (!(lesson == null)) {
                            List<String> listDataLesson = getListDataLessonForEntity(entity, lesson);
                            getCell(sheet, row, column).setCellValue(listDataLesson.get(0));//запись в 1 строку
                            row++;
                            getCell(sheet, row, column).setCellValue(listDataLesson.get(1));//запись в 2 строку
                            row++;
                            getCell(sheet, row, column).setCellValue(listDataLesson.get(2));//запись в 3 строку
                            row++;
                        } else if (entity.hasConstraint(cell)) {
                            row++;
                            getCell(sheet, row, column).
                                    setCellValue(entity.getConstraint(cell).getAbbreviationName());//запись аббревиатуры ограничения
                            row += 2;
                        } else {
                            row += 3;
                        }

                    }
                } else {
                    column++;
                    row = startRow;
                }
            }

            // Авторазмер колонок
            int widthInUnits = (int) (45 * 256 / 7.0); // Результат: ~1645 единиц
            for (int i = 0; i < column; i++) {
                sheet.setColumnWidth(i, widthInUnits);
                //sheet.autoSizeColumn(i);
            }
            // Сохраняем файл
            saveWorkbookToFile(workbook, entityName);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Сохраняет книгу Excel в файл.
     *
     * @param workbook   книга Excel
     * @param entityName имя сущности для названия файла
     * @throws IOException если произошла ошибка при сохранении
     */

    private static void saveWorkbookToFile(Workbook workbook, String entityName) throws IOException {
        String filePath = Paths.get("src/main/resources", entityName + ".xlsx").toString();

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
            System.out.println("Файл успешно создан: " + filePath);
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

    public static Cell getCell(Sheet sheet, int row, int column) {
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

    public static void applyDateFormat(Cell cell, String format, Workbook workbook) {
        // Создаем стиль для ячейки
        CellStyle dateCellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat(format));

        // Применяем стиль к ячейке
        cell.setCellStyle(dateCellStyle);
    }
}
