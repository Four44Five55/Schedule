package ru.services.educator;

import ru.entity.Educator;
import ru.entity.dictionary.AbstractEducatorDictionary;
import ru.enums.AcademicDegree;
import ru.enums.AcademicTitle;

/**
 * Сборка входа для {@link EducatorTitles}: сущность → набор уже разрешённых сокращений.
 *
 * <p><b>Почему отдельный класс, а не метод форматтера.</b> Ровно та же граница, что у
 * {@code OrgUnitNodes} при {@code OrgUnitHierarchyRule}/{@code OrgUnitSubtree}: правило —
 * чистая функция без знания о JPA, а «откуда взять её вход» — отдельная, тоже общая
 * ответственность. Иначе сборка расползлась бы по потребителям (маппер и выгрузка) и разошлась,
 * как разошлись два описателя занятия (аудит §1.2). Это тот же пункт 1.1 аудита, закрытый в
 * зачатке.</p>
 *
 * <p>Класс знает про ленивые связи ровно одно: их надо трогать в транзакции. Оба потребителя это
 * обеспечивают — маппер работает внутри {@code @Transactional} сервиса, выгрузка читает
 * преподавателей своим запросом.</p>
 */
public final class EducatorCredentialsAssembler {

    private EducatorCredentialsAssembler() {
    }

    /**
     * Регалии преподавателя в виде сокращений. Ни одно поле не обязательно — «не указано»
     * легитимное состояние, поэтому здесь нет ни одной проверки на полноту.
     */
    public static EducatorTitles.Credentials from(Educator educator) {
        if (educator == null) return EducatorTitles.Credentials.EMPTY;

        AcademicDegree degree = educator.getAcademicDegree();
        AcademicTitle title = educator.getAcademicTitle();

        return new EducatorTitles.Credentials(
                shortNameOf(educator.getSpecialRank()),
                shortNameOf(educator.getRankService()),
                degree == null ? null : degree.getAbbreviation(),
                degree == null ? null : degree.getStandalone(),
                shortNameOf(educator.getScienceBranch()),
                title == null ? null : title.getAbbreviation()
        );
    }

    /**
     * Готовая подпись преподавателя: {@code «п-к юст Иванов И.И., ктн, доц»}.
     * Единственная точка, из которой её берут и карточка, и бланк выгрузки.
     */
    public static String lineOf(Educator educator) {
        if (educator == null) return "";
        return EducatorTitles.line(from(educator), educator.getName());
    }

    private static String shortNameOf(AbstractEducatorDictionary entry) {
        return entry == null ? null : entry.getShortName();
    }
}
