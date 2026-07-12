package ru.services.board;

import ru.entity.Assignment;
import ru.entity.Educator;
import ru.entity.Group;
import ru.utils.GroupNameComparator;

import java.util.Comparator;
import java.util.List;

/**
 * Ось группировки «доски раскладки» (Strategy как enum): из назначения извлекает набор
 * сущностей-участников, по которым строится дерево.
 *
 * <p>OCP: новая ось (например, {@code AUDITORIUM} по пулам/слотам) — это новая константа с
 * реализацией {@link #entitiesOf}, без правки {@link PlacementBoardService} и фронта. Spring
 * связывает значение из query-параметра ({@code ?axis=GROUP|EDUCATOR}) прямо в этот enum.</p>
 */
public enum BoardAxis {

    /** Группы потока назначения (каждая группа — отдельная сущность). */
    GROUP {
        @Override
        public List<EntityRef> entitiesOf(Assignment a) {
            return a.getStudyStream().getGroups().stream()
                    .sorted(Comparator.comparing(Group::getName, GroupNameComparator.INSTANCE))
                    .map(g -> new EntityRef(g.getId(), g.getName()))
                    .toList();
        }
    },

    /** Преподаватели назначения. */
    EDUCATOR {
        @Override
        public List<EntityRef> entitiesOf(Assignment a) {
            return a.getEducators().stream()
                    .map(e -> new EntityRef(e.getId(), e.getName()))
                    .toList();
        }
    };

    /**
     * Сущности-участники этого назначения на данной оси.
     *
     * @param a назначение (с навигируемыми в активной транзакции ассоциациями)
     * @return список (id, имя); одно назначение может относиться к нескольким сущностям
     */
    public abstract List<EntityRef> entitiesOf(Assignment a);

    /** Ссылка на сущность оси: id (для ограничений/сетки) + имя (для отображения). */
    public record EntityRef(Integer id, String name) {
    }
}
