/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.session;

import com.xinshi.admin.domain.session.Session;
import java.util.Optional;

public interface SessionRepository {
    public Optional<Session> findByToken(String var1);

    public void save(Session var1);

    public boolean updateAccess(String var1);

    public void invalidate(String var1);

    public void invalidateByUserId(long var1);
}

