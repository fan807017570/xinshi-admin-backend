/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.interfaces.dto;

import com.xinshi.admin.domain.user.User;
import java.time.LocalDateTime;

public class UserResponse {
    private final String id;
    private final String username;
    private final String displayName;
    private final String email;
    private final String status;
    private final LocalDateTime createdAt;

    private UserResponse(String id, String username, String displayName, String email, String status, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.getId().getValue(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getStatus().name(), user.getCreatedAt());
    }

    public String getId() {
        return this.id;
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

    public String getStatus() {
        return this.status;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
}

