/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.examtype;

import com.xinshi.admin.domain.examtype.ExamType;
import com.xinshi.admin.domain.examtype.ExamTypeRepository;
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
public class MySqlExamTypeRepository
implements ExamTypeRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT_SQL = "SELECT id, exam_type_code, exam_type_name, sort_order, status, created_at FROM school_exam_type";

    public MySqlExamTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExamType> findAll() {
        return this.jdbcTemplate.query("SELECT id, exam_type_code, exam_type_name, sort_order, status, created_at FROM school_exam_type ORDER BY sort_order, id", this::mapRow);
    }

    @Override
    public Optional<ExamType> findById(long id) {
        List list = this.jdbcTemplate.query("SELECT id, exam_type_code, exam_type_name, sort_order, status, created_at FROM school_exam_type WHERE id = ?", new Object[]{id}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public Optional<ExamType> findByCode(String examTypeCode) {
        List list = this.jdbcTemplate.query("SELECT id, exam_type_code, exam_type_name, sort_order, status, created_at FROM school_exam_type WHERE exam_type_code = ?", new Object[]{examTypeCode}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public ExamType save(ExamType examType) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_exam_type (exam_type_code, exam_type_name, sort_order, status) VALUES (?, ?, ?, ?)", 1);
            ps.setString(1, examType.getExamTypeCode());
            ps.setString(2, examType.getExamTypeName());
            ps.setInt(3, examType.getSortOrder());
            ps.setInt(4, examType.getStatus());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert exam type failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted exam type not found"));
    }

    @Override
    public void update(ExamType examType) {
        this.jdbcTemplate.update("UPDATE school_exam_type SET exam_type_name = ?, sort_order = ?, status = ? WHERE id = ?", new Object[]{examType.getExamTypeName(), examType.getSortOrder(), examType.getStatus(), examType.getId()});
    }

    @Override
    public void delete(long id) {
        this.jdbcTemplate.update("DELETE FROM school_exam_type WHERE id = ?", new Object[]{id});
    }

    private ExamType mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return ExamType.rehydrate(rs.getLong("id"), rs.getString("exam_type_code"), rs.getString("exam_type_name"), rs.getInt("sort_order"), rs.getInt("status"), createdAt != null ? createdAt.toLocalDateTime() : null);
    }
}

