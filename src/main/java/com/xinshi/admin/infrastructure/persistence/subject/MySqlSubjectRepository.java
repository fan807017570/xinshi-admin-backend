/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.subject;

import com.xinshi.admin.domain.subject.Subject;
import com.xinshi.admin.domain.subject.SubjectRepository;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlSubjectRepository
implements SubjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlSubjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Subject> findById(long id) {
        List subjects = this.jdbcTemplate.query("SELECT id, subject_code, subject_name, min_score, max_score, status, created_at FROM school_subject WHERE id = ?", new Object[]{id}, (rs, rowNum) -> Subject.rehydrate(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name"), rs.getDouble("min_score"), rs.getDouble("max_score"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
        return subjects.stream().findFirst();
    }

    @Override
    public Optional<Subject> findByCode(String subjectCode) {
        if (subjectCode == null || subjectCode.trim().isEmpty()) {
            return Optional.empty();
        }
        List subjects = this.jdbcTemplate.query("SELECT id, subject_code, subject_name, min_score, max_score, status, created_at FROM school_subject WHERE subject_code = ?", new Object[]{subjectCode.trim()}, (rs, rowNum) -> Subject.rehydrate(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name"), rs.getDouble("min_score"), rs.getDouble("max_score"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
        return subjects.stream().findFirst();
    }

    @Override
    public List<Subject> findAll() {
        return this.jdbcTemplate.query("SELECT id, subject_code, subject_name, min_score, max_score, status, created_at FROM school_subject ORDER BY id DESC", (rs, rowNum) -> Subject.rehydrate(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name"), rs.getDouble("min_score"), rs.getDouble("max_score"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Override
    public List<Subject> findAllAccessibleByTeacher(long teacherUserId, boolean teacherOnly) {
        StringBuilder sql = new StringBuilder("SELECT id, subject_code, subject_name, min_score, max_score, status, created_at FROM school_subject WHERE 1 = 1");
        if (teacherOnly) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class_subject cs WHERE cs.subject_id = school_subject.id AND cs.teacher_user_id = ? AND cs.status = 1)");
            return this.jdbcTemplate.query(sql.toString(), new Object[]{teacherUserId}, (rs, rowNum) -> Subject.rehydrate(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name"), rs.getDouble("min_score"), rs.getDouble("max_score"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
        }
        sql.append(" ORDER BY id DESC");
        return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> Subject.rehydrate(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name"), rs.getDouble("min_score"), rs.getDouble("max_score"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Override
    public Subject save(Subject subject) {
        if (subject.getId() == 0L) {
            long id = this.insert(subject);
            return this.findById(id).orElseThrow(() -> new IllegalStateException("Inserted subject not found"));
        }
        this.update(subject);
        return subject;
    }

    @Override
    public void update(Subject subject) {
        this.jdbcTemplate.update("UPDATE school_subject SET subject_code = ?, subject_name = ?, min_score = ?, max_score = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{subject.getSubjectCode(), subject.getSubjectName(), subject.getMinScore(), subject.getMaxScore(), subject.getStatus(), subject.getId()});
    }

    @Override
    public void deactivate(long id) {
        this.jdbcTemplate.update("UPDATE school_subject SET status = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
    }

    @Override
    public long count() {
        Long count = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_subject", Long.class);
        return count == null ? 0L : count;
    }

    private long insert(Subject subject) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_subject (subject_code, subject_name, min_score, max_score, status) VALUES (?, ?, ?, ?, ?)", 1);
            ps.setString(1, subject.getSubjectCode());
            ps.setString(2, subject.getSubjectName());
            ps.setDouble(3, subject.getMinScore());
            ps.setDouble(4, subject.getMaxScore());
            ps.setInt(5, subject.getStatus());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert subject failed");
        }
        return key.longValue();
    }
}

