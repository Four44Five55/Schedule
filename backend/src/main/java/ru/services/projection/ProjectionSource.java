package ru.services.projection;

import ru.repository.write.LessonPlacementRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Источник устаревания снимка read-модели: какая master-сущность изменилась и как по ней найти
 * затронутые размещения.
 *
 * <p><b>Strategy как enum</b> — тот же приём, что у {@code BoardAxis} и {@code ExportAxis}:
 * новая денормализованная сущность в {@code schedule_view} = новая константа с одним запросом,
 * а не правка сервисов (OCP). Никакой другой класс не должен знать, «как от преподавателя дойти
 * до размещений».</p>
 *
 * <p>Перечислены ровно те сущности, чьи поля {@code schedule_view} хранит снимком:
 * преподаватель и группа (имена), поток (состав групп → строки проекции), аудитория (имя),
 * дисциплина (название/аббревиатура), тема (номер/название), слот плана (вид занятия, ссылка на
 * тему) и само назначение (состав преподавателей, поток).</p>
 *
 * <p><b>⚠️ Правило сопровождения:</b> добавили в {@link ru.entity.read.ScheduleView} новое
 * денормализованное поле — заведите здесь его источник и объявляйте изменения через
 * {@link ProjectionMaintenance}. Иначе снимок начнёт тихо расходиться с master-данными, и сверка
 * ({@code ProjectionHealthService}) этого не поймает: она сравнивает наличие строк, а не их
 * содержимое.</p>
 */
public enum ProjectionSource {

    /** Назначение: сменился состав преподавателей или поток. */
    ASSIGNMENT {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsByAssignmentIdIn(ids);
        }
    },

    /** Преподаватель: переименован. */
    EDUCATOR {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsByEducatorIdIn(ids);
        }
    },

    /** Группа: переименована или удалена (её строки проекции надо снять). */
    GROUP {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsByGroupIdIn(ids);
        }
    },

    /**
     * Поток: переименован или <b>изменён состав групп</b>. Ключевой случай — группу из потока
     * убрали: искать по группе уже поздно (связи нет), поэтому охват берётся по потоку — он
     * находит все размещения назначений этого потока, и перепроекция переписывает их строки
     * под актуальный состав.
     */
    STUDY_STREAM {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsByStreamIdIn(ids);
        }
    },

    /** Аудитория: переименована или удалена (занятие остаётся, комната у него пропадает). */
    AUDITORIUM {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsByAuditoriumIdIn(ids);
        }
    },

    /** Дисциплина: сменилось название или аббревиатура. */
    DISCIPLINE {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsByDisciplineIdIn(ids);
        }
    },

    /** Тема: сменился номер или название. */
    THEME {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsByThemeIdIn(ids);
        }
    },

    /** Слот учебного плана: сменился вид занятия или привязанная тема. */
    CURRICULUM_SLOT {
        @Override
        public List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids) {
            return repo.findIdsBySlotIdIn(ids);
        }
    };

    /**
     * Размещения, чей снимок устарел из-за изменения этих сущностей.
     *
     * @param repo репозиторий write-стороны
     * @param ids  id изменённых сущностей
     * @return id затронутых размещений (возможно пустой список)
     */
    public abstract List<UUID> affectedPlacements(LessonPlacementRepository repo, Collection<Integer> ids);
}
