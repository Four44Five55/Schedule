package ru.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateUtils {

    private DateUtils() {
        // Приватный конструктор, чтобы нельзя было создать экземпляр класса
    }

    // Стандартные форматы
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_DATE_FORMAT = "dd.MM.yyyy";
    public static final String DEFAULT_DATE_TIME_FORMAT = "dd.MM.yyyy HH:mm:ss";

    /**
     * Преобразует строку в LocalDate, используя стандартный формат (yyyy-MM-dd)
     *
     * @param dateStr строка с датой
     * @return LocalDate
     * @throws DateTimeParseException если строка не соответствует формату
     */
    public static LocalDate parseToLocalDate(String dateStr) {
        return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Преобразует строку в LocalDate, используя указанный формат
     *
     * @param dateStr строка с датой
     * @param pattern формат даты (например, "dd.MM.yyyy")
     * @return LocalDate
     * @throws DateTimeParseException если строка не соответствует формату
     */
    public static LocalDate parseToLocalDate(String dateStr, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDate.parse(dateStr, formatter);
    }

    /**
     * Преобразует строку в LocalDateTime, используя стандартный формат (yyyy-MM-dd HH:mm:ss)
     *
     * @param dateTimeStr строка с датой и временем
     * @return LocalDateTime
     * @throws DateTimeParseException если строка не соответствует формату
     */
    public static LocalDateTime parseToLocalDateTime(String dateTimeStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(ISO_DATE_TIME_FORMAT);
        return LocalDateTime.parse(dateTimeStr, formatter);
    }

    /**
     * Преобразует строку в LocalDateTime, используя указанный формат
     *
     * @param dateTimeStr строка с датой и временем
     * @param pattern формат даты и времени (например, "dd.MM.yyyy HH:mm:ss")
     * @return LocalDateTime
     * @throws DateTimeParseException если строка не соответствует формату
     */
    public static LocalDateTime parseToLocalDateTime(String dateTimeStr, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(dateTimeStr, formatter);
    }

    /**
     * Форматирует LocalDate в строку, используя стандартный формат (yyyy-MM-dd)
     *
     * @param date дата
     * @return строка с датой
     */
    public static String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Форматирует LocalDate в строку, используя указанный формат
     *
     * @param date дата
     * @param pattern формат (например, "dd.MM.yyyy")
     * @return строка с датой
     */
    public static String formatDate(LocalDate date, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.format(formatter);
    }

    /**
     * Форматирует LocalDateTime в строку, используя стандартный формат (yyyy-MM-dd HH:mm:ss)
     *
     * @param dateTime дата и время
     * @return строка с датой и временем
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(ISO_DATE_TIME_FORMAT);
        return dateTime.format(formatter);
    }

    /**
     * Форматирует LocalDateTime в строку, используя указанный формат
     *
     * @param dateTime дата и время
     * @param pattern формат (например, "dd.MM.yyyy HH:mm:ss")
     * @return строка с датой и временем
     */
    public static String formatDateTime(LocalDateTime dateTime, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return dateTime.format(formatter);
    }

    /**
     * Пытается разобрать строку в LocalDate, используя несколько форматов
     *
     * @param dateStr строка с датой
     * @return LocalDate, если удалось разобрать
     * @throws DateTimeParseException если ни один формат не подошел
     */
    public static LocalDate parseDateFlexible(String dateStr) {
        String[] possiblePatterns = {
                ISO_DATE_FORMAT,
                DEFAULT_DATE_FORMAT,
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "yyyyMMdd"
        };

        for (String pattern : possiblePatterns) {
            try {
                return parseToLocalDate(dateStr, pattern);
            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }
        throw new DateTimeParseException("Не удалось разобрать дату: " + dateStr, dateStr, 0);
    }
}
