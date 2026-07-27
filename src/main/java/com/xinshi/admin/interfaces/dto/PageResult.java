/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.interfaces.dto;

import java.util.List;

public class PageResult<T> {
    private final List<T> items;
    private final long total;
    private final int page;
    private final int size;
    private final int totalPages;

    public PageResult(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = size > 0 ? (int)Math.ceil((double)total / (double)size) : 0;
    }

    public List<T> getItems() {
        return this.items;
    }

    public long getTotal() {
        return this.total;
    }

    public int getPage() {
        return this.page;
    }

    public int getSize() {
        return this.size;
    }

    public int getTotalPages() {
        return this.totalPages;
    }
}

