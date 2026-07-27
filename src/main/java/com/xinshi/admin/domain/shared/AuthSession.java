/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.shared;

import java.util.List;

public interface AuthSession {
    public long userId();

    public String loginName();

    public boolean hasRole(String var1);

    public boolean hasAnyRole(String ... var1);

    public List<String> roles();

    public void ensureAtLeastOne(String ... var1);
}

