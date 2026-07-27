/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.user;

import com.xinshi.admin.domain.shared.DomainException;
import com.xinshi.admin.domain.user.UserId;
import com.xinshi.admin.domain.user.UserStatus;
import java.time.LocalDateTime;

public class User {
    private final UserId id;
    private final String username;
    private String displayName;
    private String email;
    private UserStatus status;
    private final LocalDateTime createdAt;

    private User(UserId id, String username, String displayName, String email, UserStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static User create(String username, String displayName, String email) {
        if (User.isBlank(username)) {
            throw new DomainException("Username must not be blank");
        }
        if (User.isBlank(displayName)) {
            throw new DomainException("Display name must not be blank");
        }
        return new User(UserId.newId(), username.trim(), displayName.trim(), User.normalize(email), UserStatus.ENABLED, LocalDateTime.now());
    }

    public static User rehydrate(UserId id, String username, String displayName, String email, UserStatus status, LocalDateTime createdAt) {
        if (id == null) {
            throw new DomainException("User id must not be null");
        }
        return new User(id, username, displayName, User.normalize(email), status == null ? UserStatus.ENABLED : status, createdAt == null ? LocalDateTime.now() : createdAt);
    }

    public void rename(String displayName) {
        if (User.isBlank(displayName)) {
            throw new DomainException("Display name must not be blank");
        }
        this.displayName = displayName.trim();
    }

    public void changeEmail(String email) {
        this.email = User.normalize(email);
    }

    public void enable() {
        this.status = UserStatus.ENABLED;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public UserId getId() {
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

    public UserStatus getStatus() {
        return this.status;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

