/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.courseresult;

import com.xinshi.admin.domain.courseresult.CourseResult;
import com.xinshi.admin.domain.courseresult.CourseResultRepository;
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
public class MySqlCourseResultRepository
implements CourseResultRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String RESULT_SELECT = "SELECT r.id, r.academic_term_id, t.term_name, r.class_subject_id, cs.class_id, c.class_name, r.student_id, s.student_name, s.student_no, cs.subject_id, su.subject_name, su.min_score, su.max_score, r.score, r.performance_comment, r.strengths, r.improvement_points, cs.teacher_user_id, tu.real_name, r.evaluator_user_id, u.real_name, r.evaluated_at, r.status, r.created_at FROM school_student_course_result r LEFT JOIN school_academic_term t ON t.id = r.academic_term_id LEFT JOIN school_class_subject cs ON cs.id = r.class_subject_id LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.id = r.student_id LEFT JOIN school_subject su ON su.id = cs.subject_id LEFT JOIN sys_user tu ON tu.id = cs.teacher_user_id LEFT JOIN sys_user u ON u.id = r.evaluator_user_id";

    public MySqlCourseResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CourseResult> findById(long id) {
        List list = this.jdbcTemplate.query("SELECT r.id, r.academic_term_id, t.term_name, r.class_subject_id, cs.class_id, c.class_name, r.student_id, s.student_name, s.student_no, cs.subject_id, su.subject_name, su.min_score, su.max_score, r.score, r.performance_comment, r.strengths, r.improvement_points, cs.teacher_user_id, tu.real_name, r.evaluator_user_id, u.real_name, r.evaluated_at, r.status, r.created_at FROM school_student_course_result r LEFT JOIN school_academic_term t ON t.id = r.academic_term_id LEFT JOIN school_class_subject cs ON cs.id = r.class_subject_id LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.id = r.student_id LEFT JOIN school_subject su ON su.id = cs.subject_id LEFT JOIN sys_user tu ON tu.id = cs.teacher_user_id LEFT JOIN sys_user u ON u.id = r.evaluator_user_id WHERE r.id = ?", new Object[]{id}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public List<CourseResult> findByParams(Long academicTermId, Long classId, Long studentId, Long classSubjectId, long userId, List<String> roles) {
        StringBuilder sql = new StringBuilder(RESULT_SELECT).append(" WHERE 1 = 1");
        ArrayList<Object> args = new ArrayList<Object>();
        if (academicTermId != null) {
            sql.append(" AND r.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            sql.append(" AND cs.class_id = ?");
            args.add(classId);
        }
        if (studentId != null) {
            sql.append(" AND r.student_id = ?");
            args.add(studentId);
        }
        if (classSubjectId != null) {
            sql.append(" AND r.class_subject_id = ?");
            args.add(classSubjectId);
        }
        this.appendRoleFilter(sql, args, userId, roles);
        sql.append(" ORDER BY r.id DESC");
        return this.jdbcTemplate.query(sql.toString(), args.toArray(), this::mapRow);
    }

    @Override
    public List<CourseResult> findTeacherScoreEntries(Long academicTermId, Long classId, Long subjectId, String keyword, String mode, long userId, List<String> roles, int limit, long offset) {
        String fromClause = "FROM school_class_subject cs LEFT JOIN school_academic_term t ON t.id = cs.academic_term_id LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.class_id = cs.class_id AND s.is_deleted = 0 AND s.status = 1 LEFT JOIN school_subject su ON su.id = cs.subject_id LEFT JOIN school_student_course_result r ON r.academic_term_id = cs.academic_term_id AND r.class_subject_id = cs.id AND r.student_id = s.id LEFT JOIN sys_user tu ON tu.id = cs.teacher_user_id LEFT JOIN sys_user u ON u.id = r.evaluator_user_id";
        StringBuilder where = new StringBuilder(" WHERE cs.status = 1 AND c.is_deleted = 0 AND s.id IS NOT NULL");
        ArrayList<Object> args = new ArrayList<Object>();
        if (academicTermId != null) {
            where.append(" AND cs.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            where.append(" AND cs.class_id = ?");
            args.add(classId);
        }
        if (subjectId != null) {
            where.append(" AND cs.subject_id = ?");
            args.add(subjectId);
        }
        if (this.hasText(keyword)) {
            where.append(" AND (s.student_name LIKE ? OR s.student_no LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        this.appendTeacherScoreFilter(where, args, mode, userId, roles);
        String orderBy = " ORDER BY c.grade_level ASC, c.id DESC, su.id ASC, s.student_no ASC, s.id ASC";
        String dataSql = "SELECT r.id, cs.academic_term_id, t.term_name, cs.id, cs.class_id, c.class_name, s.id, s.student_no, s.student_name, cs.subject_id, su.subject_name, su.min_score, su.max_score, r.score, r.performance_comment, r.strengths, r.improvement_points, cs.teacher_user_id, tu.real_name, r.evaluator_user_id, u.real_name, r.evaluated_at, COALESCE(r.status, 0), r.created_at " + fromClause + where + orderBy + " LIMIT ? OFFSET ?";
        args.add(limit);
        args.add(offset);
        return this.jdbcTemplate.query(dataSql, args.toArray(), this::mapRow);
    }

    @Override
    public long countTeacherScoreEntries(Long academicTermId, Long classId, Long subjectId, String keyword, String mode, long userId, List<String> roles) {
        String fromClause = "FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.class_id = cs.class_id AND s.is_deleted = 0 AND s.status = 1";
        StringBuilder where = new StringBuilder(" WHERE cs.status = 1 AND c.is_deleted = 0 AND s.id IS NOT NULL");
        ArrayList<Object> args = new ArrayList<Object>();
        if (academicTermId != null) {
            where.append(" AND cs.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            where.append(" AND cs.class_id = ?");
            args.add(classId);
        }
        if (subjectId != null) {
            where.append(" AND cs.subject_id = ?");
            args.add(subjectId);
        }
        if (this.hasText(keyword)) {
            where.append(" AND (s.student_name LIKE ? OR s.student_no LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        this.appendTeacherScoreFilter(where, args, mode, userId, roles);
        Long count = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) " + fromClause + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public Optional<CourseResult> findByTermClassSubjectStudent(long academicTermId, long classSubjectId, long studentId) {
        List list = this.jdbcTemplate.query("SELECT r.id, r.academic_term_id, t.term_name, r.class_subject_id, cs.class_id, c.class_name, r.student_id, s.student_name, s.student_no, cs.subject_id, su.subject_name, su.min_score, su.max_score, r.score, r.performance_comment, r.strengths, r.improvement_points, cs.teacher_user_id, tu.real_name, r.evaluator_user_id, u.real_name, r.evaluated_at, r.status, r.created_at FROM school_student_course_result r LEFT JOIN school_academic_term t ON t.id = r.academic_term_id LEFT JOIN school_class_subject cs ON cs.id = r.class_subject_id LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.id = r.student_id LEFT JOIN school_subject su ON su.id = cs.subject_id LEFT JOIN sys_user tu ON tu.id = cs.teacher_user_id LEFT JOIN sys_user u ON u.id = r.evaluator_user_id WHERE r.academic_term_id = ? AND r.class_subject_id = ? AND r.student_id = ?", new Object[]{academicTermId, classSubjectId, studentId}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public CourseResult save(CourseResult result) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_student_course_result (academic_term_id, class_subject_id, student_id, score, performance_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", 1);
            ps.setLong(1, result.getAcademicTermId());
            ps.setLong(2, result.getClassSubjectId());
            ps.setLong(3, result.getStudentId());
            ps.setDouble(4, result.getScore());
            ps.setString(5, result.getPerformanceComment());
            ps.setString(6, result.getStrengths());
            ps.setString(7, result.getImprovementPoints());
            ps.setLong(8, result.getEvaluatorUserId());
            ps.setTimestamp(9, result.getEvaluatedAt() != null ? Timestamp.valueOf(result.getEvaluatedAt()) : null);
            ps.setInt(10, result.getStatusCode());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert course_result failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted course_result not found"));
    }

    @Override
    public void update(CourseResult result) {
        this.jdbcTemplate.update("UPDATE school_student_course_result SET score = ?, performance_comment = ?, strengths = ?, improvement_points = ?, evaluator_user_id = ?, evaluated_at = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE academic_term_id = ? AND class_subject_id = ? AND student_id = ?", new Object[]{result.getScore(), result.getPerformanceComment(), result.getStrengths(), result.getImprovementPoints(), result.getEvaluatorUserId(), result.getEvaluatedAt() != null ? Timestamp.valueOf(result.getEvaluatedAt()) : null, result.getStatusCode(), result.getAcademicTermId(), result.getClassSubjectId(), result.getStudentId()});
    }

    @Override
    public void publish(long id) {
        this.jdbcTemplate.update("UPDATE school_student_course_result SET status = 2, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
    }

    @Override
    public int countByClassSubjectId(long classSubjectId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_student_course_result WHERE class_subject_id = ?", Integer.class, new Object[]{classSubjectId});
        return count == null ? 0 : count;
    }

    private void appendRoleFilter(StringBuilder sql, List<Object> args, long userId, List<String> roles) {
        if (roles.contains("HEAD_TEACHER") && !roles.contains("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = cs.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(userId);
        } else if (roles.contains("TEACHER") && !roles.contains("SUPER_ADMIN")) {
            sql.append(" AND cs.teacher_user_id = ?");
            args.add(userId);
        } else if (roles.contains("PARENT") && !roles.contains("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = r.student_id AND sp.parent_user_id = ?)");
            args.add(userId);
        }
    }

    private void appendTeacherScoreFilter(StringBuilder where, List<Object> args, String mode, long userId, List<String> roles) {
        if (roles.contains("SUPER_ADMIN")) {
            return;
        }
        if (roles.contains("HEAD_TEACHER") && !roles.contains("TEACHER")) {
            where.append(" AND c.head_teacher_user_id = ?");
            args.add(userId);
        } else if (roles.contains("TEACHER") && !roles.contains("HEAD_TEACHER")) {
            where.append(" AND cs.teacher_user_id = ?");
            args.add(userId);
        } else if (roles.contains("HEAD_TEACHER") && roles.contains("TEACHER")) {
            if ("teacher".equals(mode)) {
                where.append(" AND cs.teacher_user_id = ?");
            } else {
                where.append(" AND c.head_teacher_user_id = ?");
            }
            args.add(userId);
        }
    }

    private CourseResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp evaluatedAt = rs.getTimestamp("evaluated_at");
        return CourseResult.rehydrate(rs.getLong("id"), rs.getLong("academic_term_id"), rs.getString("term_name"), rs.getLong("class_subject_id"), rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("student_id"), rs.getString("student_name"), rs.getString("student_no"), rs.getLong("subject_id"), rs.getString("subject_name"), rs.getDouble("min_score"), rs.getDouble("max_score"), rs.getDouble("score"), rs.getString("performance_comment"), rs.getString("strengths"), rs.getString("improvement_points"), rs.getLong("teacher_user_id"), rs.getString("real_name"), rs.getLong("evaluator_user_id"), rs.getString("real_name"), evaluatedAt != null ? evaluatedAt.toLocalDateTime() : null, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

