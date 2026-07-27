/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.honortype;

import com.xinshi.admin.domain.honortype.HonorType;
import com.xinshi.admin.domain.honortype.HonorTypeRepository;
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
public class MySqlHonorTypeRepository
implements HonorTypeRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT_SQL = "SELECT id, honor_type_code, honor_type_name, sort_order, status, created_at FROM school_honor_type";

    public MySqlHonorTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<HonorType> findAll() {
        return this.jdbcTemplate.query("SELECT id, honor_type_code, honor_type_name, sort_order, status, created_at FROM school_honor_type ORDER BY sort_order, id", this::mapRow);
    }

    @Override
    public List<HonorType> findAllEnabled() {
        return this.jdbcTemplate.query("SELECT id, honor_type_code, honor_type_name, sort_order, status, created_at FROM school_honor_type WHERE status = 1 ORDER BY sort_order, id", this::mapRow);
    }

    @Override
    public Optional<HonorType> findById(long id) {
        List list = this.jdbcTemplate.query("SELECT id, honor_type_code, honor_type_name, sort_order, status, created_at FROM school_honor_type WHERE id = ?", new Object[]{id}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public Optional<HonorType> findByCode(String honorTypeCode) {
        List list = this.jdbcTemplate.query("SELECT id, honor_type_code, honor_type_name, sort_order, status, created_at FROM school_honor_type WHERE honor_type_code = ?", new Object[]{honorTypeCode}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public HonorType save(HonorType honorType) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_honor_type (honor_type_code, honor_type_name, sort_order, status) VALUES (?, ?, ?, ?)", 1);
            ps.setString(1, honorType.getHonorTypeCode());
            ps.setString(2, honorType.getHonorTypeName());
            ps.setInt(3, honorType.getSortOrder());
            ps.setInt(4, honorType.getStatus());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert honor type failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted honor type not found"));
    }

    @Override
    public void update(HonorType honorType) {
        this.jdbcTemplate.update("UPDATE school_honor_type SET honor_type_name = ?, sort_order = ?, status = ? WHERE id = ?", new Object[]{honorType.getHonorTypeName(), honorType.getSortOrder(), honorType.getStatus(), honorType.getId()});
    }

    @Override
    public void delete(long id) {
        this.jdbcTemplate.update("DELETE FROM school_honor_type WHERE id = ?", new Object[]{id});
    }

    private HonorType mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return HonorType.rehydrate(rs.getLong("id"), rs.getString("honor_type_code"), rs.getString("honor_type_name"), rs.getInt("sort_order"), rs.getInt("status"), createdAt != null ? createdAt.toLocalDateTime() : null);
    }
}

