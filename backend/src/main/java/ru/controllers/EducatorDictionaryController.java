package ru.controllers;

import ru.exceptions.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.educatorDictionary.DictionaryEntryDto;
import ru.dto.educatorDictionary.DictionaryEntryFormDto;
import ru.services.educator.dictionary.EducatorDictionaryKind;
import ru.services.educator.dictionary.EducatorDictionaryService;

import java.util.Arrays;
import java.util.List;
import ru.exceptions.NotFoundException;

/**
 * Справочники регалий преподавателя: специальные звания, роды службы, отрасли науки.
 *
 * <p><b>Один контроллер на все виды</b> ({@code /api/educator-dictionaries/{kind}}): справочники
 * отличаются только таблицей, поэтому три копии CRUD-эндпоинтов были бы дублированием, а
 * добавление должности потребовало бы четвёртой. Вид приходит сегментом пути и разрешается в
 * {@link EducatorDictionaryKind}; неизвестный вид → 404.</p>
 *
 * <p>Степень (кандидат/доктор) и учёное звание (доцент/профессор) сюда <b>не</b> входят: у них по
 * два значения, заданных нормативкой, они живут Java-enum'ами и едут фронту через
 * {@code /api/enums/all} — тем же каналом, что виды занятий и виды подразделений.</p>
 */
@RestController
@RequestMapping("/api/educator-dictionaries")
@RequiredArgsConstructor
public class EducatorDictionaryController {

    private final EducatorDictionaryService dictionaryService;

    /** Какие справочники есть: вид, сегмент пути, человекочитаемое имя — для вкладок на фронте. */
    @GetMapping
    public ResponseEntity<List<DictionaryKindDto>> kinds() {
        return ResponseEntity.ok(Arrays.stream(EducatorDictionaryKind.values())
                .map(kind -> new DictionaryKindDto(kind.name(), kind.getSlug(), kind.getLabel()))
                .toList());
    }

    @GetMapping("/{kind}")
    public ResponseEntity<List<DictionaryEntryDto>> getAll(@PathVariable String kind) {
        return ResponseEntity.ok(dictionaryService.findAll(kindOf(kind)));
    }

    @GetMapping("/{kind}/{id}")
    public ResponseEntity<DictionaryEntryDto> getById(@PathVariable String kind, @PathVariable Integer id) {
        return dictionaryService.findById(kindOf(kind), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Занятое сокращение — {@code DuplicateException} и 409: разбор чужого файла по нему обязан быть однозначным. */
    @PostMapping("/{kind}")
    public ResponseEntity<DictionaryEntryDto> create(@PathVariable String kind,
                                                     @Valid @RequestBody DictionaryEntryFormDto form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dictionaryService.create(kindOf(kind), form));
    }

    @PutMapping("/{kind}/{id}")
    public ResponseEntity<DictionaryEntryDto> update(@PathVariable String kind,
                                                     @PathVariable Integer id,
                                                     @Valid @RequestBody DictionaryEntryFormDto form) {
        return ResponseEntity.ok(dictionaryService.update(kindOf(kind), id, form));
    }

    /**
     * Удаление. Строку, за которой числятся преподаватели, БД удалить не даст (RESTRICT), поэтому
     * сервис проверяет заранее: {@code InUseException} становится 409 с числом ссылающихся.
     */
    @DeleteMapping("/{kind}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String kind, @PathVariable Integer id) {
        dictionaryService.delete(kindOf(kind), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Вид справочника из сегмента пути; неизвестный — 404.
     *
     * <p>Разрешение вынесено сюда, а не повторено пятью проверками {@code isEmpty()}: из-за этой
     * пары строк все пять методов возвращали {@code ResponseEntity} без типа и теряли контракт
     * ответа. {@code NotFoundException} — тот же путь наружу, что у остальных «не найдено».</p>
     */
    private static EducatorDictionaryKind kindOf(String slug) {
        return EducatorDictionaryKind.bySlug(slug)
                .orElseThrow(() -> new NotFoundException("Справочника «" + slug + "» не существует"));
    }

    /**
     * Вид справочника для навигации на фронте.
     *
     * @param value имя константы
     * @param slug  сегмент пути ({@code special-ranks})
     * @param label человекочитаемое имя вкладки
     */
    public record DictionaryKindDto(String value, String slug, String label) {}
}
