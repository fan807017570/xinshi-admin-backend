/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.user;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserManagementService
extends SchoolBaseService {
    private static final int MOBILE_LENGTH = 11;
    private final AccessControlService accessControlService;

    public UserManagementService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
    }

    public PageResult<Map<String, Object>> listUsers(String keyword, Integer status, String roleCode, PageRequest pageRequest) {
        StringBuilder where = new StringBuilder(" WHERE u.is_deleted = 0");
        ArrayList<Object> args = new ArrayList<Object>();
        if (StringUtils.hasText((String)keyword)) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            where.append(" AND (LOWER(u.login_name) LIKE ? OR LOWER(u.real_name) LIKE ?)");
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            where.append(" AND u.status = ?");
            args.add(status);
        }
        if (StringUtils.hasText((String)roleCode)) {
            where.append(" AND EXISTS (SELECT 1 FROM sys_user_role ur2 JOIN sys_role r2 ON r2.id = ur2.role_id WHERE ur2.user_id = u.id AND r2.role_code = ?)");
            args.add(roleCode.trim());
        }
        String countSql = "SELECT COUNT(1) FROM (SELECT u.id FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id = u.id LEFT JOIN sys_role r ON r.id = ur.role_id" + where + " GROUP BY u.id) cnt";
        long total = (Long)this.jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        StringBuilder dataSql = new StringBuilder("SELECT u.id, u.login_name AS loginName, u.real_name AS realName, u.mobile, u.email, u.status, u.last_login_at AS lastLoginAt, u.created_at AS createdAt, u.updated_at AS updatedAt, COALESCE(GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ','), '') AS roleCodes, u.updated_by AS updatedBy, mu.real_name AS updatedByName FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id = u.id LEFT JOIN sys_role r ON r.id = ur.role_id LEFT JOIN sys_user mu ON mu.id = u.updated_by");
        dataSql.append((CharSequence)where);
        dataSql.append(" GROUP BY u.id ORDER BY u.id DESC LIMIT ? OFFSET ?");
        args.add(pageRequest.limit());
        args.add(pageRequest.offset());
        List items = this.jdbcTemplate.queryForList(dataSql.toString(), args.toArray());
        return new PageResult<Map<String, Object>>(items, total, pageRequest.page(), pageRequest.size());
    }

    public Map<String, Object> getUser(long id) {
        List list = this.jdbcTemplate.queryForList("SELECT u.id, u.login_name AS loginName, u.real_name AS realName, u.mobile, u.email, u.status, u.last_login_at AS lastLoginAt, u.created_at AS createdAt, u.updated_at AS updatedAt, COALESCE(GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ','), '') AS roleCodes, u.updated_by AS updatedBy, mu.real_name AS updatedByName FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id = u.id LEFT JOIN sys_role r ON r.id = ur.role_id LEFT JOIN sys_user mu ON mu.id = u.updated_by WHERE u.id = ? AND u.is_deleted = 0 GROUP BY u.id", new Object[]{id});
        return this.first(list);
    }

    public boolean loginNameExists(String loginName) {
        if (!StringUtils.hasText((String)loginName)) {
            return false;
        }
        return this.exists("SELECT COUNT(1) FROM sys_user WHERE LOWER(login_name) = LOWER(?) AND is_deleted = 0", loginName.trim()) > 0;
    }

    public Map<String, Object> createUser(Map<String, Object> request) {
        String loginName = this.requiredString(request, "loginName");
        String realName = this.requiredString(request, "realName");
        String password = this.optionalString(request, "password", "change-me-default");
        String mobile = this.optionalString(request, "mobile", null);
        String email = this.optionalString(request, "email", null);
        Integer status = this.optionalInteger(request, "status", 1);
        List<String> roleCodes = this.stringList(request.get("roleCodes"));
        UserManagementService.validatePassword(password);
        UserManagementService.validateMobile(mobile);
        if (this.exists("SELECT COUNT(1) FROM sys_user WHERE LOWER(login_name) = LOWER(?) AND is_deleted = 0", loginName) > 0) {
            throw new IllegalArgumentException("登录账号已存在");
        }
        long userId = this.insert("sys_user", "INSERT INTO sys_user (login_name, password_hash, real_name, mobile, email, status, is_deleted) VALUES (?, ?, ?, ?, ?, ?, 0)", loginName, this.prefixPassword(password), realName, mobile, email, status);
        this.assignRoles(userId, roleCodes);
        return this.getUser(userId);
    }

    public Map<String, Object> updateUser(long id, Map<String, Object> request) {
        this.accessControlService.ensureMutableUser(id);
        // loginName 不允许修改，忽略请求中的 loginName
        String realName = this.optionalString(request, "realName", null);
        String mobile = this.optionalString(request, "mobile", null);
        String email = this.optionalString(request, "email", null);
        Integer status = this.optionalInteger(request, "status", null);
        String password = this.optionalString(request, "password", null);
        UserManagementService.validateMobile(mobile);
        ArrayList<Object> args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE sys_user SET ");
        boolean first = true;
        if (realName != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("real_name = ?");
            args.add(realName);
            first = false;
        }
        if (mobile != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("mobile = ?");
            args.add(mobile);
            first = false;
        }
        if (email != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("email = ?");
            args.add(email);
            first = false;
        }
        if (status != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("status = ?");
            args.add(status);
            first = false;
        }
        if (password != null) {
            UserManagementService.validatePassword(password);
            if (!first) {
                sql.append(", ");
            }
            sql.append("password_hash = ?");
            args.add(this.prefixPassword(password));
            first = false;
        }
        if (args.isEmpty()) {
            return this.getUser(id);
        }
        sql.append(", updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND is_deleted = 0");
        args.add(this.accessControlService.currentUserId());
        args.add(id);
        this.jdbcTemplate.update(sql.toString(), args.toArray());
        return this.getUser(id);
    }

    public Map<String, Object> updateUserStatus(long id, int status) {
        this.accessControlService.ensureMutableUser(id);
        this.jdbcTemplate.update("UPDATE sys_user SET status = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND is_deleted = 0", new Object[]{status, this.accessControlService.currentUserId(), id});
        return this.getUser(id);
    }

    public void assignRoles(long userId, List<String> roleCodes) {
        this.accessControlService.ensureMutableUser(userId);
        this.jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", new Object[]{userId});
        if (roleCodes == null) {
            return;
        }
        for (String roleCode : roleCodes) {
            if (!StringUtils.hasText((String)roleCode)) continue;
            Integer protectedCount = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_role WHERE role_code = ? AND is_protected = 1", Integer.class, new Object[]{roleCode.trim()});
            if (protectedCount != null && protectedCount > 0) {
                throw new IllegalArgumentException("系统保护角色不能通过普通管理接口分配");
            }
            List roleIds = this.jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ?", new Object[]{roleCode.trim()}, (rs, rowNum) -> rs.getLong("id"));
            if (roleIds.isEmpty()) continue;
            this.jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", new Object[]{userId, roleIds.get(0)});
        }
    }

    public List<Map<String, Object>> listRoles() {
        return this.jdbcTemplate.queryForList("SELECT id, role_code AS roleCode, role_name AS roleName, landing_page AS landingPage, status, is_protected AS isProtected, created_at AS createdAt FROM sys_role ORDER BY id");
    }

    public Map<String, Object> createRole(Map<String, Object> request) {
        String roleCode = this.requiredString(request, "roleCode");
        String roleName = this.requiredString(request, "roleName");
        Integer status = this.optionalInteger(request, "status", 1);
        if (this.exists("SELECT COUNT(1) FROM sys_role WHERE role_code = ?", roleCode) > 0) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        long roleId = this.insert("sys_role", "INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, ?)", roleCode, roleName, status);
        return this.first(this.jdbcTemplate.queryForList("SELECT id, role_code AS roleCode, role_name AS roleName, status, created_at AS createdAt FROM sys_role WHERE id = ?", new Object[]{roleId}));
    }

    public List<Map<String, Object>> listMenus() {
        return this.jdbcTemplate.queryForList("SELECT menu_code AS menuCode, menu_label AS menuLabel, sort_order AS sortOrder, status FROM sys_menu WHERE status = 1 ORDER BY sort_order");
    }

    private boolean passwordMatches(String passwordHash, String inputPassword) {
        if (!StringUtils.hasText((String)inputPassword)) {
            return false;
        }
        if (!StringUtils.hasText((String)passwordHash)) {
            return false;
        }
        String normalized = passwordHash.trim();
        if (normalized.startsWith("{bcrypt}")) {
            normalized = normalized.substring("{bcrypt}".length());
        }
        return normalized.equals(inputPassword);
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

    private static void validateMobile(String mobile) {
        if (!StringUtils.hasText((String)mobile)) {
            return;
        }
        String trimmed = mobile.trim();
        if (trimmed.length() != 11 || !trimmed.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("手机号必须为11位数字");
        }
    }

    private String prefixPassword(String password) {
        if (!StringUtils.hasText((String)password)) {
            return "{bcrypt}change-me-default";
        }
        String normalized = password.trim();
        return normalized.startsWith("{bcrypt}") ? normalized : "{bcrypt}" + normalized;
    }

    private List<String> menusForRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String placeholders = roleCodes.stream().map(c -> "?").collect(Collectors.joining(","));
            return this.jdbcTemplate.queryForList("SELECT DISTINCT rm.menu_code FROM sys_role_menu rm JOIN sys_role r ON r.role_code = rm.role_code AND r.status = 1 JOIN sys_menu m ON m.menu_code = rm.menu_code AND m.status = 1 WHERE rm.role_code IN (" + placeholders + ") ORDER BY (SELECT m2.sort_order FROM sys_menu m2 WHERE m2.menu_code = rm.menu_code)", String.class, roleCodes.toArray());
        }
        catch (Exception e) {
            return this.menusForRolesFallback(roleCodes);
        }
    }

    private List<String> menusForRolesFallback(List<String> roleCodes) {
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
}

