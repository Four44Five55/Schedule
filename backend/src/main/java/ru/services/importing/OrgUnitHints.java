package ru.services.importing;

import ru.entity.OrgUnit;
import ru.services.importing.EducatorNameDecoder.EducatorName;
import ru.services.importing.GroupNumberDecoder.GroupNumber;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Кому какое подразделение достанется — общий владелец правила для сверки и заведения.
 *
 * <h2>Зачем отдельный класс</h2>
 * <p>Вывод кафедры нужен дважды и в разных местах: <b>сверка</b> обязана показать, куда человек
 * попадёт, ещё до записи (И-10), а <b>заведение</b> — туда его положить. Пока правило жило внутри
 * заведения, показать его было нечем; вторая копия в сверке разошлась бы с первой при первой же
 * правке — ровно так уже расходилось «та же дисциплина», пока не появился
 * {@link DisciplineDictionary}.</p>
 *
 * <h2>Канал у каждого свой, и это принципиально</h2>
 * <ul>
 *   <li><b>Преподаватель</b> — шапка ЕГО СОБСТВЕННОГО файла. Подвал группового не годится: «Каф.»
 *       там относится к дисциплине, а не к тому, кто её ведёт.</li>
 *   <li><b>Аудитория</b> — шапка её собственного файла (И-24).</li>
 *   <li><b>Группа</b> — цифры её номера (§4 спецификации): опора надёжнее, чем шапка.</li>
 * </ul>
 *
 * <p><b>Чистые функции</b>: ни Spring, ни репозиториев. Разрешение «строка → наша сущность» берёт
 * список подразделений параметром — тем же приёмом, что чистые функции обхода дерева.</p>
 */
public final class OrgUnitHints {

    private OrgUnitHints() {
    }

    /**
     * Подпись преподавателя (в форме ключа) → кафедра из шапки его файла.
     *
     * @param ranks сокращения званий из справочника — чтобы «п-к Иванов И.И.» дал тот же ключ
     */
    public static Map<String, String> byEducator(List<ParsedSheet> sheets, List<String> ranks) {
        Map<String, String> departments = new LinkedHashMap<>();
        for (ParsedSheet sheet : sheets) {
            if (sheet.header().kind() != ParsedSheet.CutKind.EDUCATOR
                    || sheet.header().owner() == null || sheet.header().department() == null) {
                continue;
            }
            EducatorName parsed = EducatorNameDecoder.decode(sheet.header().owner(), ranks);
            if (parsed.recognized()) {
                departments.putIfAbsent(parsed.key(), sheet.header().department().trim());
            }
        }
        return departments;
    }

    /** Номер комнаты как в файле → кафедра-владелец из шапки её аудиторного файла (И-24). */
    public static Map<String, String> byRoom(List<ParsedSheet> sheets) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (ParsedSheet sheet : sheets) {
            if (sheet.header().kind() == ParsedSheet.CutKind.AUDITORIUM
                    && sheet.header().owner() != null && sheet.header().department() != null) {
                owners.putIfAbsent(sheet.header().owner().trim(), sheet.header().department().trim());
            }
        }
        return owners;
    }

    /**
     * Кафедра группы — краткое имя из её номера; {@code null}, если номера не понял <b>или</b> форма
     * номера кафедру не кодирует (короткие курсы 11 факультета).
     *
     * <p>Оба случая оставляют группу без подразделения вовсе: угадывать нечего, и это ровно та
     * строка, которой нужна ручная простановка (И-25).</p>
     */
    public static String byGroupNumber(String groupNumber) {
        GroupNumber decoded = GroupNumberDecoder.decode(groupNumber);
        return decoded.recognized() ? decoded.departmentShortName() : null;
    }

    /**
     * Подразделение по краткому либо полному имени — и только если оно <b>одно</b>.
     *
     * <p>Несколько одноимённых значит, что выбирать должен человек: приписать к не той кафедре —
     * ошибка, которую потом не увидеть, расписание будет выглядеть нормально.</p>
     *
     * @param all  все подразделения базы
     * @param name имя как в файле («91», «91 кафедра»); {@code null} — вопроса нет
     */
    public static Optional<OrgUnit> unique(List<OrgUnit> all, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        // Тот же ключ, что в сверке: «51» из номера группы и «51 кафедра» в базе — одно и то же.
        String wanted = ImportMatchingService.orgUnitKey(name);
        List<OrgUnit> found = all.stream()
                .filter(unit -> wanted.equals(ImportMatchingService.orgUnitKey(unit.getShortName()))
                        || wanted.equals(ImportMatchingService.orgUnitKey(unit.getName())))
                .toList();
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }
}
