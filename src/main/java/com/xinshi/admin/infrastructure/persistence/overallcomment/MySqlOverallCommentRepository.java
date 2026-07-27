/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.overallcomment;

import com.xinshi.admin.domain.overallcomment.OverallComment;
import com.xinshi.admin.domain.overallcomment.OverallCommentRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlOverallCommentRepository
implements OverallCommentRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT_SQL = "SELECT o.id, o.academic_term_id, t.term_name, o.class_id, c.class_name, o.student_id, s.student_name, o.overall_comment, o.strengths, o.improvement_points, o.evaluator_user_id, u.real_name, o.evaluated_at, o.status, o.created_at FROM school_student_overall_comment o LEFT JOIN school_academic_term t ON t.id = o.academic_term_id LEFT JOIN school_class c ON c.id = o.class_id LEFT JOIN school_student s ON s.id = o.student_id LEFT JOIN sys_user u ON u.id = o.evaluator_user_id";

    public MySqlOverallCommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<OverallComment> findById(long id) {
        List list = this.jdbcTemplate.query("SELECT o.id, o.academic_term_id, t.term_name, o.class_id, c.class_name, o.student_id, s.student_name, o.overall_comment, o.strengths, o.improvement_points, o.evaluator_user_id, u.real_name, o.evaluated_at, o.status, o.created_at FROM school_student_overall_comment o LEFT JOIN school_academic_term t ON t.id = o.academic_term_id LEFT JOIN school_class c ON c.id = o.class_id LEFT JOIN school_student s ON s.id = o.student_id LEFT JOIN sys_user u ON u.id = o.evaluator_user_id WHERE o.id = ?", new Object[]{id}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public List<OverallComment> findByParams(Long academicTermId, Long classId, Long studentId, long userId, List<String> roles) {
        StringBuilder sql = new StringBuilder(SELECT_SQL).append(" WHERE 1 = 1");
        ArrayList<Long> args = new ArrayList<Long>();
        if (academicTermId != null) {
            sql.append(" AND o.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            sql.append(" AND o.class_id = ?");
            args.add(classId);
        }
        if (studentId != null) {
            sql.append(" AND o.student_id = ?");
            args.add(studentId);
        }
        if (roles.contains("HEAD_TEACHER") && !roles.contains("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = o.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(userId);
        } else if (roles.contains("PARENT") && !roles.contains("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = o.student_id AND sp.parent_user_id = ?)");
            args.add(userId);
        }
        sql.append(" ORDER BY o.id DESC");
        return this.jdbcTemplate.query(sql.toString(), args.toArray(), this::mapRow);
    }

    @Override
    public Optional<OverallComment> findByTermAndStudent(long academicTermId, long studentId) {
        List list = this.jdbcTemplate.query("SELECT o.id, o.academic_term_id, t.term_name, o.class_id, c.class_name, o.student_id, s.student_name, o.overall_comment, o.strengths, o.improvement_points, o.evaluator_user_id, u.real_name, o.evaluated_at, o.status, o.created_at FROM school_student_overall_comment o LEFT JOIN school_academic_term t ON t.id = o.academic_term_id LEFT JOIN school_class c ON c.id = o.class_id LEFT JOIN school_student s ON s.id = o.student_id LEFT JOIN sys_user u ON u.id = o.evaluator_user_id WHERE o.academic_term_id = ? AND o.student_id = ?", new Object[]{academicTermId, studentId}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public OverallComment save(OverallComment comment) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_student_overall_comment (academic_term_id, class_id, student_id, overall_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", 1);
            ps.setLong(1, comment.getAcademicTermId());
            ps.setLong(2, comment.getClassId());
            ps.setLong(3, comment.getStudentId());
            ps.setString(4, comment.getOverallComment());
            ps.setString(5, comment.getStrengths());
            ps.setString(6, comment.getImprovementPoints());
            ps.setLong(7, comment.getEvaluatorUserId());
            ps.setTimestamp(8, comment.getEvaluatedAt() != null ? Timestamp.valueOf(comment.getEvaluatedAt()) : null);
            ps.setInt(9, comment.getStatusCode());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert overall_comment failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted overall_comment not found"));
    }

    @Override
    public void update(OverallComment comment) {
        this.jdbcTemplate.update("UPDATE school_student_overall_comment SET class_id = ?, overall_comment = ?, strengths = ?, improvement_points = ?, evaluator_user_id = ?, evaluated_at = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE academic_term_id = ? AND student_id = ?", new Object[]{comment.getClassId(), comment.getOverallComment(), comment.getStrengths(), comment.getImprovementPoints(), comment.getEvaluatorUserId(), comment.getEvaluatedAt() != null ? Timestamp.valueOf(comment.getEvaluatedAt()) : null, comment.getStatusCode(), comment.getAcademicTermId(), comment.getStudentId()});
    }

    private OverallComment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp evaluatedAt = rs.getTimestamp("evaluated_at");
        return OverallComment.rehydrate(rs.getLong("id"), rs.getLong("academic_term_id"), rs.getString("term_name"), rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("student_id"), rs.getString("student_name"), rs.getString("overall_comment"), rs.getString("strengths"), rs.getString("improvement_points"), rs.getLong("evaluator_user_id"), rs.getString("real_name"), evaluatedAt != null ? evaluatedAt.toLocalDateTime() : null, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}

