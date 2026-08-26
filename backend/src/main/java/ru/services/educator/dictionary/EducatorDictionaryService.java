package ru.services.educator.dictionary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exceptions.NotFoundException;
import ru.exceptions.DuplicateException;
import ru.exceptions.InUseException;
import ru.dto.educatorDictionary.DictionaryEntryDto;
import ru.dto.educatorDictionary.DictionaryEntryFormDto;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Справочники регалий преподавателя: один сервис на все виды.
 *
 * <p><b>Реестр, а не {@code switch}.</b> Spring отдаёт сюда все объявленные
 * {@link EducatorDictionaryPort}, и сервис раскладывает их по {@link EducatorDictionaryKind}.
 * Новый справочник (должность) — это новый {@code @Component}: ни здесь, ни в контроллере, ни на
 * экране править нечего (OCP). Ровно тот же приём, каким устроены enum-стратегии проекта.</p>
 *
 * <p><b>Что здесь, а не в порте:</b> правила, одинаковые для всех справочников, — уникальность
 * сокращения и запрет удаления занятой строки. Порт отвечает только за доступ к своей таблице.</p>
 */
@Service
public class EducatorDictionaryService {

    private final Map<EducatorDictionaryKind, EducatorDictionaryPort<?>> ports =
            new EnumMap<>(EducatorDictionaryKind.class);

    public EducatorDictionaryService(List<EducatorDictionaryPort<?>> ports) {
        for (EducatorDictionaryPort<?> port : ports) {
            this.ports.put(port.kind(), port);
        }
    }

    @Transactional(readOnly = true)
    public List<DictionaryEntryDto> findAll(EducatorDictionaryKind kind) {
        return port(kind).findAll();
    }

    @Transactional(readOnly = true)
    public Optional<DictionaryEntryDto> findById(EducatorDictionaryKind kind, Integer id) {
        return port(kind).findById(id);
    }

    @Transactional
    public DictionaryEntryDto create(EducatorDictionaryKind kind, DictionaryEntryFormDto form) {
        requireUniqueShortName(kind, form, null);
        return port(kind).create(form);
    }

    @Transactional
    public DictionaryEntryDto update(EducatorDictionaryKind kind, Integer id, DictionaryEntryFormDto form) {
        requireUniqueShortName(kind, form, id);
        return port(kind).update(id, form);
    }

    /**
     * Удаление строки справочника.
     *
     * <p>Занятую строку удалять нельзя — FK стоят с {@code ON DELETE RESTRICT}, и без этой
     * проверки пользователь получил бы сырой 500 на коммите. Цена названа заранее: число
     * ссылающихся едет в списке ({@code educatorCount}), а отказ приходит осмысленным 409.</p>
     *
     * <p>Мягкая альтернатива — {@code active = false}: устаревшее звание исчезает из выбора, но
     * люди сохраняют его в истории. Та же развилка, что у подразделений.</p>
     *
     * @throws IllegalStateException если на строку ссылаются преподаватели (контроллер → 409)
     */
    @Transactional
    public void delete(EducatorDictionaryKind kind, Integer id) {
        EducatorDictionaryPort<?> port = port(kind);
        if (!port.exists(id)) {
            throw new NotFoundException(
                    "Строка справочника «" + kind.getLabel() + "» с id=" + id + " не найдена.");
        }
        long used = port.countEducators(id);
        if (used > 0) {
            throw new InUseException(
                    "Удаление невозможно: значение указано у " + used + " преподавателей. "
                            + "Снимите его у них или сделайте значение неактивным.");
        }
        port.deleteById(id);
    }

    /**
     * Сокращение уникально в пределах справочника. Проверяем в коде, а не только UNIQUE-индексом:
     * иначе пользователь увидел бы сырую ошибку БД вместо объяснения. Причина требования не
     * косметическая — по сокращению собирается подпись преподавателя и будет опознаваться звание
     * при разборе чужого файла на импорте, а два одинаковых сокращения сделали бы разбор
     * неоднозначным.
     */
    private void requireUniqueShortName(EducatorDictionaryKind kind,
                                        DictionaryEntryFormDto form,
                                        Integer excludeId) {
        String candidate = form.shortName().trim();
        boolean taken = port(kind).findAll().stream()
                .filter(entry -> excludeId == null || !excludeId.equals(entry.id()))
                .anyMatch(entry -> entry.shortName().equalsIgnoreCase(candidate));
        if (taken) {
            throw new DuplicateException(
                    "Сокращение «" + candidate + "» уже занято в справочнике «" + kind.getLabel() + "».");
        }
    }

    private EducatorDictionaryPort<?> port(EducatorDictionaryKind kind) {
        EducatorDictionaryPort<?> port = ports.get(kind);
        if (port == null) {
            throw new IllegalStateException("Справочник «" + kind + "» не подключён.");
        }
        return port;
    }
}
