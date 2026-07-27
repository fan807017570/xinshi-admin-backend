/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.academicterm;

import com.xinshi.admin.domain.academicterm.AcademicTerm;
import com.xinshi.admin.domain.academicterm.AcademicTermRepository;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlAcademicTermRepository
implements AcademicTermRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlAcademicTermRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AcademicTerm> findById(long id) {
        List terms = this.jdbcTemplate.query("SELECT id, term_code, academic_year, term_name, start_date, end_date, status, created_at FROM school_academic_term WHERE id = ?", new Object[]{id}, (rs, rowNum) -> {
            Date startDate = rs.getDate("start_date");
            Date endDate = rs.getDate("end_date");
            return AcademicTerm.rehydrate(rs.getLong("id"), rs.getString("term_code"), rs.getString("academic_year"), rs.getString("term_name"), startDate != null ? startDate.toLocalDate() : null, endDate != null ? endDate.toLocalDate() : null, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
        });
        return terms.stream().findFirst();
    }

    @Override
    public Optional<AcademicTerm> findByCode(String termCode) {
        if (termCode == null || termCode.trim().isEmpty()) {
            return Optional.empty();
        }
        List terms = this.jdbcTemplate.query("SELECT id, term_code, academic_year, term_name, start_date, end_date, status, created_at FROM school_academic_term WHERE term_code = ?", new Object[]{termCode.trim()}, (rs, rowNum) -> {
            Date startDate = rs.getDate("start_date");
            Date endDate = rs.getDate("end_date");
            return AcademicTerm.rehydrate(rs.getLong("id"), rs.getString("term_code"), rs.getString("academic_year"), rs.getString("term_name"), startDate != null ? startDate.toLocalDate() : null, endDate != null ? endDate.toLocalDate() : null, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
        });
        return terms.stream().findFirst();
    }

    @Override
    public List<AcademicTerm> findAll() {
        return this.jdbcTemplate.query("SELECT id, term_code, academic_year, term_name, start_date, end_date, status, created_at FROM school_academic_term ORDER BY id DESC", (rs, rowNum) -> {
            Date startDate = rs.getDate("start_date");
            Date endDate = rs.getDate("end_date");
            return AcademicTerm.rehydrate(rs.getLong("id"), rs.getString("term_code"), rs.getString("academic_year"), rs.getString("term_name"), startDate != null ? startDate.toLocalDate() : null, endDate != null ? endDate.toLocalDate() : null, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
        });
    }

    @Override
    public AcademicTerm save(AcademicTerm term) {
        if (term.getId() == 0L) {
            long id = this.insert(term);
            return this.findById(id).orElseThrow(() -> new IllegalStateException("Inserted term not found"));
        }
        this.update(term);
        return term;
    }

    @Override
    public void update(AcademicTerm term) {
        this.jdbcTemplate.update("UPDATE school_academic_term SET term_name = ?, start_date = ?, end_date = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{term.getTermName(), term.getStartDate() != null ? Date.valueOf(term.getStartDate()) : null, term.getEndDate() != null ? Date.valueOf(term.getEndDate()) : null, term.getStatus(), term.getId()});
    }

    @Override
    public long count() {
        Long count = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_academic_term", Long.class);
        return count == null ? 0L : count;
    }

    private long insert(AcademicTerm term) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_academic_term (term_code, academic_year, term_name, start_date, end_date, status) VALUES (?, ?, ?, ?, ?, ?)", 1);
            ps.setString(1, term.getTermCode());
            ps.setString(2, term.getAcademicYear());
            ps.setString(3, term.getTermName());
            ps.setDate(4, term.getStartDate() != null ? Date.valueOf(term.getStartDate()) : null);
            ps.setDate(5, term.getEndDate() != null ? Date.valueOf(term.getEndDate()) : null);
            ps.setInt(6, term.getStatus());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert term failed");
        }
        return key.longValue();
    }
}

