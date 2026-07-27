/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.menu;

import com.xinshi.admin.domain.menu.Menu;
import java.util.List;

public interface MenuRepository {
    public List<Menu> findAllActive();

    public List<String> findMenuCodesByRoles(List<String> var1);

    public List<String> findMenuCodesByRolesFallback(List<String> var1);
}

