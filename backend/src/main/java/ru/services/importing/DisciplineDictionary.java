package ru.services.importing;

import ru.services.importing.DisciplineFooterParser.FooterRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Единый ответ на вопрос «одна ли это дисциплина» по всей пачке файлов.
 *
 * <h2>Зачем отдельный класс</h2>
 * <p>Одну дисциплину в разных файлах сокращают по-разному, а полное название знает <b>только подвал
 * группового файла</b> — в аудиторной и преподавательской ячейке стоит голое обозначение. Значит
 * «та же дисциплина» определяется не строкой ячейки, а сводом подвалов всей пачки.</p>
 *
 * <p>Потребителей у этого правила <b>двое</b>: сверка со справочниками (чтобы не завести две
 * дисциплины на одну) и склейка разрезов (чтобы занятие из трёх файлов сошлось в одно). Две копии
 * правила разошлись бы при первой же правке — как уже разъезжались списки кодов аттестаций,
 * пока их не свели в {@code KindOfStudy.Category}.</p>
 *
 * <p><b>Чистая функция</b>: ни Spring, ни базы. Что из этого есть у нас в справочнике — вопрос
 * следующего слоя.</p>
 */
public final class DisciplineDictionary {

    /** Ключ обозначения → каноническое обозначение (представитель группы вариантов). */
    private final Map<String, String> canonicalByCode;

    /** Каноническое обозначение → все написания, которыми оно встретилось. */
    private final Map<String, List<String>> variantsByCanonical;

    /**
     * Ключ обозначения <b>или полного названия</b> → строка подвала (первая встреченная).
     *
     * <p>Названием тоже, потому что в ячейке дисциплина иногда написана полностью — так выгрузка
     * печатает экзамены. Пока входом служило только обозначение, у таких занятий не находился
     * подвал, а с ним и преподаватель: занятие уходило в блокеры «преподаватель не определён».</p>
     */
    private final Map<String, FooterRow> footerByCode;

    private DisciplineDictionary(Map<String, String> canonicalByCode,
                                 Map<String, List<String>> variantsByCanonical,
                                 Map<String, FooterRow> footerByCode) {
        this.canonicalByCode = canonicalByCode;
        this.variantsByCanonical = variantsByCanonical;
        this.footerByCode = footerByCode;
    }

    /**
     * Собирает словарь по всей пачке.
     *
     * <p>Группируются обозначения <b>по названию из подвала</b>: две записи «в базе нет» с одним и
     * тем же названием — это одна дисциплина, и заводить её дважды нельзя (имя уникально, вторая
     * вставка роняет весь прогон). Названия нет — группируем по самому обозначению: сводить по
     * догадке нечего.</p>
     */
    public static DisciplineDictionary of(List<ParsedSheet> sheets) {
        Map<String, FooterRow> footerByCode = new LinkedHashMap<>();
        for (ParsedSheet sheet : sheets) {
            for (FooterRow row : sheet.footer()) {
                footerByCode.putIfAbsent(key(row.code()), row);
                // Полное название — второй вход в ту же строку: в ячейке дисциплина иногда написана
                // именно им, а не обозначением (живой случай — экзамены). Без этого входа такая
                // ячейка не находила ни подвала, ни своих однофамильцев по коду.
                if (namable(row.name())) {
                    footerByCode.putIfAbsent(key(row.name()), row);
                }
            }
        }

        // Порядок встречи, а не алфавит: он повторяет порядок файлов, и найденное легче искать глазами.
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        for (String code : codes(sheets)) {
            FooterRow row = footerByCode.get(key(code));
            String name = row == null ? null : row.name();
            boolean namable = namable(name);
            byGroup.computeIfAbsent(namable ? "имя:" + key(name) : "код:" + key(code), ignored -> new ArrayList<>())
                    .add(code.trim());
        }

        Map<String, String> canonicalByCode = new LinkedHashMap<>();
        Map<String, List<String>> variantsByCanonical = new LinkedHashMap<>();
        for (List<String> group : byGroup.values()) {
            List<String> variants = List.copyOf(new LinkedHashSet<>(group));
            // Представитель — самое короткое обозначение: это оно и есть, а длинное чаще всего
            // название, попавшее в колонку «Обозн» (живой случай — индекс плана «ДС.1.О»).
            String canonical = variants.stream().min(Comparator.comparingInt(String::length)).orElseThrow();
            variantsByCanonical.put(canonical, variants);
            variants.forEach(variant -> canonicalByCode.put(key(variant), canonical));
        }
        return new DisciplineDictionary(canonicalByCode, variantsByCanonical, footerByCode);
    }

    /** Все обозначения пачки: сначала подвалы (там есть названия), затем ячейки. */
    private static List<String> codes(List<ParsedSheet> sheets) {
        List<String> found = new ArrayList<>();
        sheets.forEach(sheet -> sheet.footer().forEach(row -> found.add(row.code())));
        sheets.forEach(sheet -> CellDialect.readAll(sheet).forEach(lesson -> found.add(lesson.discipline())));
        return found.stream().filter(code -> code != null && !code.isBlank()).toList();
    }

    /**
     * Каноническое обозначение дисциплины.
     *
     * @return представитель группы написаний; для незнакомого обозначения — оно само (обрезанное),
     * а не {@code null}: склейка обязана работать и по файлам без подвала
     */
    public String canonicalOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return canonicalByCode.getOrDefault(key(code), code.trim());
    }

    /** Полное название из подвала либо {@code null}, если подвал такого обозначения не знает. */
    public String nameOf(String code) {
        FooterRow row = footerOf(code);
        return row == null || !namable(row.name()) ? null : row.name();
    }

    /**
     * Годится ли строка подвала на роль названия дисциплины.
     *
     * <p>Пустое не годится очевидно, а индекс плана («ДС.1.О») — потому, что это не название, а
     * номер строки учебного плана, случайно попавший в колонку.</p>
     */
    static boolean namable(String name) {
        return name != null && !name.isBlank() && !DisciplineFooterParser.looksLikePlanIndex(name);
    }

    /** Строка подвала по любому написанию обозначения. */
    public FooterRow footerOf(String code) {
        return code == null ? null : footerByCode.get(key(code));
    }

    /** Все написания одного обозначения; для канонического, которого нет в словаре, — оно само. */
    public List<String> variantsOf(String canonical) {
        return variantsByCanonical.getOrDefault(canonical, canonical == null ? List.of() : List.of(canonical));
    }

    /** Ключ сравнения обозначений: регистр и краевые пробелы — оформление, а не различие. */
    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
