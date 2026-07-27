/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.auth;

import com.xinshi.admin.application.auth.command.LoginCommand;
import com.xinshi.admin.domain.menu.MenuRepository;
import com.xinshi.admin.domain.role.RoleRepository;
import com.xinshi.admin.domain.session.Session;
import com.xinshi.admin.domain.session.SessionRepository;
import com.xinshi.admin.interfaces.web.security.AuthContext;
import com.xinshi.admin.interfaces.web.security.UnauthorizedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthApplicationService {
    private final SessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Map<String, Object>> activeSessions = new ConcurrentHashMap<String, Map<String, Object>>();

    public AuthApplicationService(SessionRepository sessionRepository, RoleRepository roleRepository, MenuRepository menuRepository, JdbcTemplate jdbcTemplate) {
        this.sessionRepository = sessionRepository;
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void invalidateAllSessionsOnStartup() {
        this.jdbcTemplate.update("UPDATE sys_user_session SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE is_active = 1");
    }

    public Map<String, Object> login(LoginCommand command) {
        String loginName = command.getLoginName();
        String password = command.getPassword();
        List users = this.jdbcTemplate.queryForList("SELECT u.id, u.login_name AS loginName, u.password_hash AS passwordHash, u.real_name AS realName, u.mobile, u.email, u.status, COALESCE(GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ','), '') AS roleCodes FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id = u.id LEFT JOIN sys_role r ON r.id = ur.role_id WHERE LOWER(u.login_name) = LOWER(?) AND u.is_deleted = 0 GROUP BY u.id", new Object[]{loginName});
        if (users.isEmpty()) {
            throw new IllegalArgumentException("账号不存在");
        }
        LinkedHashMap<String, Object> user = new LinkedHashMap<String, Object>((Map)users.get(0));
        if (this.toInt(user.get("status"), 0) != 1) {
            throw new IllegalArgumentException("账号已停用");
        }
        if (!this.passwordMatches(Objects.toString(user.get("passwordHash"), ""), password)) {
            throw new IllegalArgumentException("密码错误");
        }
        long userId = this.toLong(user.get("id"));
        this.sessionRepository.invalidateByUserId(userId);
        this.removeCachedSessionsByUserId(userId);
        String token = UUID.randomUUID().toString().replace("-", "");
        List<String> roleCodes = this.stringList(user.get("roleCodes"));
        Map<String, Object> sessionMap = this.buildSessionMap(user, token, roleCodes);
        this.upsertSession(token, sessionMap, user.get("id"));
        this.jdbcTemplate.update("UPDATE sys_user SET last_login_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{user.get("id")});
        return sessionMap;
    }

    public Map<String, Object> currentUser(String token) {
        Optional<Session> dbSession;
        if (!StringUtils.hasText((String)token)) {
            throw new UnauthorizedException("未登录");
        }
        Map<String, Object> session = this.activeSessions.get(token);
        if (session == null && (dbSession = this.sessionRepository.findByToken(token)).isPresent()) {
            session = this.toSessionMap(dbSession.get());
            this.activeSessions.put(token, session);
        }
        if (session != null) {
            if (!this.sessionRepository.updateAccess(token)) {
                this.activeSessions.remove(token);
                throw new UnauthorizedException("登录已过期");
            }
            return new LinkedHashMap<String, Object>(session);
        }
        throw new UnauthorizedException("未登录");
    }

    public void logout(String token) {
        if (StringUtils.hasText((String)token)) {
            this.activeSessions.remove(token);
            this.sessionRepository.invalidate(token);
        }
    }

    public Map<String, Object> updateProfile(Map<String, Object> request) {
        String trimmed;
        long userId = AuthContext.userId();
        if (userId == 0L) {
            throw new UnauthorizedException("未登录");
        }
        String mobile = this.optionalString(request, "mobile", null);
        String email = this.optionalString(request, "email", null);
        String oldPassword = this.optionalString(request, "oldPassword", null);
        String newPassword = this.optionalString(request, "newPassword", null);
        if (StringUtils.hasText((String)mobile) && ((trimmed = mobile.trim()).length() != 11 || !trimmed.chars().allMatch(Character::isDigit))) {
            throw new IllegalArgumentException("手机号必须为11位数字");
        }
        if (StringUtils.hasText((String)newPassword)) {
            if (!StringUtils.hasText((String)oldPassword)) {
                throw new IllegalArgumentException("修改密码需要提供旧密码");
            }
            List rows = this.jdbcTemplate.queryForList("SELECT password_hash FROM sys_user WHERE id = ? AND is_deleted = 0", new Object[]{userId});
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("用户不存在");
            }
            String passwordHash = Objects.toString(((Map)rows.get(0)).get("password_hash"), "");
            if (!this.passwordMatches(passwordHash, oldPassword)) {
                throw new IllegalArgumentException("旧密码错误");
            }
            AuthApplicationService.validatePassword(newPassword);
            this.jdbcTemplate.update("UPDATE sys_user SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0", new Object[]{this.prefixPassword(newPassword), userId});
        }
        ArrayList<Object> args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE sys_user SET ");
        boolean first = true;
        if (mobile != null) {
            sql.append("mobile = ?");
            args.add(mobile.trim());
            first = false;
        }
        if (email != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("email = ?");
            args.add(email.trim());
            first = false;
        }
        if (!args.isEmpty()) {
            sql.append(", updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0");
            args.add(userId);
            this.jdbcTemplate.update(sql.toString(), args.toArray());
        }
        for (Map.Entry<String, Map<String, Object>> entry : this.activeSessions.entrySet()) {
            Map<String, Object> session = entry.getValue();
            Object sessionUserId = session.get("userId");
            long sessionUid = 0L;
            if (sessionUserId instanceof Number) {
                sessionUid = ((Number)sessionUserId).longValue();
            }
            if (sessionUid != userId) continue;
            if (mobile != null) {
                session.put("mobile", mobile.trim());
            }
            if (email == null) continue;
            session.put("email", email.trim());
        }
        return this.getUserById(userId);
    }

    private Map<String, Object> getUserById(long userId) {
        List users = this.jdbcTemplate.queryForList("SELECT u.id, u.login_name AS loginName, u.real_name AS realName, u.mobile, u.email, u.status, u.last_login_at AS lastLoginAt, u.created_at AS createdAt, COALESCE(GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ','), '') AS roleCodes FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id = u.id LEFT JOIN sys_role r ON r.id = ur.role_id WHERE u.id = ? AND u.is_deleted = 0 GROUP BY u.id", new Object[]{userId});
        if (users.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        LinkedHashMap<String, Object> user = new LinkedHashMap<String, Object>((Map)users.get(0));
        List<String> roleCodes = this.stringList(user.get("roleCodes"));
        user.put("roles", roleCodes);
        user.put("menus", this.menuRepository.findMenuCodesByRoles(roleCodes));
        user.put("landingPage", this.loadLandingPage(roleCodes));
        return user;
    }

    private String optionalString(Map<String, Object> request, String key, String defaultValue) {
        Object value = request.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private static void validatePassword(String password) {
        if (!StringUtils.hasText((String)password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < 10) {
            throw new IllegalArgumentException("密码长度不能小于10位");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
                continue;
            }
            if (Character.isLowerCase(c)) {
                hasLower = true;
                continue;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
                continue;
            }
            hasSpecial = true;
        }
        if (!hasUpper) {
            throw new IllegalArgumentException("密码必须包含大写字母");
        }
        if (!hasLower) {
            throw new IllegalArgumentException("密码必须包含小写字母");
        }
        if (!hasDigit) {
            throw new IllegalArgumentException("密码必须包含数字");
        }
        if (!hasSpecial) {
            throw new IllegalArgumentException("密码必须包含特殊字符");
        }
    }

    private String prefixPassword(String password) {
        if (!StringUtils.hasText((String)password)) {
            return "{bcrypt}change-me-default";
        }
        String normalized = password.trim();
        return normalized.startsWith("{bcrypt}") ? normalized : "{bcrypt}" + normalized;
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            List<?> raw = (List<?>)value;
            return raw.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText((String)text)) {
            return Collections.emptyList();
        }
        return Arrays.stream(text.split(",")).filter(StringUtils::hasText).map(String::trim).collect(Collectors.toList());
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number)value).intValue();
        }
        return defaultValue;
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        return 0L;
    }

    private void removeCachedSessionsByUserId(long userId) {
        this.activeSessions.entrySet().removeIf(entry -> {
            Object sessionUserId = ((Map)entry.getValue()).get("userId");
            if (sessionUserId instanceof Number) {
                return ((Number)sessionUserId).longValue() == userId;
            }
            return false;
        });
    }

    private Map<String, Object> buildSessionMap(Map<String, Object> user, String token, List<String> roleCodes) {
        LinkedHashMap<String, Object> session = new LinkedHashMap<String, Object>();
        session.put("token", token);
        session.put("userId", user.get("id"));
        session.put("loginName", user.get("loginName"));
        session.put("realName", user.get("realName"));
        session.put("mobile", user.get("mobile"));
        session.put("email", user.get("email"));
        session.put("status", user.get("status"));
        session.put("roleCodes", String.join((CharSequence)",", roleCodes));
        session.put("roles", roleCodes);
        session.put("menus", this.menuRepository.findMenuCodesByRoles(roleCodes));
        session.put("landingPage", this.loadLandingPage(roleCodes));
        return session;
    }

    private Map<String, Object> toSessionMap(Session session) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("token", session.getToken());
        map.put("userId", session.getUserId());
        map.put("loginName", session.getLoginName());
        map.put("realName", session.getRealName());
        map.put("roles", session.getRoles());
        map.put("roleCodes", String.join((CharSequence)",", session.getRoles()));
        map.put("menus", session.getMenus());
        map.put("landingPage", session.getLandingPage());
        map.put("expiresAt", session.getExpiresAt());
        return map;
    }

    private void upsertSession(String token, Map<String, Object> sessionMap, Object userId) {
        String roleCodes = Objects.toString(sessionMap.get("roleCodes"), "");
        String menus = String.join((CharSequence)",", this.stringList(sessionMap.get("menus")));
        String landingPage = Objects.toString(sessionMap.get("landingPage"), "");
        int updated = this.jdbcTemplate.update("UPDATE sys_user_session SET user_id = ?, login_name = ?, real_name = ?, role_codes = ?, menus = ?, landing_page = ?, is_active = 1, last_access_at = CURRENT_TIMESTAMP, expires_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 HOUR), updated_at = CURRENT_TIMESTAMP WHERE token = ?", new Object[]{userId, sessionMap.get("loginName"), sessionMap.get("realName"), roleCodes, menus, landingPage, token});
        if (updated == 0) {
            this.jdbcTemplate.update("INSERT INTO sys_user_session (token, user_id, login_name, real_name, role_codes, menus, landing_page, is_active, last_access_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 HOUR))", new Object[]{token, userId, sessionMap.get("loginName"), sessionMap.get("realName"), roleCodes, menus, landingPage});
        }
    }

    private String loadLandingPage(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return null;
        }
        try {
            String placeholders = roleCodes.stream().map(c -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> rows = this.jdbcTemplate.queryForList("SELECT role_code, landing_page FROM sys_role WHERE role_code IN (" + placeholders + ") AND landing_page IS NOT NULL AND status = 1", roleCodes.toArray());
            LinkedHashMap<String, String> pageMap = new LinkedHashMap<String, String>();
            for (Map<String, Object> row : rows) {
                pageMap.put((String)row.get("role_code"), (String)row.get("landing_page"));
            }
            for (String code : roleCodes) {
                String page = (String)pageMap.get(code);
                if (page == null) continue;
                return page;
            }
            return null;
        }
        catch (Exception e) {
            if (roleCodes.contains("HEAD_TEACHER")) {
                return "/students";
            }
            if (roleCodes.contains("TEACHER")) {
                return "/scores";
            }
            if (roleCodes.contains("PARENT")) {
                return "/parents";
            }
            return null;
        }
    }

    private boolean passwordMatches(String passwordHash, String inputPassword) {
        if (!StringUtils.hasText((String)inputPassword) || !StringUtils.hasText((String)passwordHash)) {
            return false;
        }
        String normalized = passwordHash.trim();
        if (normalized.startsWith("{bcrypt}")) {
            normalized = normalized.substring("{bcrypt}".length());
        }
        return normalized.equals(inputPassword);
    }
}

