/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.honortype;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDateTime;

public class HonorType {
    private final long id;
    private String honorTypeCode;
    private String honorTypeName;
    private int sortOrder;
    private int status;
    private final LocalDateTime createdAt;

    private HonorType(long id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public static HonorType create(String honorTypeCode, String honorTypeName, int sortOrder) {
        if (HonorType.isBlank(honorTypeCode)) {
            throw new DomainException("荣誉类型编码不能为空");
        }
        if (honorTypeCode.length() > 32) {
            throw new DomainException("荣誉类型编码不能超过32个字符");
        }
        if (HonorType.isBlank(honorTypeName)) {
            throw new DomainException("荣誉类型名称不能为空");
        }
        if (honorTypeName.length() > 64) {
            throw new DomainException("荣誉类型名称不能超过64个字符");
        }
        HonorType honorType = new HonorType(0L, LocalDateTime.now());
        honorType.honorTypeCode = honorTypeCode.trim().toUpperCase();
        honorType.honorTypeName = honorTypeName.trim();
        honorType.sortOrder = Math.max(sortOrder, 0);
        honorType.status = 1;
        return honorType;
    }

    public static HonorType rehydrate(long id, String honorTypeCode, String honorTypeName, int sortOrder, int status, LocalDateTime createdAt) {
        HonorType honorType = new HonorType(id, createdAt);
        honorType.honorTypeCode = honorTypeCode;
        honorType.honorTypeName = honorTypeName;
        honorType.sortOrder = sortOrder;
        honorType.status = status;
        return honorType;
    }

    public void updateInfo(String honorTypeName, int sortOrder, int status) {
        if (HonorType.isBlank(honorTypeName)) {
            throw new DomainException("荣誉类型名称不能为空");
        }
        if (honorTypeName.length() > 64) {
            throw new DomainException("荣誉类型名称不能超过64个字符");
        }
        this.honorTypeName = honorTypeName.trim();
        this.sortOrder = Math.max(sortOrder, 0);
        this.status = status;
    }

    public long getId() {
        return this.id;
    }

    public String getHonorTypeCode() {
        return this.honorTypeCode;
    }

    public String getHonorTypeName() {
        return this.honorTypeName;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public int getStatus() {
        return this.status;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public boolean isEnabled() {
        return this.status == 1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

