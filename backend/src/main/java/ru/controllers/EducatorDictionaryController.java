package ru.controllers;

import jakarta.persistence.EntityNotFoundException;
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
import java.util.Optional;

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
    public ResponseEntity<?> getAll(@PathVariable String kind) {
        Optional<EducatorDictionaryKind> resolved = EducatorDictionaryKind.bySlug(kind);
        if (resolved.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dictionaryService.findAll(resolved.get()));
    }

    @GetMapping("/{kind}/{id}")
    public ResponseEntity<?> getById(@PathVariable String kind, @PathVariable Integer id) {
        Optional<EducatorDictionaryKind> resolved = EducatorDictionaryKind.bySlug(kind);
        if (resolved.isEmpty()) return ResponseEntity.notFound().build();
        Optional<DictionaryEntryDto> entry = dictionaryService.findById(resolved.get(), id);
        return entry.isPresent() ? ResponseEntity.ok(entry.get()) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{kind}")
    public ResponseEntity<?> create(@PathVariable String kind,
                                    @Valid @RequestBody DictionaryEntryFormDto form) {
        Optional<EducatorDictionaryKind> resolved = EducatorDictionaryKind.bySlug(kind);
        if (resolved.isEmpty()) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(dictionaryService.create(resolved.get(), form));
        } catch (IllegalArgumentException e) {
            // Занятое сокращение: разбор чужого файла по нему обязан быть однозначным.
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{kind}/{id}")
    public ResponseEntity<?> update(@PathVariable String kind,
                                    @PathVariable Integer id,
                                    @Valid @RequestBody DictionaryEntryFormDto form) {
        Optional<EducatorDictionaryKind> resolved = EducatorDictionaryKind.bySlug(kind);
        if (resolved.isEmpty()) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(dictionaryService.update(resolved.get(), id, form));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Удаление. Строку, за которой числятся преподаватели, БД удалить не даст (RESTRICT), поэтому
     * отказ приходит осмысленным 409 с числом ссылающихся — а не сырым 500 после попытки.
     */
    @DeleteMapping("/{kind}/{id}")
    public ResponseEntity<?> delete(@PathVariable String kind, @PathVariable Integer id) {
        Optional<EducatorDictionaryKind> resolved = EducatorDictionaryKind.bySlug(kind);
        if (resolved.isEmpty()) return ResponseEntity.notFound().build();
        try {
            dictionaryService.delete(resolved.get(), id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
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
