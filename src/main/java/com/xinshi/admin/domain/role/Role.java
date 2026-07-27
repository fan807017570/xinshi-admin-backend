/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.role;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDateTime;

public class Role {
    private final long id;
    private String roleCode;
    private String roleName;
    private String landingPage;
    private boolean isProtected;
    private int status;
    private final LocalDateTime createdAt;

    private Role(long id, String roleCode, String roleName, String landingPage, boolean isProtected, int status, LocalDateTime createdAt) {
        this.id = id;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.landingPage = landingPage;
        this.isProtected = isProtected;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Role create(String roleCode, String roleName, int status) {
        if (Role.isBlank(roleCode)) {
            throw new DomainException("角色编码不能为空");
        }
        if (Role.isBlank(roleName)) {
            throw new DomainException("角色名称不能为空");
        }
        return new Role(0L, roleCode.trim(), roleName.trim(), null, false, status, LocalDateTime.now());
    }

    public static Role rehydrate(long id, String roleCode, String roleName, String landingPage, boolean isProtected, int status, LocalDateTime createdAt) {
        return new Role(id, roleCode, roleName, landingPage, isProtected, status, createdAt);
    }

    public long getId() {
        return this.id;
    }

    public String getRoleCode() {
        return this.roleCode;
    }

    public String getRoleName() {
        return this.roleName;
    }

    public String getLandingPage() {
        return this.landingPage;
    }

    public boolean isProtected() {
        return this.isProtected;
    }

    public int getStatus() {
        return this.status;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

