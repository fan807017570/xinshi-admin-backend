/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.user;

import java.util.Objects;
import java.util.UUID;

public final class UserId {
    private final String value;

    private UserId(String value) {
        this.value = value;
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID().toString());
    }

    public static UserId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("User id must not be blank");
        }
        return new UserId(value);
    }

    public String getValue() {
        return this.value;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserId)) {
            return false;
        }
        UserId userId = (UserId)o;
        return Objects.equals(this.value, userId.value);
    }

    public int hashCode() {
        return Objects.hash(this.value);
    }

    public String toString() {
        return this.value;
    }
}

