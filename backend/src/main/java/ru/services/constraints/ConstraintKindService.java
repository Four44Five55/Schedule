package ru.services.constraints;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.exceptions.InUseException;
import ru.exceptions.RuleViolationException;
import ru.dto.constraint.ConstraintKindDto;
import ru.dto.constraint.ConstraintKindFormDto;
import ru.entity.dictionary.KindOfConstraint;
import ru.enums.ConstraintMode;
import ru.repository.constraints.AuditoriumConstraintRepository;
import ru.repository.constraints.EducatorConstraintRepository;
import ru.repository.constraints.GroupConstraintRepository;
import ru.repository.dictionary.KindOfConstraintRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Справочник видов ограничений: чтение, заведение, правка, удаление.
 *
 * <p>Перечень ведёт пользователь — вид ограничения это подпись и цвет, код по нему не ветвится
 * (правило «кто владеет списком» — в docs/CONVENTIONS.md). Поэтому здесь обычный CRUD, а не enum.</p>
 */
@Service
@RequiredArgsConstructor
public class ConstraintKindService {

    /**
     * Цвет по умолчанию — нейтральный.
     *
     * <p>Списка допустимых цветов здесь НЕТ намеренно. Палитрой владеет фронт: он выбирает ключи,
     * знает соответствующие им CSS-классы и рисует. Держать копию списка на бэке значило бы
     * завести второй источник правды о чужом слое — ровно то дублирование, из-за которого раньше
     * разъезжались списки аттестаций. Бэк хранит ключ как строку и о его смысле не судит;
     * неизвестный ключ фронт покажет нейтральным цветом (fallback в {@code constraintStyles.ts}),
     * а выбирать произвольные значения пользователю негде — в редакторе только палитра.</p>
     */
    private static final String DEFAULT_COLOR = "slate";
    /** Префикс кода для видов, заведённых пользователем; человеку не показывается. */
    private static final String USER_CODE_PREFIX = "USER_";

    private final KindOfConstraintRepository repository;
    private final EducatorConstraintRepository educatorConstraints;
    private final GroupConstraintRepository groupConstraints;
    private final AuditoriumConstraintRepository auditoriumConstraints;

    @Transactional(readOnly = true)
    public List<ConstraintKindDto> findAll() {
        // Использования считаем ОДИН раз на весь список (три GROUP BY), а не по три COUNT на
        // каждый вид: справочник читается при каждом открытии рабочего места ограничений.
        Map<String, Long> usage = usageByCode();
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(kind -> toDto(kind, usage.getOrDefault(kind.getCode(), 0L)))
                .toList();
    }

    /** Карта «код вида → сколько им размечено» по всем трём видам ресурсов. */
    private Map<String, Long> usageByCode() {
        Map<String, Long> total = new HashMap<>();
        Stream.of(educatorConstraints.countGroupedByKind(),
                        groupConstraints.countGroupedByKind(),
                        auditoriumConstraints.countGroupedByKind())
                .flatMap(List::stream)
                .forEach(row -> total.merge(row.getCode(), row.getCount(), Long::sum));
        return total;
    }

    @Transactional
    public ConstraintKindDto create(ConstraintKindFormDto form) {
        requireFreeLabels(form, null);

        KindOfConstraint kind = new KindOfConstraint();
        kind.setCode(nextUserCode());
        kind.setSystem(false);
        apply(kind, form);
        KindOfConstraint saved = repository.save(kind);
        return toDto(saved, usageCount(saved.getCode()));
    }

    @Transactional
    public ConstraintKindDto update(String code, ConstraintKindFormDto form) {
        KindOfConstraint kind = repository.findById(code)
                .orElseThrow(() -> new NotFoundException("Вид ограничения не найден: " + code));
        requireFreeLabels(form, code);

        // Системным правим всё, кроме кода: название «Командировка» можно переименовать в «Убытие»,
        // а вот код BUSINESS_TRIP держит уже проставленные ограничения.
        apply(kind, form);
        KindOfConstraint saved = repository.save(kind);
        return toDto(saved, usageCount(saved.getCode()));
    }

    /**
     * Удаление. Вид, которым что-то размечено, не удаляем — вернём число ссылающихся, чтобы отказ
     * был осмысленным, а не сырой ошибкой внешнего ключа. Системные виды не удаляются вовсе:
     * их коды могли попасть в чужие выгрузки и в старые данные, а погасить вид можно и так.
     */
    @Transactional
    public void delete(String code) {
        KindOfConstraint kind = repository.findById(code)
                .orElseThrow(() -> new NotFoundException("Вид ограничения не найден: " + code));

        if (kind.isSystem()) {
            throw new RuleViolationException(
                    "Вид «" + kind.getName() + "» пришёл из кода и не удаляется. Его можно погасить: "
                            + "погашенный не предлагается при вводе, но уже проставленные ограничения сохранятся.");
        }

        long used = usageCount(code);
        if (used > 0) {
            throw new InUseException(
                    "Видом «" + kind.getName() + "» размечено ограничений: " + used
                            + ". Удаление запрещено — сначала перенесите их на другой вид или погасите этот.");
        }

        repository.delete(kind);
    }

    private void apply(KindOfConstraint kind, ConstraintKindFormDto form) {
        kind.setName(form.name().trim());
        kind.setShortName(form.shortName().trim());
        kind.setColor(resolveColor(form.color()));
        kind.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        kind.setActive(form.active() == null || form.active());
        // Не задан — запрещает всё. Умолчание строгое намеренно: забытое поле не должно молча
        // открывать интервал для аттестаций.
        kind.setMode(form.mode() == null ? ConstraintMode.BLOCKING : form.mode());
    }

    private String resolveColor(String color) {
        if (color == null || color.isBlank()) return DEFAULT_COLOR;
        return color.trim().toLowerCase();
    }

    /**
     * Название и сокращение уникальны: по сокращению вид узнают в ячейке сетки и в бланке,
     * два одинаковых сделали бы расписание неоднозначным. Проверяем до вставки, чтобы вернуть
     * 400 с человеческим текстом, а не 500 от нарушения UNIQUE.
     */
    private void requireFreeLabels(ConstraintKindFormDto form, String selfCode) {
        Optional<KindOfConstraint> byName = repository.findByNameIgnoreCase(form.name().trim());
        if (byName.isPresent() && !byName.get().getCode().equals(selfCode)) {
            throw new DuplicateException("Вид с названием «" + form.name().trim() + "» уже есть");
        }
        Optional<KindOfConstraint> byShort = repository.findByShortNameIgnoreCase(form.shortName().trim());
        if (byShort.isPresent() && !byShort.get().getCode().equals(selfCode)) {
            throw new DuplicateException("Сокращение «" + form.shortName().trim() + "» уже занято");
        }
    }

    /** Следующий свободный служебный код: USER_1, USER_2, … */
    private String nextUserCode() {
        int next = 1;
        while (repository.existsById(USER_CODE_PREFIX + next)) {
            next++;
        }
        return USER_CODE_PREFIX + next;
    }

    private long usageCount(String code) {
        return educatorConstraints.countByKindOfConstraint_Code(code)
                + groupConstraints.countByKindOfConstraint_Code(code)
                + auditoriumConstraints.countByKindOfConstraint_Code(code);
    }

    private ConstraintKindDto toDto(KindOfConstraint kind, long usageCount) {
        return new ConstraintKindDto(
                kind.getCode(),
                kind.getName(),
                kind.getShortName(),
                kind.getColor(),
                kind.getSortOrder(),
                kind.isActive(),
                kind.isSystem(),
                usageCount,
                kind.getMode());
    }
}
