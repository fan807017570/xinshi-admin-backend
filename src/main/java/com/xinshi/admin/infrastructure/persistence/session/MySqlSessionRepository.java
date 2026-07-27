/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.infrastructure.persistence.session;

import com.xinshi.admin.domain.session.Session;
import com.xinshi.admin.domain.session.SessionRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class MySqlSessionRepository
implements SessionRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Session> findByToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Optional.empty();
        }
        List rows = this.jdbcTemplate.queryForList("SELECT token, user_id, login_name, real_name, role_codes, menus, landing_page, expires_at, is_active FROM sys_user_session WHERE token = ? AND is_active = 1 AND expires_at > CURRENT_TIMESTAMP", new Object[]{token});
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map row = (Map)rows.get(0);
        return Optional.of(this.mapToSession(row));
    }

    @Override
    public void save(Session session) {
        String roleCodes = session.getRoleCodes();
        String menus = String.join((CharSequence)",", session.getMenus());
        String landingPage = session.getLandingPage();
        int updated = this.jdbcTemplate.update("UPDATE sys_user_session SET user_id = ?, login_name = ?, real_name = ?, role_codes = ?, menus = ?, landing_page = ?, is_active = 1, last_access_at = CURRENT_TIMESTAMP, expires_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 HOUR), updated_at = CURRENT_TIMESTAMP WHERE token = ?", new Object[]{session.getUserId(), session.getLoginName(), session.getRealName(), roleCodes, menus, landingPage, session.getToken()});
        if (updated == 0) {
            this.jdbcTemplate.update("INSERT INTO sys_user_session (token, user_id, login_name, real_name, role_codes, menus, landing_page, is_active, last_access_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 HOUR))", new Object[]{session.getToken(), session.getUserId(), session.getLoginName(), session.getRealName(), roleCodes, menus, landingPage});
        }
    }

    @Override
    public boolean updateAccess(String token) {
        int updated = this.jdbcTemplate.update("UPDATE sys_user_session SET last_access_at = CURRENT_TIMESTAMP, expires_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 HOUR), updated_at = CURRENT_TIMESTAMP WHERE token = ? AND is_active = 1 AND expires_at > CURRENT_TIMESTAMP", new Object[]{token});
        return updated > 0;
    }

    @Override
    public void invalidate(String token) {
        this.jdbcTemplate.update("UPDATE sys_user_session SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE token = ?", new Object[]{token});
    }

    @Override
    public void invalidateByUserId(long userId) {
        this.jdbcTemplate.update("UPDATE sys_user_session SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND is_active = 1", new Object[]{userId});
    }

    private Session mapToSession(Map<String, Object> row) {
        String roleCodesStr = Objects.toString(row.get("role_codes"), "");
        String menusStr = Objects.toString(row.get("menus"), "");
        List<String> roles = roleCodesStr.isEmpty() ? Collections.emptyList() : this.stringList(roleCodesStr);
        List<String> menus = menusStr.isEmpty() ? Collections.emptyList() : this.stringList(menusStr);
        Object expiresObj = row.get("expires_at");
        LocalDateTime expiresAt = null;
        if (expiresObj instanceof LocalDateTime) {
            expiresAt = (LocalDateTime)expiresObj;
        } else if (expiresObj instanceof Timestamp) {
            expiresAt = ((Timestamp)expiresObj).toLocalDateTime();
        }
        boolean active = row.get("is_active") instanceof Number && ((Number)row.get("is_active")).intValue() == 1;
        Session session = Session.rehydrate((String)row.get("token"), ((Number)row.get("user_id")).longValue(), (String)row.get("login_name"), (String)row.get("real_name"), roleCodesStr, roles, menus, (String)row.get("landing_page"), expiresAt, active);
        return session;
    }

    private List<String> stringList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = value.split(",");
        ArrayList<String> list = new ArrayList<String>();
        for (String part : parts) {
            if (!StringUtils.hasText((String)part)) continue;
            list.add(part.trim());
        }
        return list;
    }
}

