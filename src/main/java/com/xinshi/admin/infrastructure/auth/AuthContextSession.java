/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 */
package com.xinshi.admin.infrastructure.auth;

import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.interfaces.web.security.AuthContext;
import com.xinshi.admin.interfaces.web.security.ForbiddenException;
import com.xinshi.admin.interfaces.web.security.UnauthorizedException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthContextSession
implements AuthSession {
    @Override
    public long userId() {
        return AuthContext.userId();
    }

    @Override
    public String loginName() {
        return AuthContext.loginName();
    }

    @Override
    public boolean hasRole(String roleCode) {
        List<String> roles = this.roles();
        return roles != null && roles.contains(roleCode);
    }

    @Override
    public boolean hasAnyRole(String ... roleCodes) {
        List<String> roles = this.roles();
        if (roles == null) {
            return false;
        }
        for (String roleCode : roleCodes) {
            if (!roles.contains(roleCode)) continue;
            return true;
        }
        return false;
    }

    @Override
    public List<String> roles() {
        Map<String, Object> session = AuthContext.get();
        if (session == null || session.isEmpty()) {
            throw new UnauthorizedException("未登录");
        }
        Object rolesObj = session.get("roles");
        if (rolesObj instanceof List) {
            return (List)rolesObj;
        }
        return Collections.emptyList();
    }

    @Override
    public void ensureAtLeastOne(String ... roleCodes) {
        if (!this.hasAnyRole(roleCodes)) {
            throw new ForbiddenException("权限不足");
        }
    }
}

