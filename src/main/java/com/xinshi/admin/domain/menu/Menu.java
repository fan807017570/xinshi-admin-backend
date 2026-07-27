/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.menu;

public class Menu {
    private final String menuCode;
    private final String menuLabel;
    private final int sortOrder;
    private final int status;

    private Menu(String menuCode, String menuLabel, int sortOrder, int status) {
        this.menuCode = menuCode;
        this.menuLabel = menuLabel;
        this.sortOrder = sortOrder;
        this.status = status;
    }

    public static Menu rehydrate(String menuCode, String menuLabel, int sortOrder, int status) {
        return new Menu(menuCode, menuLabel, sortOrder, status);
    }

    public String getMenuCode() {
        return this.menuCode;
    }

    public String getMenuLabel() {
        return this.menuLabel;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public int getStatus() {
        return this.status;
    }

    public boolean isActive() {
        return this.status == 1;
    }
}

