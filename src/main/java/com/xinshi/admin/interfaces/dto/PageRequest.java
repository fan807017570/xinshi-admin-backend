/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.interfaces.dto;

import java.util.Objects;

public class PageRequest {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;
    private final int page;
    private final int size;

    public PageRequest(int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 200) {
            size = 200;
        }
        this.page = page;
        this.size = size;
    }

    public int offset() {
        return (this.page - 1) * this.size;
    }

    public int limit() {
        return this.size;
    }

    public int page() {
        return this.page;
    }

    public int size() {
        return this.size;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PageRequest)) {
            return false;
        }
        PageRequest that = (PageRequest)o;
        return this.page == that.page && this.size == that.size;
    }

    public int hashCode() {
        return Objects.hash(this.page, this.size);
    }
}

