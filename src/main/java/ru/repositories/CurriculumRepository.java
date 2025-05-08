package ru.repositories;


import ru.entity.Discipline;
import ru.entity.logicSchema.ChainManager;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.entity.logicSchema.ThemeLesson;
import ru.enums.KindOfStudy;

import java.sql.*;
import java.util.*;

public class CurriculumRepository {
    private final Connection connection;

    public CurriculumRepository(Connection connection) {
        this.connection = connection;
    }

    // Основной метод: загрузка учебного плана по ID дисциплины
    public DisciplineCurriculum findCurriculumByDisciplineId(int disciplineId) throws SQLException {
        Discipline discipline = loadDiscipline(disciplineId);
        List<CurriculumSlot> slots = loadCurriculumSlots(disciplineId);
        ChainManager chainManager = loadChainManager(disciplineId);

        return new DisciplineCurriculum(disciplineId, discipline, slots, chainManager);
    }

    // Загрузка дисциплины
    public Discipline loadDiscipline(int id) throws SQLException {
        String sql = "SELECT id, name, abbreviation FROM discipline WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Discipline(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("abbreviation")
                );
            }
            throw new SQLException("Discipline not found with id: " + id);
        }
    }

    // Загрузка слотов учебного плана
    private List<CurriculumSlot> loadCurriculumSlots(int disciplineId) throws SQLException {
        String sql = """
                SELECT cs.id, cs.kind_of_study, 
                       tl.id AS theme_id, tl.theme_number, tl.title AS theme_title
                FROM curriculum_slot cs
                JOIN discipline_curriculum dc ON cs.id = dc.curriculum_slot_id
                LEFT JOIN theme_lesson tl ON cs.theme_lesson_id = tl.id
                WHERE dc.discipline_id = ?
                ORDER BY cs.id""";

        List<CurriculumSlot> slots = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, disciplineId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ThemeLesson theme = null;
                if (rs.getObject("theme_id") != null) {
                    theme = new ThemeLesson(
                            rs.getInt("theme_id"),
                            rs.getString("theme_number"),
                            rs.getString("theme_title")
                    );
                }

                CurriculumSlot slot = new CurriculumSlot(
                        rs.getInt("id"),
                        KindOfStudy.valueOf(rs.getString("kind_of_study")),
                        theme
                );
                slots.add(slot);
            }
        }
        return slots;
    }

    // Загрузка связей между слотами
    private ChainManager loadChainManager(int disciplineId) throws SQLException {
        String sql = """
                SELECT sc.slot_a_id, sc.slot_b_id
                FROM slot_chain sc
                JOIN discipline_curriculum dc ON sc.slot_a_id = dc.curriculum_slot_id
                WHERE dc.discipline_id = ?""";

        ChainManager chainManager = new ChainManager();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, disciplineId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                chainManager.chain(
                        rs.getInt("slot_a_id"),
                        rs.getInt("slot_b_id")
                );
            }
        }
        return chainManager;
    }
}
