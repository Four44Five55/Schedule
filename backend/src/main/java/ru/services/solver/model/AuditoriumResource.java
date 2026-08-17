package ru.services.solver.model;

import ru.entity.Auditorium;

/**
 * Аудитория как планируемый ресурс: занятость (от {@link SchedulableResource}) плюс то, что
 * знает про себя сама комната — сколько в неё влезает.
 *
 * <p><b>Зачем понадобился отдельный класс.</b> Аудитория была единственным ресурсом без своего
 * подкласса: у преподавателя есть {@link EducatorResource} с предпочтениями, а комната
 * подставлялась голым {@code SchedulableResource}, который умеет отвечать только «занято или
 * нет». Вместимости было негде жить — и она протекла в одну ветку подбора из четырёх
 * ({@code ScheduleWorkspace.findAvailableAuditoriumsFor}, случай «пул»). Остальные три про неё
 * забыли, и в живой базе оказалось 366 занятий, не помещающихся в свою комнату, с перебором до
 * 46 человек (замер 2026-07-17). Это не забывчивость, а следствие структуры: когда абстракция
 * вынуждает <i>помнить</i> о проверке, кто-нибудь обязательно забудет.</p>
 *
 * <p><b>Класс отвечает на вопросы, но не выносит приговоров.</b> {@link #shortfall(int)} говорит
 * «не хватает 2 мест», а не «нельзя». Тесно на пару человек — рабочая ситуация, решать
 * диспетчеру; тесно на полсотни — фикция. Где граница, решает вызывающий: подбор этим
 * ранжирует, датчик ({@code AuditoriumUsageRule}) показывает, будущий выбор комнаты вручную —
 * предупреждает. Тот же принцип, что в {@code AuditoriumFinding}: факт отдельно, политика
 * отдельно.</p>
 */
public final class AuditoriumResource extends SchedulableResource {

    private final Auditorium auditorium;

    public AuditoriumResource(Auditorium auditorium) {
        super(auditorium.getId(), auditorium.getName());
        this.auditorium = auditorium;
    }

    /** Сама сущность — нужна тем, кто отдаёт подобранную комнату наружу. */
    public Auditorium auditorium() {
        return auditorium;
    }

    /** Мест в комнате. */
    public int capacity() {
        return auditorium.getCapacity();
    }

    /**
     * Кафедра-владелец комнаты; {@code null} — не указана (миграция 025).
     *
     * <p>Только id: у ленивого прокси он читается без обращения к БД, а больше подбору ничего и не
     * нужно — вопрос «своя ли комната» решается сравнением идентификаторов.</p>
     */
    public Integer orgUnitId() {
        return auditorium.getOrgUnit() == null ? null : auditorium.getOrgUnit().getId();
    }

    /**
     * Помещается ли столько людей.
     *
     * @param headcount сколько человек придёт (суммарный размер групп потока)
     */
    public boolean fits(int headcount) {
        return capacity() >= headcount;
    }

    /**
     * На сколько человек не хватает мест — 0, если помещаются все.
     *
     * <p>Число, а не флаг, намеренно: им можно и отсортировать (меньше перебор — лучше комната),
     * и предупредить («тесно на 2»), и промолчать. Флаг «мала» умел бы только запрещать.</p>
     *
     * @param headcount сколько человек придёт
     */
    public int shortfall(int headcount) {
        return Math.max(0, headcount - capacity());
    }
}
