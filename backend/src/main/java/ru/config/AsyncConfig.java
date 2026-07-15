package ru.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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

    /**
     * Пул проекции read-модели — намеренно ОДНОПОТОЧНЫЙ.
     *
     * <p>{@link ru.services.ScheduleSynchronizer} — единственный, кто пишет в
     * {@code schedule_view}, и делает это через {@code delete by placement} + {@code insert}.
     * Одна команда может породить НЕСКОЛЬКО событий на один и тот же placement из одного
     * коммита: перенос публикует событие о занятии, а следующая за ним в той же транзакции
     * пересортировка ({@code TrackReorderService}) переиздаёт его же среди сдвинутых. При
     * многопоточном пуле два таких события синхронизируют один placement конкурентно — оба
     * удаляют старые строки и оба вставляют новые → нарушение
     * {@code schedule_view_placement_group_educator_unique} (23505, миграция 016).</p>
     *
     * <p>Один поток сериализует писателя: {@code delete+insert} каждого события коммитится до
     * начала следующего, гонка перекрывающихся событий исчезает как класс (не только move→reorder,
     * но и цепочки, и массовые операции). Порядок обработки становится FIFO по публикации —
     * детерминированнее прежнего. Для однопользовательского профиля пропускной способности
     * одного потока достаточно; {@code onScheduleGenerated} и без того обрабатывает всю пачку
     * размещений внутри одного вызова.</p>
     */
    @Bean("projectionExecutor")
    public Executor projectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("projection-");
        executor.initialize();
        return executor;
    }
}
