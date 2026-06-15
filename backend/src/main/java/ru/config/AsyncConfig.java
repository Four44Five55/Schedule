package ru.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Конфигурация для асинхронного выполнения задач.
 *
 * <p>Включает поддержку {@link org.springframework.scheduling.annotation.Async}
 * для синхронизации Query Side с Command Side.</p>
 *
 * @see ru.services.ScheduleSynchronizer
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Конфигурация для асинхронных задач
    // Можно настроить ThreadPoolTaskExecutor здесь
}
