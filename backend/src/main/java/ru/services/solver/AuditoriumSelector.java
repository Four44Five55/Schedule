package ru.services.solver;

import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.services.solver.model.AuditoriumResource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Политика подбора аудитории: какие комнаты занятию вообще разрешены и какая из свободных лучше.
 *
 * <p>Отделена от {@link ScheduleWorkspace} намеренно. Workspace — это <b>состояние</b> (сетка и
 * занятость ресурсов), а «какая комната лучше» — <b>решение</b>. Раньше решение жило внутри
 * состояния цепочкой из четырёх {@code if}, и следствия были такие:</p>
 * <ul>
 *   <li>каждая ветка проверяла что хотела — {@code isFree} спрашивали три из четырёх, вместимость
 *       одна из четырёх, а резервная не спрашивала ничего и выдавала базовую аудиторию группы
 *       вслепую (замер живой базы 2026-07-17: <b>179</b> ячеек с двойным бронированием и
 *       <b>366</b> занятий, не помещающихся в комнату, все — из резервной ветки);</li>
 *   <li>политику нельзя было ни протестировать, ни переиспользовать: чтобы спросить «а куда ещё
 *       можно посадить это занятие», приходилось поднимать весь solver.</li>
 * </ul>
 *
 * <p><b>Устройство: область → жёсткий фильтр → предпочтение.</b> Область задаёт учебный план
 * (жёсткое требование или пул сужают, иначе — все комнаты). Жёсткий фильтр ровно один — комната
 * физически свободна; обойти его мимо этого класса нельзя, поэтому пятая ветка политики не сможет
 * «забыть» проверку, как забывали прежние. Всё остальное — сортировка: там, где раньше был отсев,
 * теперь порядок.</p>
 *
 * <p><b>Вместимость не отсеивает.</b> Решение заказчика: перебор на пару человек — рабочая
 * ситуация (в живой базе 134 занятия с перебором на ОДНОГО), и жёсткий фильтр оставил бы их без
 * комнаты. Плюс поток на 116 человек иначе не разместить вовсе: учебных аудиторий такого размера
 * в базе нет, самая большая — на 100. Поэтому теснота — первый ключ сортировки, а не фильтр:
 * подходящая комната всегда побеждает тесную, но тесная остаётся вариантом. Показывает тесноту
 * датчик ({@code ru.services.auditorium.AuditoriumUsageRule}), решает человек.</p>
 *
 * <p>Класс не знает ни про Spring, ни про БД, ни про {@code ResourceAvailabilityManager}: на вход
 * — комнаты как ресурсы, на выход — упорядоченный список. Поэтому покрыт юнит-тестами и годится
 * не только подбору, но и будущему списку «какие комнаты свободны сейчас» в интерфейсе: вопрос
 * тот же, меняется только то, что делают с ответом.</p>
 */
public final class AuditoriumSelector {

    /**
     * Свободные комнаты, подходящие занятию в этой ячейке, — лучшие первыми.
     *
     * @param lesson   занятие: несёт требования плана и состав потока
     * @param cell     целевая ячейка
     * @param allRooms все комнаты как ресурсы (у каждой своя занятость и вместимость)
     * @return свободные комнаты в порядке предпочтения; пусто — ставить некуда
     */
    public List<AuditoriumResource> select(Lesson lesson, CellForLesson cell,
                                           Collection<AuditoriumResource> allRooms) {
        int headcount = headcountOf(lesson);
        return searchScope(lesson, allRooms).stream()
                .filter(room -> room.isFree(cell))   // единственный жёсткий отсев
                .sorted(preference(lesson, headcount))
                .collect(Collectors.toList());
    }

    /**
     * Область поиска: какие комнаты разрешены учебным планом. Единственное место, где ветвимся.
     *
     * <p>Жёсткое требование и пул <b>сужают</b> область — других комнат занятию не предлагать.
     * Приоритетная аудитория область НЕ сужает: это пожелание, и его место в сортировке.</p>
     */
    private static List<AuditoriumResource> searchScope(Lesson lesson, Collection<AuditoriumResource> allRooms) {
        if (lesson.getRequiredAuditorium() != null) {
            Integer requiredId = lesson.getRequiredAuditorium().getId();
            return allRooms.stream()
                    .filter(room -> room.getId().equals(requiredId))
                    .collect(Collectors.toList());
        }
        if (lesson.getAllowedAuditoriumPool() != null) {
            Set<Integer> poolIds = lesson.getAllowedAuditoriumPool().getAuditoriums().stream()
                    .map(Auditorium::getId)
                    .collect(Collectors.toSet());
            return allRooms.stream()
                    .filter(room -> poolIds.contains(room.getId()))
                    .collect(Collectors.toList());
        }
        // План не сузил — рассматриваем все. Раньше здесь не искали вовсе: брали базовую аудиторию
        // группы (для потока — самую большую из базовых), и свободная сотня рядом в расчёт не шла.
        return new ArrayList<>(allRooms);
    }

    /**
     * Какую из свободных комнат взять. Порядок ключей — это и есть политика подбора, целиком.
     *
     * <ol>
     *   <li><b>Меньше тесноты.</b> Влезает — раньше тесной; среди тесных — где перебор меньше.
     *       Один ключ закрывает оба случая: у вмещающей комнаты недобор мест равен нулю.</li>
     *   <li><b>Приоритетная из плана</b> — но среди тех, куда поток влезает. Осознанно сажать в
     *       тесноту, когда рядом свободна подходящая, алгоритм не должен. (Раньше приоритетная
     *       бралась безусловно, даже если мала.)</li>
     *   <li><b>Домашняя аудитория группы</b> — привычная комната лучше случайной.</li>
     *   <li><b>Меньшая из достаточных</b> — чтобы не занять зал на 400 под семинар на 20 и не
     *       оставить без него поток, которому больше некуда.</li>
     * </ol>
     */
    private static Comparator<AuditoriumResource> preference(Lesson lesson, int headcount) {
        Integer priorityId = lesson.getPriorityAuditorium() != null
                ? lesson.getPriorityAuditorium().getId()
                : null;
        Set<Integer> homeIds = homeAuditoriumIds(lesson);

        return Comparator
                .comparingInt((AuditoriumResource room) -> room.shortfall(headcount))
                .thenComparingInt(room -> room.getId().equals(priorityId) ? 0 : 1)
                .thenComparingInt(room -> homeIds.contains(room.getId()) ? 0 : 1)
                .thenComparingInt(AuditoriumResource::capacity)
                .thenComparingInt(AuditoriumResource::getId); // стабильный порядок при равенстве
    }

    /** Сколько человек придёт. Поток без групп — 0: тогда влезает любая комната. */
    private static int headcountOf(Lesson lesson) {
        return lesson.getStudyStream() != null ? lesson.getStudyStream().calculateTotalSize() : 0;
    }

    /**
     * Домашние аудитории групп потока.
     *
     * <p>Раньше отсутствие базовой аудитории роняло подбор исключением прямо посреди подсветки
     * (а {@code MoveLessonSuggestionService} изымает занятие из workspace до вызова и возвращает
     * после — без {@code try/finally}, см. FOLLOWUPS). Теперь это просто отсутствие
     * предпочтения: комната найдётся среди прочих.</p>
     */
    private static Set<Integer> homeAuditoriumIds(Lesson lesson) {
        if (lesson.getStudyStream() == null || lesson.getStudyStream().getGroups() == null) {
            return Set.of();
        }
        return lesson.getStudyStream().getGroups().stream()
                .map(Group::getBaseAuditorium)
                .filter(Objects::nonNull)
                .map(Auditorium::getId)
                .collect(Collectors.toSet());
    }
}
