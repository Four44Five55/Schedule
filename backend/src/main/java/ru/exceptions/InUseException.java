package ru.exceptions;

import java.util.Map;

/**
 * Удалить нельзя: на строку ссылаются, и удаление унесло бы с собой чужие данные.
 *
 * <p>Ожидаемый исход, и притом такой, о котором проект старается сообщать <b>заранее</b>, а не
 * броском: {@code delete-impact}, {@code usageCount}, {@code deletable} существуют ровно для того,
 * чтобы цена удаления была названа до нажатия. Это исключение — вторая линия: оно срабатывает,
 * когда между показом цены и удалением что-то изменилось, либо когда путь удаления предпросмотра
 * не имеет.</p>
 *
 * <p><b>Не путать с {@link DuplicateException}</b> — см. там же. И не путать с каскадом: если
 * ссылающиеся строки положено уносить вместе с родителем, это не отказ, а предупреждение, и
 * решается оно предпросмотром, а не исключением.</p>
 *
 * @see #usageCount() сколько ссылок мешает — если считать дёшево
 */
public class InUseException extends DomainException {

    /**
     * Число ссылающихся объектов; {@code null} — «не считали».
     *
     * <p>Отдельным полем, а не только внутри текста: клиенту бывает нужно число само по себе
     * (счётчик, подсветка), и вынимать его разбором русской фразы — это ровно то склеивание
     * смысла из презентации, от которого проект уходит везде.</p>
     */
    private final Integer usageCount;

    public InUseException(String message) {
        this(message, null);
    }

    public InUseException(String message, Integer usageCount) {
        super(message);
        this.usageCount = usageCount;
    }

    public Integer usageCount() {
        return usageCount;
    }

    /** Число ссылок уезжает клиенту отдельным полем — если его считали. */
    @Override
    public Map<String, Object> details() {
        return usageCount == null ? Map.of() : Map.of("usageCount", usageCount);
    }

    @Override
    public String code() {
        return "IN_USE";
    }
}
