package ru.services.importing;

import java.util.List;

/**
 * Отчёт сверки: что из файлов нашлось в нашей базе, а что нет. <b>Ни одной записи не делается.</b>
 *
 * <p>Это первый прогон импорта по решению И-10. Асимметрия необратимости жёсткая: завести
 * преподавателя или дисциплину легко, убрать — нет (как только на сущность сошлётся назначение,
 * удаление упрётся в {@code RESTRICT} и отдаст сырой 500). А дубль преподавателя разрежет
 * расписание надвое тише и вреднее, чем кривые занятия. Поэтому сначала отчёт, создание — после
 * подтверждения человеком.</p>
 *
 * <p><b>Форма у всех разделов одна</b> — источник, что о нём понял разбор, наша сущность (или её
 * отсутствие) и причина. Разделы отличаются только тем, откуда берётся источник и по чему идёт
 * поиск, поэтому четыре разных отчёта здесь были бы четырьмя копиями одной таблицы.</p>
 *
 * @param sections разделы сверки: преподаватели, группы, дисциплины, аудитории
 */
public record ImportMatchReport(List<MatchSection> sections) {

    /**
     * Раздел сверки по одной категории.
     *
     * @param title   заголовок: «Преподаватели»
     * @param hint    чем сопоставляли — чтобы «не сопоставлено» читалось без чтения кода
     * @param total   сколько разных значений встретилось в файлах
     * @param matched сколько из них нашлось
     * @param rows    строки; несопоставленные идут первыми — это то, что требует действия
     */
    public record MatchSection(
            String title,
            String hint,
            int total,
            int matched,
            List<MatchRow> rows
    ) {
    }

    /**
     * Что стало со значением из файла.
     *
     * <p><b>Отдельное поле, а не разбор текста замечания.</b> Заводить недостающее можно только по
     * {@link #MISSING}: «несколько кандидатов» — это незнание, а не отсутствие, и завести ещё одну
     * строку значило бы сделать неоднозначность вечной.</p>
     */
    public enum MatchStatus {
        /** Нашлось ровно одно. */
        MATCHED,
        /** В базе нет — можно завести. */
        MISSING,
        /** Кандидатов несколько: выбирает человек, автоматика молчит. */
        AMBIGUOUS,
        /** Разбор не понял само значение — заводить нечего. */
        UNREADABLE
    }

    /**
     * Одно значение из файла и его судьба.
     *
     * @param source      значение как в файле: «АСКС», «п/п-к Чащин С.В.», «252-3», «911»
     * @param detail      что о нём понял разбор: «корпус 3», «ф. 9, набор 2025, каф. 91»
     * @param status      сопоставлено / нет / неоднозначно / не прочитано
     * @param matchedId   id нашей сущности либо {@code null}
     * @param matchedName как она называется у нас (может отличаться от файла — это и надо увидеть)
     * @param note        причина, по которой не сопоставлено, либо расхождение при совпадении
     */
    public record MatchRow(
            String source,
            String detail,
            MatchStatus status,
            Integer matchedId,
            String matchedName,
            String note
    ) {
        public boolean isMatched() {
            return status == MatchStatus.MATCHED;
        }

        static MatchRow matched(String source, String detail, Integer id, String name, String note) {
            return new MatchRow(source, detail, MatchStatus.MATCHED, id, name, note);
        }

        static MatchRow missing(String source, String detail, String note) {
            return new MatchRow(source, detail, MatchStatus.MISSING, null, null, note);
        }

        static MatchRow ambiguous(String source, String detail, String note) {
            return new MatchRow(source, detail, MatchStatus.AMBIGUOUS, null, null, note);
        }

        static MatchRow unreadable(String source, String detail, String note) {
            return new MatchRow(source, detail, MatchStatus.UNREADABLE, null, null, note);
        }
    }
}
