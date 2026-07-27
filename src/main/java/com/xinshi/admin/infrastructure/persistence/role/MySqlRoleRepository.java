/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.role;

import com.xinshi.admin.domain.role.Role;
import com.xinshi.admin.domain.role.RoleRepository;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlRoleRepository
implements RoleRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlRoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Role> findById(long id) {
        List roles = this.jdbcTemplate.query("SELECT id, role_code, role_name, landing_page, is_protected, status, created_at FROM sys_role WHERE id = ?", new Object[]{id}, (rs, rowNum) -> Role.rehydrate(rs.getLong("id"), rs.getString("role_code"), rs.getString("role_name"), rs.getString("landing_page"), rs.getInt("is_protected") == 1, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
        return roles.stream().findFirst();
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return Optional.empty();
        }
        List roles = this.jdbcTemplate.query("SELECT id, role_code, role_name, landing_page, is_protected, status, created_at FROM sys_role WHERE role_code = ?", new Object[]{roleCode.trim()}, (rs, rowNum) -> Role.rehydrate(rs.getLong("id"), rs.getString("role_code"), rs.getString("role_name"), rs.getString("landing_page"), rs.getInt("is_protected") == 1, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
        return roles.stream().findFirst();
    }

    @Override
    public List<Role> findAll() {
        return this.jdbcTemplate.query("SELECT id, role_code, role_name, landing_page, is_protected, status, created_at FROM sys_role ORDER BY id", (rs, rowNum) -> Role.rehydrate(rs.getLong("id"), rs.getString("role_code"), rs.getString("role_name"), rs.getString("landing_page"), rs.getInt("is_protected") == 1, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Override
    public Role save(Role role) {
        if (role.getId() == 0L) {
            return this.insert(role);
        }
        this.jdbcTemplate.update("UPDATE sys_role SET role_code = ?, role_name = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{role.getRoleCode(), role.getRoleName(), role.getStatus(), role.getId()});
        return role;
    }

    @Override
    public long count() {
        Long count = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_role", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public boolean isProtectedRole(String roleCode) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_role WHERE role_code = ? AND is_protected = 1", Integer.class, new Object[]{roleCode});
        return count != null && count > 0;
    }

    @Override
    public Map<String, String> findLandingPages(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return new LinkedHashMap<String, String>();
        }
        String placeholders = String.join((CharSequence)",", (CharSequence[])roleCodes.stream().map(c -> "?").toArray(String[]::new));
        List<Map<String, Object>> rows = this.jdbcTemplate.queryForList("SELECT role_code, landing_page FROM sys_role WHERE role_code IN (" + placeholders + ") AND landing_page IS NOT NULL AND status = 1", roleCodes.toArray());
        LinkedHashMap<String, String> pageMap = new LinkedHashMap<String, String>();
        for (Map<String, Object> row : rows) {
            pageMap.put((String)row.get("role_code"), (String)row.get("landing_page"));
        }
        return pageMap;
    }

    private Role insert(Role role) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, ?)", 1);
            ps.setString(1, role.getRoleCode());
            ps.setString(2, role.getRoleName());
            ps.setInt(3, role.getStatus());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert role failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted role not found"));
    }
}

