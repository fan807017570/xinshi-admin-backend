/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.session;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class Session {
    private final String token;
    private final long userId;
    private final String loginName;
    private final String realName;
    private String mobile;
    private String email;
    private int status;
    private String roleCodes;
    private List<String> roles;
    private List<String> menus;
    private String landingPage;
    private LocalDateTime expiresAt;
    private boolean active;

    private Session(String token, long userId, String loginName, String realName) {
        this.token = token;
        this.userId = userId;
        this.loginName = loginName;
        this.realName = realName;
        this.roles = Collections.emptyList();
        this.menus = Collections.emptyList();
        this.active = true;
    }

    public static Session create(String token, long userId, String loginName, String realName) {
        return new Session(token, userId, loginName, realName);
    }

    public static Session rehydrate(String token, long userId, String loginName, String realName, String roleCodes, List<String> roles, List<String> menus, String landingPage, LocalDateTime expiresAt, boolean active) {
        Session session = new Session(token, userId, loginName, realName);
        session.roleCodes = roleCodes;
        session.roles = roles != null ? roles : Collections.emptyList();
        session.menus = menus != null ? menus : Collections.emptyList();
        session.landingPage = landingPage;
        session.expiresAt = expiresAt;
        session.active = active;
        return session;
    }

    public boolean isValid() {
        return this.active && this.expiresAt != null && this.expiresAt.isAfter(LocalDateTime.now());
    }

    public void invalidate() {
        this.active = false;
    }

    public String getToken() {
        return this.token;
    }

    public long getUserId() {
        return this.userId;
    }

    public String getLoginName() {
        return this.loginName;
    }

    public String getRealName() {
        return this.realName;
    }

    public String getMobile() {
        return this.mobile;
    }

    public String getEmail() {
        return this.email;
    }

    public int getStatus() {
        return this.status;
    }

    public String getRoleCodes() {
        return this.roleCodes;
    }

    public List<String> getRoles() {
        return this.roles;
    }

    public List<String> getMenus() {
        return this.menus;
    }

    public String getLandingPage() {
        return this.landingPage;
    }

    public LocalDateTime getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setRoleCodes(String roleCodes) {
        this.roleCodes = roleCodes;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public void setMenus(List<String> menus) {
        this.menus = menus;
    }

    public void setLandingPage(String landingPage) {
        this.landingPage = landingPage;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}

