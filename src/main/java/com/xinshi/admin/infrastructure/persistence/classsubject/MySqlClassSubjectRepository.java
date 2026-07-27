/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.classsubject;

import com.xinshi.admin.domain.classsubject.ClassSubject;
import com.xinshi.admin.domain.classsubject.ClassSubjectRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlClassSubjectRepository
implements ClassSubjectRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT_SQL = "SELECT cs.id, cs.academic_term_id, cs.class_id, c.class_name, cs.subject_id, s.subject_name, cs.source_grade_subject_id, cs.teacher_user_id, u.real_name, s.min_score, s.max_score, cs.status, cs.created_at FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_subject s ON s.id = cs.subject_id LEFT JOIN sys_user u ON u.id = cs.teacher_user_id";

    public MySqlClassSubjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ClassSubject> findById(long id) {
        List list = this.jdbcTemplate.query("SELECT cs.id, cs.academic_term_id, cs.class_id, c.class_name, cs.subject_id, s.subject_name, cs.source_grade_subject_id, cs.teacher_user_id, u.real_name, s.min_score, s.max_score, cs.status, cs.created_at FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_subject s ON s.id = cs.subject_id LEFT JOIN sys_user u ON u.id = cs.teacher_user_id WHERE cs.id = ?", new Object[]{id}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public List<ClassSubject> findByTermAndClass(long academicTermId, long classId) {
        return this.jdbcTemplate.query("SELECT cs.id, cs.academic_term_id, cs.class_id, c.class_name, cs.subject_id, s.subject_name, cs.source_grade_subject_id, cs.teacher_user_id, u.real_name, s.min_score, s.max_score, cs.status, cs.created_at FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_subject s ON s.id = cs.subject_id LEFT JOIN sys_user u ON u.id = cs.teacher_user_id WHERE cs.academic_term_id = ? AND cs.class_id = ? ORDER BY cs.id", new Object[]{academicTermId, classId}, this::mapRow);
    }

    @Override
    public Optional<ClassSubject> findByTermClassSubject(long academicTermId, long classId, long subjectId) {
        List list = this.jdbcTemplate.query("SELECT cs.id, cs.academic_term_id, cs.class_id, c.class_name, cs.subject_id, s.subject_name, cs.source_grade_subject_id, cs.teacher_user_id, u.real_name, s.min_score, s.max_score, cs.status, cs.created_at FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_subject s ON s.id = cs.subject_id LEFT JOIN sys_user u ON u.id = cs.teacher_user_id WHERE cs.academic_term_id = ? AND cs.class_id = ? AND cs.subject_id = ?", new Object[]{academicTermId, classId, subjectId}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public int countByTermAndClass(long academicTermId, long classId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class_subject WHERE academic_term_id = ? AND class_id = ?", Integer.class, new Object[]{academicTermId, classId});
        return count == null ? 0 : count;
    }

    @Override
    public ClassSubject save(ClassSubject classSubject) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_class_subject (academic_term_id, class_id, subject_id, teacher_user_id, status) VALUES (?, ?, ?, ?, ?)", 1);
            ps.setLong(1, classSubject.getAcademicTermId());
            ps.setLong(2, classSubject.getClassId());
            ps.setLong(3, classSubject.getSubjectId());
            if (classSubject.getTeacherUserId() != null) {
                ps.setLong(4, classSubject.getTeacherUserId());
            } else {
                ps.setNull(4, -5);
            }
            ps.setInt(5, classSubject.getStatus());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert class_subject failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted class_subject not found"));
    }

    @Override
    public void updateTeacher(long id, long teacherUserId) {
        this.jdbcTemplate.update("UPDATE school_class_subject SET teacher_user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{teacherUserId, id});
    }

    @Override
    public void delete(long id) {
        this.jdbcTemplate.update("DELETE FROM school_class_subject WHERE id = ?", new Object[]{id});
    }

    @Override
    public boolean isTeacherOfClass(long classId, long teacherUserId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class_subject WHERE class_id = ? AND teacher_user_id = ? AND status = 1", Integer.class, new Object[]{classId, teacherUserId});
        return count != null && count > 0;
    }

    private ClassSubject mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long teacherUserId = rs.getLong("teacher_user_id");
        if (rs.wasNull()) {
            teacherUserId = null;
        }
        Long sourceGradeSubjectId = rs.getLong("source_grade_subject_id");
        if (rs.wasNull()) {
            sourceGradeSubjectId = null;
        }
        return ClassSubject.rehydrate(rs.getLong("id"), rs.getLong("academic_term_id"), rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("subject_id"), rs.getString("subject_name"), sourceGradeSubjectId, teacherUserId, rs.getString("real_name"), rs.getDouble("min_score"), rs.getDouble("max_score"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}

