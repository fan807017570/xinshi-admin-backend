/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.achievement;

import com.xinshi.admin.domain.achievement.AchievementRepository;
import com.xinshi.admin.domain.achievement.StudentAchievement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlAchievementRepository
implements AchievementRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT_SQL = "SELECT id, academic_term_id, student_id, honor_type_id, achievement_text, sort_order, created_at FROM school_student_achievement";

    public MySqlAchievementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentAchievement> findByTermAndStudent(long academicTermId, long studentId) {
        return this.jdbcTemplate.query("SELECT id, academic_term_id, student_id, honor_type_id, achievement_text, sort_order, created_at FROM school_student_achievement WHERE academic_term_id = ? AND student_id = ? ORDER BY sort_order, id", new Object[]{academicTermId, studentId}, this::mapRow);
    }

    @Override
    public StudentAchievement save(StudentAchievement achievement) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_student_achievement (academic_term_id, student_id, honor_type_id, achievement_text, sort_order) VALUES (?, ?, ?, ?, ?)", 1);
            ps.setLong(1, achievement.getAcademicTermId());
            ps.setLong(2, achievement.getStudentId());
            if (achievement.getHonorTypeId() != null) {
                ps.setLong(3, achievement.getHonorTypeId());
            } else {
                ps.setNull(3, -5);
            }
            ps.setString(4, achievement.getAchievementText());
            ps.setInt(5, achievement.getSortOrder());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert achievement failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted achievement not found"));
    }

    @Override
    public void delete(long id) {
        this.jdbcTemplate.update("DELETE FROM school_student_achievement WHERE id = ?", new Object[]{id});
    }

    @Override
    public void update(StudentAchievement achievement) {
        this.jdbcTemplate.update("UPDATE school_student_achievement SET honor_type_id = ?, achievement_text = ?, sort_order = ? WHERE id = ?", new Object[]{achievement.getHonorTypeId() != null ? achievement.getHonorTypeId() : null, achievement.getAchievementText(), achievement.getSortOrder(), achievement.getId()});
    }

    private Optional<StudentAchievement> findById(long id) {
        List list = this.jdbcTemplate.query("SELECT id, academic_term_id, student_id, honor_type_id, achievement_text, sort_order, created_at FROM school_student_achievement WHERE id = ?", new Object[]{id}, this::mapRow);
        return list.stream().findFirst();
    }

    private StudentAchievement mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        long honorTypeId = rs.getLong("honor_type_id");
        Long honorTypeIdValue = rs.wasNull() ? null : Long.valueOf(honorTypeId);
        return StudentAchievement.rehydrate(rs.getLong("id"), rs.getLong("academic_term_id"), rs.getLong("student_id"), rs.getString("achievement_text"), rs.getInt("sort_order"), honorTypeIdValue, createdAt != null ? createdAt.toLocalDateTime() : null);
    }
}

