/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.application.user.command;

public class CreateUserCommand {
    private final String username;
    private final String displayName;
    private final String email;

    public CreateUserCommand(String username, String displayName, String email) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
    }

    public String getUsername() {
        return this.username;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getEmail() {
        return this.email;
    }
}

