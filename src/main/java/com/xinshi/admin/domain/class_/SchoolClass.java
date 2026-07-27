/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.class_;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDateTime;

public class SchoolClass {
    private final long id;
    private String classCode;
    private String className;
    private String gradeSession;
    private int gradeLevel;
    private long headTeacherUserId;
    private String headTeacherName;
    private int isKeyClass;
    private int status;
    private final LocalDateTime createdAt;

    private SchoolClass(long id, String classCode, String className, String gradeSession, int gradeLevel, long headTeacherUserId, String headTeacherName, int isKeyClass, int status, LocalDateTime createdAt) {
        this.id = id;
        this.classCode = classCode;
        this.className = className;
        this.gradeSession = gradeSession;
        this.gradeLevel = gradeLevel;
        this.headTeacherUserId = headTeacherUserId;
        this.headTeacherName = headTeacherName;
        this.isKeyClass = isKeyClass;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static SchoolClass create(String classCode, String className, String gradeSession, int gradeLevel, long headTeacherUserId, int isKeyClass, int status) {
        if (SchoolClass.isBlank(classCode)) {
            throw new DomainException("班级编码不能为空");
        }
        if (SchoolClass.isBlank(className)) {
            throw new DomainException("班级名称不能为空");
        }
        if (SchoolClass.isBlank(gradeSession)) {
            throw new DomainException("年级届别不能为空");
        }
        return new SchoolClass(0L, classCode.trim(), className.trim(), gradeSession.trim(), gradeLevel, headTeacherUserId, null, isKeyClass, status, LocalDateTime.now());
    }

    public static SchoolClass rehydrate(long id, String classCode, String className, String gradeSession, int gradeLevel, long headTeacherUserId, String headTeacherName, int isKeyClass, int status, LocalDateTime createdAt) {
        return new SchoolClass(id, classCode, className, gradeSession, gradeLevel, headTeacherUserId, headTeacherName, isKeyClass, status, createdAt);
    }

    public void update(String classCode, String className, String gradeSession, Integer gradeLevel, Long headTeacherUserId, Integer isKeyClass, Integer status) {
        if (classCode != null) {
            this.classCode = classCode.trim();
        }
        if (className != null) {
            this.className = className.trim();
        }
        if (gradeSession != null) {
            this.gradeSession = gradeSession.trim();
        }
        if (gradeLevel != null) {
            this.gradeLevel = gradeLevel;
        }
        if (headTeacherUserId != null) {
            this.headTeacherUserId = headTeacherUserId;
        }
        if (isKeyClass != null) {
            this.isKeyClass = isKeyClass;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public void deactivate() {
        this.status = 0;
    }

    public long getId() {
        return this.id;
    }

    public String getClassCode() {
        return this.classCode;
    }

    public String getClassName() {
        return this.className;
    }

    public String getGradeSession() {
        return this.gradeSession;
    }

    public int getGradeLevel() {
        return this.gradeLevel;
    }

    public long getHeadTeacherUserId() {
        return this.headTeacherUserId;
    }

    public String getHeadTeacherName() {
        return this.headTeacherName;
    }

    public int getIsKeyClass() {
        return this.isKeyClass;
    }

    public int getStatus() {
        return this.status;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

