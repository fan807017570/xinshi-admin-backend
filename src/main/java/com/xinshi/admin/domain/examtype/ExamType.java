/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.examtype;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDateTime;

public class ExamType {
    private final long id;
    private String examTypeCode;
    private String examTypeName;
    private int sortOrder;
    private int status;
    private final LocalDateTime createdAt;

    private ExamType(long id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public static ExamType create(String examTypeCode, String examTypeName, int sortOrder) {
        if (ExamType.isBlank(examTypeCode)) {
            throw new DomainException("考试类型编码不能为空");
        }
        if (examTypeCode.length() > 32) {
            throw new DomainException("考试类型编码不能超过32个字符");
        }
        if (ExamType.isBlank(examTypeName)) {
            throw new DomainException("考试类型名称不能为空");
        }
        if (examTypeName.length() > 64) {
            throw new DomainException("考试类型名称不能超过64个字符");
        }
        ExamType examType = new ExamType(0L, LocalDateTime.now());
        examType.examTypeCode = examTypeCode.trim().toUpperCase();
        examType.examTypeName = examTypeName.trim();
        examType.sortOrder = Math.max(sortOrder, 0);
        examType.status = 1;
        return examType;
    }

    public static ExamType rehydrate(long id, String examTypeCode, String examTypeName, int sortOrder, int status, LocalDateTime createdAt) {
        ExamType examType = new ExamType(id, createdAt);
        examType.examTypeCode = examTypeCode;
        examType.examTypeName = examTypeName;
        examType.sortOrder = sortOrder;
        examType.status = status;
        return examType;
    }

    public void updateInfo(String examTypeName, int sortOrder, int status) {
        if (ExamType.isBlank(examTypeName)) {
            throw new DomainException("考试类型名称不能为空");
        }
        if (examTypeName.length() > 64) {
            throw new DomainException("考试类型名称不能超过64个字符");
        }
        this.examTypeName = examTypeName.trim();
        this.sortOrder = Math.max(sortOrder, 0);
        this.status = status;
    }

    public long getId() {
        return this.id;
    }

    public String getExamTypeCode() {
        return this.examTypeCode;
    }

    public String getExamTypeName() {
        return this.examTypeName;
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

