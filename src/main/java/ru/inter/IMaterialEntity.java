package ru.inter;
/**
 * Интерфейс-маркер для всех сущностей, которые могут участвовать в расписании
 * и иметь свои собственные ограничения (constraints).
 * (Educator, Group, Auditorium).
 */
public interface IMaterialEntity {
    Integer getId();
    String getName();
}
