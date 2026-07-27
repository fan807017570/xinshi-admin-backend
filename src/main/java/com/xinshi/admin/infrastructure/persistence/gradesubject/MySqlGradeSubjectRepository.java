/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.gradesubject;

import com.xinshi.admin.domain.gradesubject.GradeSubject;
import com.xinshi.admin.domain.gradesubject.GradeSubjectRepository;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlGradeSubjectRepository
implements GradeSubjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlGradeSubjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<GradeSubject> findByTermAndGrade(long academicTermId, int gradeLevel) {
        return this.jdbcTemplate.query("SELECT gs.id, gs.academic_term_id, gs.grade_level, gs.subject_id, s.subject_name, gs.is_required, gs.sort_order, gs.status, gs.created_at FROM school_grade_subject gs LEFT JOIN school_subject s ON s.id = gs.subject_id WHERE gs.academic_term_id = ? AND gs.grade_level = ? ORDER BY gs.sort_order, gs.id", new Object[]{academicTermId, gradeLevel}, (rs, rowNum) -> GradeSubject.rehydrate(rs.getLong("id"), rs.getLong("academic_term_id"), rs.getInt("grade_level"), rs.getLong("subject_id"), rs.getString("subject_name"), rs.getInt("is_required"), rs.getInt("sort_order"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Override
    public List<GradeSubject> findActiveByTermAndGrade(long academicTermId, int gradeLevel) {
        return this.jdbcTemplate.query("SELECT gs.id, gs.academic_term_id, gs.grade_level, gs.subject_id, s.subject_name, gs.is_required, gs.sort_order, gs.status, gs.created_at FROM school_grade_subject gs LEFT JOIN school_subject s ON s.id = gs.subject_id WHERE gs.academic_term_id = ? AND gs.grade_level = ? AND gs.status = 1 ORDER BY gs.sort_order, gs.id", new Object[]{academicTermId, gradeLevel}, (rs, rowNum) -> GradeSubject.rehydrate(rs.getLong("id"), rs.getLong("academic_term_id"), rs.getInt("grade_level"), rs.getLong("subject_id"), rs.getString("subject_name"), rs.getInt("is_required"), rs.getInt("sort_order"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Override
    public void saveBatch(long academicTermId, int gradeLevel, List<GradeSubject> subjects) {
        this.jdbcTemplate.update("DELETE FROM school_grade_subject WHERE academic_term_id = ? AND grade_level = ?", new Object[]{academicTermId, gradeLevel});
        for (GradeSubject subject : subjects) {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            this.jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO school_grade_subject (academic_term_id, grade_level, subject_id, is_required, sort_order, status) VALUES (?, ?, ?, ?, ?, ?)", 1);
                ps.setLong(1, academicTermId);
                ps.setInt(2, gradeLevel);
                ps.setLong(3, subject.getSubjectId());
                ps.setInt(4, subject.getIsRequired());
                ps.setInt(5, subject.getSortOrder());
                ps.setInt(6, subject.getStatus());
                return ps;
            }, (KeyHolder)keyHolder);
        }
    }
}

