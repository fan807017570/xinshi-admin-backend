/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.menu;

import com.xinshi.admin.domain.menu.Menu;
import com.xinshi.admin.domain.menu.MenuRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlMenuRepository
implements MenuRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlMenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Menu> findAllActive() {
        return this.jdbcTemplate.query("SELECT menu_code, menu_label, sort_order, status FROM sys_menu WHERE status = 1 ORDER BY sort_order", (rs, rowNum) -> Menu.rehydrate(rs.getString("menu_code"), rs.getString("menu_label"), rs.getInt("sort_order"), rs.getInt("status")));
    }

    @Override
    public List<String> findMenuCodesByRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String placeholders = roleCodes.stream().map(c -> "?").collect(Collectors.joining(","));
            return this.jdbcTemplate.queryForList("SELECT DISTINCT rm.menu_code FROM sys_role_menu rm JOIN sys_role r ON r.role_code = rm.role_code AND r.status = 1 JOIN sys_menu m ON m.menu_code = rm.menu_code AND m.status = 1 WHERE rm.role_code IN (" + placeholders + ") ORDER BY (SELECT m2.sort_order FROM sys_menu m2 WHERE m2.menu_code = rm.menu_code)", String.class, roleCodes.toArray());
        }
        catch (Exception e) {
            return this.findMenuCodesByRolesFallback(roleCodes);
        }
    }

    @Override
    public List<String> findMenuCodesByRolesFallback(List<String> roleCodes) {
        ArrayList<String> menus = new ArrayList<String>();
        if (roleCodes == null) {
            return menus;
        }
        if (roleCodes.contains("SUPER_ADMIN")) {
            Collections.addAll(menus, "config", "users", "classes", "students", "scores", "transcripts", "parents");
            return menus;
        }
        if (roleCodes.contains("HEAD_TEACHER")) {
            Collections.addAll(menus, "classes", "students", "scores", "transcripts", "parents");
        }
        if (roleCodes.contains("TEACHER")) {
            Collections.addAll(menus, "scores");
        }
        if (roleCodes.contains("PARENT")) {
            Collections.addAll(menus, "parents", "transcripts");
        }
        return menus.stream().distinct().collect(Collectors.toList());
    }
}

