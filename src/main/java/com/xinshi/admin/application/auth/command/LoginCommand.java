/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.application.auth.command;

public class LoginCommand {
    private final String loginName;
    private final String password;

    public LoginCommand(String loginName, String password) {
        this.loginName = loginName;
        this.password = password;
    }

    public String getLoginName() {
        return this.loginName;
    }

    public String getPassword() {
        return this.password;
    }
}

