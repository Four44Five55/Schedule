package ru.services.exporting;

import ru.entity.Group;
import ru.entity.OrgUnit;
import ru.services.importing.GroupNumberDecoder;

/**
 * Реквизиты шапки бланка группы: факультет и курс — те самые графы «ФАКУЛЬТЕТ» (K2) и «КУРС» (K3),
 * которые в бланке были подписаны, но никогда не заполнялись.
 *
 * <p>Чистая функция — ни Spring, ни БД: на вход приходят уже загруженные группа и её факультет,
 * на выход — две строки шапки. Поэтому правило «какой курс у группы» покрывается быстрым
 * юнит-тестом, а не проверяется глазами по выгруженному файлу.</p>
 *
 * <p><b>Курс считается, а не читается.</b> В выгрузке сторонней программы графа «Курс» напечатана
 * пустой (см. {@code docs/IMPORT_FORMAT.md}), поэтому взять её оттуда нельзя даже при импорте.
 * Зато курс однозначно выводится из года набора и учебного года периода, и оба у нас есть.</p>
 *
 * @param faculty что писать в графу «ФАКУЛЬТЕТ»: «9Ф» либо полное имя; {@code null} — графа пуста
 * @param course  номер курса; {@code null} — вывести не из чего (см. {@link #of})
 */
public record GroupRequisites(String faculty, Integer course) {

    /** Реквизитов нет: бланк преподавателя и аудитории, а также группа, о которой ничего не известно. */
    public static final GroupRequisites EMPTY = new GroupRequisites(null, null);

    /**
     * Реквизиты одной группы.
     *
     * <p><b>Год набора — сначала свой, потом из номера.</b> Поле {@code enrollment_year} заполнено
     * не у всех групп (оно появилось миграцией 019 и у старых строк пусто), а в номере год набора
     * закодирован цифрой всегда — расшифровывает её {@link GroupNumberDecoder}, единственный
     * владелец формы номера (§4 спецификации импорта). Заводить здесь второе понимание номера
     * значило бы обзавестись вторым владельцем правила.</p>
     *
     * <p><b>Курса нет — графа остаётся пустой.</b> Так бывает законно: у коротких курсов 11
     * факультета года набора не существует вовсе (обучение короче семестра), а у неразобранного
     * номера его неоткуда взять. Подставить «1» значило бы напечатать в документе догадку.</p>
     *
     * <p>Неположительный курс (год набора позже учебного года — испорченные данные) тоже не
     * печатается: «0 курс» в шапке документа хуже пустой графы, потому что выглядит как факт.</p>
     *
     * @param group     группа, чей бланк выгружается
     * @param faculty   факультет группы (подъём по дереву подразделений); {@code null} — не найден
     * @param studyYear учебный год периода: 2025 для 2025/2026
     */
    public static GroupRequisites of(Group group, OrgUnit faculty, int studyYear) {
        if (group == null) {
            return EMPTY;
        }
        return new GroupRequisites(facultyLabel(faculty), course(group, studyYear));
    }

    /**
     * Как называть факультет в шапке: краткое имя, если оно есть («9Ф» — ровно так факультет
     * подписан и в выгрузке сторонней программы), иначе полное. Графа узкая (N2:O2), и полное
     * «9 факультет» в ней читается хуже.
     */
    private static String facultyLabel(OrgUnit faculty) {
        if (faculty == null) {
            return null;
        }
        String shortName = faculty.getShortName();
        return shortName != null && !shortName.isBlank() ? shortName : faculty.getName();
    }

    private static Integer course(Group group, int studyYear) {
        Integer enrollmentYear = enrollmentYear(group, studyYear);
        if (enrollmentYear == null) {
            return null;
        }
        int course = studyYear - enrollmentYear + 1;
        return course >= 1 ? course : null;
    }

    private static Integer enrollmentYear(Group group, int studyYear) {
        if (group.getEnrollmentYear() != null) {
            return group.getEnrollmentYear();
        }
        if (group.getName() == null) {
            return null;
        }
        return GroupNumberDecoder.enrollmentYear(
                GroupNumberDecoder.decode(group.getName()).enrollmentDigit(), studyYear);
    }
}
