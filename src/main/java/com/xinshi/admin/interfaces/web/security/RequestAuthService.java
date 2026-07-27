/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.interfaces.web.security;

import com.xinshi.admin.application.auth.AuthApplicationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RequestAuthService {
    private final AuthApplicationService authApplicationService;

    public RequestAuthService(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    public Map<String, Object> resolveSession(String token, String loginName) {
        return this.authApplicationService.currentUser(this.normalizeToken(token));
    }

    public void invalidate(String token) {
        this.authApplicationService.logout(this.normalizeToken(token));
    }

    public boolean hasAnyRole(Map<String, Object> session, String ... roles) {
        if (session == null || roles == null || roles.length == 0) {
            return true;
        }
        Object value = session.get("roles");
        if (!(value instanceof List)) {
            return false;
        }
        List userRoles = (List)value;
        for (String role : roles) {
            if (role == null) continue;
            for (Object userRole : userRoles) {
                if (!role.equals(String.valueOf(userRole))) continue;
                return true;
            }
        }
        return false;
    }

    public String normalizeToken(String token) {
        if (!StringUtils.hasText((String)token)) {
            return null;
        }
        String candidate = token.trim();
        if (candidate.toLowerCase().startsWith("bearer ")) {
            candidate = candidate.substring(7).trim();
        }
        return candidate;
    }
}

