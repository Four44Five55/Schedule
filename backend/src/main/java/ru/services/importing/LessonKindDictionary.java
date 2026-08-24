package ru.services.importing;

import ru.enums.KindOfStudy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Обозначение вида занятия из чужой выгрузки → наш {@link KindOfStudy}.
 *
 * <h2>Порядок разрешения</h2>
 * <p>Сначала совпадение с нашей аббревиатурой ({@code «Л»} → лекция, {@code «ЛР»} → лабораторная,
 * {@code «КП»} → курсовой проект): свои обозначения объявлены один раз, в самом enum'е. Дальше —
 * {@link #ALIASES}, где живут только те написания, которые расходятся с нашими.</p>
 *
 * <p><b>Своего списка видов чужой программы мы не ведём:</b> незнакомое обозначение уходит строкой
 * отчёта, а не подстановкой «самого вероятного». В таблице лежит только то, о чём <b>сказал
 * заказчик</b>, — это сообщённый факт, а не догадка.</p>
 *
 * <h2>Один владелец на двоих</h2>
 * <p>Потребителей правила двое: расчёт плана (вид выбирает слот) и сведение разрезов (вид разводит
 * лектора и практика в подвале). Оба спрашивают здесь, а «лекция это или аттестация» объявляет сам
 * {@link KindOfStudy.Category} — единственный владелец классификации. Второе описание того же
 * знания разошлось бы с этим на первом же новом обозначении.</p>
 *
 * <p><b>Чистая функция</b>: ни Spring, ни базы.</p>
 */
public final class LessonKindDictionary {

    private LessonKindDictionary() {
    }

    /**
     * Написания чужой программы, расходящиеся с нашими аббревиатурами. <b>Только сообщённое
     * заказчиком</b> — угаданному здесь не место.
     *
     * <ul>
     *   <li>{@code «П»} — практическое занятие; у нас оно «ПЗ» (22368 занятий в живой выгрузке);</li>
     *   <li>{@code «КуР»} — курсовой проект; вторым его обозначением выгрузка пишет «КП», и оно
     *       совпадает с нашим, поэтому здесь не нужно.</li>
     * </ul>
     */
    private static final Map<String, KindOfStudy> ALIASES = Map.of(
            "п", KindOfStudy.PRACTICAL_WORK,
            "кур", KindOfStudy.COURSE_PROJECT);

    /** Наши аббревиатуры — объявлены в enum'е, копии здесь не заводим. */
    private static final Map<String, KindOfStudy> OURS = new HashMap<>();

    static {
        for (KindOfStudy kind : KindOfStudy.values()) {
            OURS.put(key(kind.getAbbreviationName()), kind);
        }
    }

    /**
     * Наш вид занятия по обозначению из файла.
     *
     * @param code обозначение как в ячейке: «Л», «ЛР», «П», «КуР»
     * @return вид либо {@code null} — обозначение неизвестно, и это строка отчёта: подставлять
     * «самый вероятный» вид нельзя, ошибка стала бы неотличима от данных
     */
    public static KindOfStudy of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String key = key(code);
        KindOfStudy ours = OURS.get(key);
        return ours != null ? ours : ALIASES.get(key);
    }

    /**
     * Лекция ли это по обозначению из файла.
     *
     * <p>Нужно сведению: подвал разводит лектора и практиков по виду занятия (И-14). Неизвестное
     * обозначение лекцией не считается — но и практикой не объявляется: об этом спрашивают
     * {@link #of}, который отвечает {@code null}.</p>
     */
    public static boolean isLecture(String code) {
        KindOfStudy kind = of(code);
        return kind != null && kind.isLectureType();
    }

    /**
     * Аттестация ли это (экзамен, зачёт) по обозначению из файла.
     *
     * <p>Тоже для подвала: экзамен принимает <b>тот, кто читал курс</b>, поэтому аттестация идёт
     * к лектору, а не к практикам.</p>
     */
    public static boolean isAssessment(String code) {
        KindOfStudy kind = of(code);
        return kind != null && kind.isAssessment();
    }

    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
