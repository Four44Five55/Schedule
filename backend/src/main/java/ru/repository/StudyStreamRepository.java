package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.StudyStream;

import java.util.List;

@Repository
public interface StudyStreamRepository extends JpaRepository<StudyStream, Integer> {
    boolean existsByName(String name);

    /**
     * Потоки, на которые не ссылается ни одно назначение.
     *
     * <p><b>Зачем.</b> {@code assignment.study_stream_id} стоит под {@code RESTRICT}, поэтому снос
     * курсов периода до потока не доходит — он остаётся мусором. А имя потока уникально
     * <b>глобально</b>, и второй прогон импорта упрётся в занятое имя: без этой уборки откат
     * неполон, и повторить прогон нельзя.</p>
     *
     * <p>Возвращаются <b>сущности</b>, а не id: удалять надо через {@code deleteAll}, чтобы
     * Hibernate почистил и связь {@code stream_groups}, которой поток владеет.</p>
     */
    @Query("SELECT s FROM StudyStream s WHERE NOT EXISTS "
            + "(SELECT 1 FROM Assignment a WHERE a.studyStream = s)")
    List<StudyStream> findOrphans();
}
