/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.role;

import com.xinshi.admin.domain.role.Role;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RoleRepository {
    public Optional<Role> findById(long var1);

    public Optional<Role> findByCode(String var1);

    public List<Role> findAll();

    public Role save(Role var1);

    public long count();

    public boolean isProtectedRole(String var1);

    public Map<String, String> findLandingPages(List<String> var1);
}

