package ru.services.constraints;

/**
 * Сервис для загрузки всех постоянных ограничений из базы данных.
 */
public interface ConstraintService {

    /**
     * Загружает все ограничения для всех типов ресурсов и группирует их по ID ресурса.
     *
     * @return DTO, содержащее карты ограничений для каждого типа ресурса.
     */
    AllConstraints loadAllConstraints();
}
