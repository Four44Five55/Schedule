package ru.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Печатает при старте, к какой базе подключились.
 *
 * <p><b>Зачем строка в логе.</b> Имя базы стало параметром ({@code DB_NAME}), чтобы эксперименты с
 * импортом шли в копии, а не в боевых данных. Но у переключаемого подключения есть своя цена:
 * «я думала, что играю в тестовой» — ошибка, которая обнаруживается уже по испорченным данным.
 * Одна явная строка при старте закрывает вопрос раньше, чем он возникнет.</p>
 *
 * <p>Пишется на {@code WARN}, если база не та, что по умолчанию: переключение — состояние
 * необычное и временное, и в потоке лога оно должно быть заметно.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseIdentityLogger {

    /** Боевая база. Всё остальное — эксперимент, и об этом стоит сказать громче. */
    private static final String PRODUCTION_DATABASE = "schedule_db";

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectedDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String url = meta.getURL();
            String database = connection.getCatalog();

            if (PRODUCTION_DATABASE.equals(database)) {
                log.info("База данных: {} (пользователь {}), {}", database, meta.getUserName(), url);
            } else {
                log.warn("База данных: {} — НЕ боевая (пользователь {}), {}", database, meta.getUserName(), url);
            }
        } catch (SQLException e) {
            log.warn("Не удалось определить, к какой базе подключились: {}", e.getMessage());
        }
    }
}
