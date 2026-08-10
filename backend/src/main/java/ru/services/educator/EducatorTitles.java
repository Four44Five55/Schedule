package ru.services.educator;

import java.util.ArrayList;
import java.util.List;

/**
 * Подпись преподавателя с регалиями: {@code «п-к юст Иванов И.И., к.т.н., доц»}.
 *
 * <p><b>Чистая функция без Spring и БД</b> — по образцу {@code OrgUnitSubtree},
 * {@code LessonOrderRule}, {@code AuditoriumUsageRule}: доменное правило выносится в класс,
 * который покрывается быстрыми тестами и не зависит от того, откуда приехали значения.</p>
 *
 * <p><b>Зачем один владелец формата.</b> Потребителей у подписи двое — карточка преподавателя и
 * таблица «Обозначения» в выгрузке. Проект уже наступал на это в малом: {@code describe} в
 * {@code AuditoriumViolationService} и {@code describeOccupant} в
 * {@code PlacementAuditoriumService} строят одну и ту же строку «дисциплина · поток» из разных
 * корней и разошлись на первой же неделе (аудит §1.2). Здесь склейка одна, и фронт её не
 * повторяет — он получает готовую строку.</p>
 *
 * <p><b>Вход — уже разрешённые строки, а не сущности и не enum'ы.</b> Функции всё равно, что
 * звание приехало из справочника, а учёное звание из enum'а: решение «что справочник, а что
 * enum» остаётся обратимым и не протекает сюда (DIP).</p>
 *
 * <p><b>Части, а не шаблон.</b> Подпись собирается из упорядоченного списка кусков: приставки
 * перед ФИО, хвост после. Поэтому будущая должность добавляется одним куском, а не переписыванием
 * склейки.</p>
 *
 * <p><b>Точки.</b> В данных сокращения хранятся без точек (решение заказчика): «п-к», «юст»,
 * «доц», «т». Единственное место, где точки нужны по форме, — составное сокращение степени
 * («к.т.н.»), и расставляются они здесь: точка — часть формата, а не часть данных.</p>
 */
public final class EducatorTitles {

    private EducatorTitles() {
    }

    /**
     * Регалии преподавателя, уже разрешённые в сокращения (любое поле может быть {@code null}).
     *
     * @param rankShort    сокращение специального звания: «п-к»
     * @param serviceShort сокращение рода службы: «юст»; печатается только вместе со званием
     * @param degreeShort  буквенная часть степени: «к»
     * @param degreeStandalone степень без отрасли: «канд наук»
     * @param branchShort  буквенная часть отрасли науки: «т»
     * @param titleShort   сокращение учёного звания: «доц»
     */
    public record Credentials(
            String rankShort,
            String serviceShort,
            String degreeShort,
            String degreeStandalone,
            String branchShort,
            String titleShort
    ) {
        /** Пустые регалии: подпись сведётся к одному ФИО. */
        public static final Credentials EMPTY = new Credentials(null, null, null, null, null, null);
    }

    /**
     * Полная подпись: {@code [звание [служба]] ФИО[, степень][, учёное звание]}.
     *
     * <p>Ни одна часть не обязательна. Без регалий возвращается само ФИО — без ведущих пробелов
     * и висящих запятых.</p>
     *
     * @param credentials регалии; {@code null} равносилен пустым
     * @param fullName    ФИО преподавателя
     * @return подпись; пустая строка, если ФИО пустое
     */
    public static String line(Credentials credentials, String fullName) {
        String name = trimToNull(fullName);
        if (name == null) return "";
        if (credentials == null) return name;

        String prefix = rankPrefix(credentials);
        String head = prefix.isEmpty() ? name : prefix + " " + name;

        List<String> tail = new ArrayList<>();
        String degree = degreeAbbreviation(credentials);
        if (!degree.isEmpty()) tail.add(degree);
        String title = trimToNull(credentials.titleShort());
        if (title != null) tail.add(title);

        return tail.isEmpty() ? head : head + ", " + String.join(", ", tail);
    }

    /**
     * Приставка перед фамилией: «п-к» либо «п-к юст».
     *
     * <p>Служба без звания не печатается: «юстиции Иванов» — не подпись, а обрывок. Пустая
     * строка, если звания нет.</p>
     */
    public static String rankPrefix(Credentials credentials) {
        if (credentials == null) return "";
        String rank = trimToNull(credentials.rankShort());
        if (rank == null) return "";
        String service = trimToNull(credentials.serviceShort());
        return service == null ? rank : rank + " " + service;
    }

    /**
     * Составное сокращение степени: «к.т.н.», «к.ф-м.н.», «д.т.н.».
     *
     * <p>Отрасль без уровня — не степень (одни «технические» ничего не значат), поэтому в таком
     * случае пусто. Уровень без отрасли даёт «канд наук»: записи «к.н.» не существует.</p>
     */
    public static String degreeAbbreviation(Credentials credentials) {
        if (credentials == null) return "";
        String degree = trimToNull(credentials.degreeShort());
        if (degree == null) return "";
        String branch = trimToNull(credentials.branchShort());
        if (branch == null) {
            String standalone = trimToNull(credentials.degreeStandalone());
            return standalone == null ? "" : standalone;
        }
        return degree + "." + branch + ".н.";
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
