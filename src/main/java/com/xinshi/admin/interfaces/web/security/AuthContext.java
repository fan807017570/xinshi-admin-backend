/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.interfaces.web.security;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AuthContext {
    private static final ThreadLocal<Map<String, Object>> CURRENT = new ThreadLocal();

    private AuthContext() {
    }

    public static void set(Map<String, Object> session) {
        CURRENT.set(session);
    }

    public static Map<String, Object> get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static long userId() {
        Object value = AuthContext.value("userId");
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    public static String realName() {
        return Objects.toString(AuthContext.value("realName"), "");
    }

    public static String loginName() {
        return Objects.toString(AuthContext.value("loginName"), "");
    }

    public static List<String> roles() {
        Object value = AuthContext.value("roles");
        if (value instanceof List) {
            return (List)value;
        }
        return Collections.emptyList();
    }

    private static Object value(String key) {
        Map<String, Object> session = CURRENT.get();
        return session == null ? null : session.get(key);
    }
}

