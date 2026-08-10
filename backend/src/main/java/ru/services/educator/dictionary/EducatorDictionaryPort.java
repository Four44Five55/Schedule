package ru.services.educator.dictionary;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.dto.educatorDictionary.DictionaryEntryDto;
import ru.dto.educatorDictionary.DictionaryEntryFormDto;
import ru.entity.dictionary.AbstractEducatorDictionary;

import java.util.List;
import java.util.Optional;

/**
 * Доступ к одному справочнику регалий. <b>Template Method (GoF):</b> вся механика CRUD и сборки
 * DTO живёт здесь один раз, а наследник называет только своё — вид, репозиторий, конструктор
 * сущности и способ посчитать ссылающихся преподавателей.
 *
 * <p><b>Зачем так, а не три обычных сервиса.</b> Справочников три (скоро четыре — должность), и
 * они отличаются исключительно таблицей. Скопировать CRUD трижды — это ровно та находка аудита
 * §1.5 («~10 форм-модалок с одинаковым каркасом»), только на бэке; добавление должности стоило бы
 * четвёртой копии. Здесь новый справочник — один {@code @Component} на два десятка строк, а
 * контроллер, сервис и экран не трогаются (OCP).</p>
 *
 * <p><b>Почему обобщение по типу, а не одна таблица с колонкой «вид».</b> Общая таблица лишила бы
 * БД возможности отличить звание от отрасли: {@code educator.special_rank_id} мог бы указывать на
 * строку-отрасль. Инвариант должен держать FK — обобщается код, а не схема.</p>
 *
 * <p>Публичные методы намеренно не упоминают {@code T} в сигнатурах: сервис хранит порты как
 * {@code EducatorDictionaryPort<?>} и зовёт их, не зная конкретного типа.</p>
 *
 * @param <T> сущность конкретного справочника
 */
public abstract class EducatorDictionaryPort<T extends AbstractEducatorDictionary> {

    /** Какой справочник обслуживает порт — ключ в реестре {@link EducatorDictionaryService}. */
    public abstract EducatorDictionaryKind kind();

    protected abstract JpaRepository<T, Integer> repository();

    /** Пустая сущность нужного типа: {@code new SpecialRank()}. */
    protected abstract T newEntry();

    /** Строки справочника в порядке отображения (звания — по старшинству, не по алфавиту). */
    protected abstract List<T> findAllOrdered();

    /**
     * Сколько преподавателей ссылается на строку. У каждого справочника своя колонка на
     * {@code educator}, поэтому вопрос задаётся здесь, а не общим запросом.
     */
    public abstract long countEducators(Integer id);

    public List<DictionaryEntryDto> findAll() {
        return findAllOrdered().stream().map(this::toDto).toList();
    }

    public Optional<DictionaryEntryDto> findById(Integer id) {
        return repository().findById(id).map(this::toDto);
    }

    public DictionaryEntryDto create(DictionaryEntryFormDto form) {
        T entry = newEntry();
        apply(entry, form);
        return toDto(repository().save(entry));
    }

    public DictionaryEntryDto update(Integer id, DictionaryEntryFormDto form) {
        T entry = repository().findById(id).orElseThrow(() -> notFound(id));
        apply(entry, form);
        return toDto(repository().save(entry));
    }

    /**
     * Удаление. Проверку «за строкой числятся люди» ведёт {@link EducatorDictionaryService} — она
     * одинакова для всех справочников, а порт отвечает только за доступ к своему.
     */
    public void deleteById(Integer id) {
        if (!repository().existsById(id)) throw notFound(id);
        repository().deleteById(id);
    }

    public boolean exists(Integer id) {
        return repository().existsById(id);
    }

    private void apply(T entry, DictionaryEntryFormDto form) {
        entry.setName(form.name().trim());
        entry.setShortName(form.shortName().trim());
        entry.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        // null → активна: новое значение должно сразу попадать в выбор, иначе его «не видно».
        entry.setActive(form.active() == null || form.active());
    }

    private DictionaryEntryDto toDto(T entry) {
        long used = countEducators(entry.getId());
        return new DictionaryEntryDto(
                entry.getId(),
                entry.getName(),
                entry.getShortName(),
                entry.getSortOrder(),
                entry.isActive(),
                used,
                used == 0
        );
    }

    private EntityNotFoundException notFound(Integer id) {
        return new EntityNotFoundException(
                "Строка справочника «" + kind().getLabel() + "» с id=" + id + " не найдена.");
    }
}
