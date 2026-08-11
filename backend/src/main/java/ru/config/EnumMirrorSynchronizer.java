package ru.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.enums.AcademicDegree;
import ru.enums.AcademicTitle;
import ru.enums.KindOfStudy;
import ru.enums.OrgUnitType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Наполняет справочники-«зеркала» Java-enum'ов при старте приложения.
 *
 * <h2>Зачем</h2>
 * Часть enum'ов продублирована таблицами ({@code kind_of_study}, {@code org_unit_type},
 * {@code academic_degree}, {@code academic_title}) — ради внешних ключей и читаемости сырого SQL.
 * Пока эти строки заводились вручную, зеркало могло разойтись с кодом, и однажды разошлось:
 * на чистой машине таблица {@code kind_of_study} осталась пустой (её наполнял файл демо-данных,
 * закрытый {@code .gitignore}), и добавление занятия падало по внешнему ключу — «вида LECTURE
 * не существует». Теперь БД получает значения из кода автоматически, и рассинхрон невозможен:
 * добавить вид занятия — значит дописать строку в enum, больше ничего.
 *
 * <h2>Правила, без которых это ломается</h2>
 * <ul>
 *   <li><b>Только upsert, никогда DELETE.</b> Константа, исчезнувшая из enum, остаётся в таблице:
 *       на неё могут ссылаться старые записи, и FK {@code RESTRICT} тут защита, а не помеха.
 *       О таких «осиротевших» строках синхронизатор предупреждает в лог, но не трогает их —
 *       удаление стоит денег (данные) и делается осознанно, миграцией.</li>
 *   <li><b>Переименование константы этим не лечится.</b> {@code LECTURE → LECTURES} синхронизатор
 *       воспримет как новое значение, а старые строки останутся с прежним ключом. Переименование —
 *       всегда миграция с {@code UPDATE} по ссылающимся таблицам.</li>
 *   <li><b>Метки обновляются, ключ — нет.</b> Правка русского названия или аббревиатуры в enum
 *       доезжает до БД сама; {@code enum_name} — первичный ключ и неизменен.</li>
 * </ul>
 *
 * <h2>Почему ApplicationRunner</h2>
 * Liquibase отрабатывает при инициализации {@code DataSource}, то есть строго до готовности
 * контекста, а runner'ы — сразу после неё. Значит таблицы уже созданы, а первые запросы ещё не
 * обслужены. DDL по-прежнему только за Liquibase — здесь пишутся исключительно данные-проекция
 * кода, не схема и не данные пользователя.
 *
 * <p>Миграция {@code 021-seed-kind-of-study.sql} после появления этого класса избыточна, но
 * оставлена: она уже применена на существующих базах, а повторный upsert ничего не портит.</p>
 *
 * <p>SQL здесь постгресовый ({@code ON CONFLICT}), как и все миграции проекта. Имена таблиц и
 * колонок склеиваются в строку, но берутся только из констант ниже — пользовательский ввод сюда
 * не попадает.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnumMirrorSynchronizer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    /**
     * Одно зеркало: таблица, её колонки-значения (кроме ключа {@code enum_name}) и строки из enum.
     * Первый элемент каждой строки — всегда {@code enum_name}.
     */
    private record Mirror(String table, List<String> valueColumns, List<Object[]> rows) {
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Mirror> mirrors = mirrors();
        for (Mirror mirror : mirrors) {
            sync(mirror);
            warnAboutOrphans(mirror);
        }
        log.info("↔ Справочники-зеркала enum'ов синхронизированы: {}",
                mirrors.stream()
                        .map(m -> m.table() + "=" + m.rows().size())
                        .collect(Collectors.joining(", ")));
    }

    /**
     * Список зеркал. Добавил enum с таблицей-справочником — допиши сюда строку, и наполнение
     * перестанет быть твоей заботой.
     */
    private List<Mirror> mirrors() {
        return List.of(
                new Mirror("kind_of_study", List.of("full_name", "abbreviation_name"),
                        Arrays.stream(KindOfStudy.values())
                                .map(e -> new Object[]{e.name(), e.getFullName(), e.getAbbreviationName()})
                                .toList()),
                new Mirror("org_unit_type", List.of("full_name", "abbreviation_name", "nesting_rank"),
                        Arrays.stream(OrgUnitType.values())
                                .map(e -> new Object[]{e.name(), e.getFullName(), e.getAbbreviationName(), e.getNestingRank()})
                                .toList()),
                new Mirror("academic_degree", List.of("full_name", "abbreviation_name"),
                        Arrays.stream(AcademicDegree.values())
                                .map(e -> new Object[]{e.name(), e.getFullName(), e.getAbbreviation()})
                                .toList()),
                new Mirror("academic_title", List.of("full_name", "abbreviation_name"),
                        Arrays.stream(AcademicTitle.values())
                                .map(e -> new Object[]{e.name(), e.getFullName(), e.getAbbreviation()})
                                .toList()));
    }

    /**
     * Upsert всех значений enum-а одним батчем.
     *
     * <p>Падение здесь роняет старт приложения — намеренно. Единственная реальная причина
     * ошибки — две константы с одинаковой меткой (на {@code full_name} и {@code abbreviation_name}
     * стоит UNIQUE), и узнать об этом на старте лучше, чем ловить потом отказ вставки занятия.</p>
     */
    private void sync(Mirror mirror) {
        String columns = "enum_name, " + String.join(", ", mirror.valueColumns());
        String placeholders = "?" + ", ?".repeat(mirror.valueColumns().size());
        String updates = mirror.valueColumns().stream()
                .map(column -> column + " = EXCLUDED." + column)
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (enum_name) DO UPDATE SET %s"
                .formatted(mirror.table(), columns, placeholders, updates);

        jdbc.batchUpdate(sql, mirror.rows());
    }

    /**
     * Строки, которых в enum больше нет. Не удаляем — только сообщаем: почти всегда это след
     * переименованной или выброшенной константы, и решать, что делать с ссылающимися данными,
     * должен человек.
     */
    private void warnAboutOrphans(Mirror mirror) {
        Set<String> known = mirror.rows().stream()
                .map(row -> (String) row[0])
                .collect(Collectors.toCollection(HashSet::new));

        List<String> orphans = jdbc.queryForList("SELECT enum_name FROM " + mirror.table(), String.class)
                .stream()
                .filter(name -> !known.contains(name))
                .toList();

        if (!orphans.isEmpty()) {
            log.warn("⚠ В справочнике {} есть значения, которых нет в Java-enum: {}. Строки оставлены "
                            + "(на них могут ссылаться данные). Если это переименование — нужна миграция с UPDATE.",
                    mirror.table(), orphans);
        }
    }
}
