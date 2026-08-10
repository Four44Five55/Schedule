package ru.services.educator.dictionary;

import java.util.Arrays;
import java.util.Optional;

/**
 * Вид справочника регалий преподавателя.
 *
 * <p>Тот же приём, что у {@code BoardAxis}/{@code ExportAxis}/{@code ProjectionSource}: enum как
 * точка расширения. Три справочника имеют одну форму (имя, сокращение, порядок, активность) и
 * различаются только таблицей и тем, какое поле преподавателя на них ссылается — поэтому у них
 * один контроллер, один сервис и один экран, а не три копии CRUD'а.</p>
 *
 * <p><b>Задел под должность:</b> она добавляется новой константой здесь, новым
 * {@link EducatorDictionaryPort} и миграцией. Контроллер, сервис и экран не трогаются (OCP).</p>
 *
 * <p>{@code slug} — сегмент пути ({@code /api/educator-dictionaries/special-ranks}). Отдельно от
 * {@code name()} потому, что имя константы — деталь Java, а путь — часть контракта API.</p>
 */
public enum EducatorDictionaryKind {

    SPECIAL_RANK("special-ranks", "Специальные звания"),
    RANK_SERVICE("rank-services", "Род службы"),
    SCIENCE_BRANCH("science-branches", "Отрасли науки");

    private final String slug;
    private final String label;

    EducatorDictionaryKind(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String getSlug() {
        return slug;
    }

    public String getLabel() {
        return label;
    }

    /** Вид по сегменту пути; пусто — если такого справочника нет (контроллер отдаст 404). */
    public static Optional<EducatorDictionaryKind> bySlug(String slug) {
        return Arrays.stream(values())
                .filter(kind -> kind.slug.equalsIgnoreCase(slug))
                .findFirst();
    }
}
