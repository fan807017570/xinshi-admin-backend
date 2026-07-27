/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.achievement;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDateTime;

public class StudentAchievement {
    private final long id;
    private final long academicTermId;
    private final long studentId;
    private Long honorTypeId;
    private String achievementText;
    private int sortOrder;
    private final LocalDateTime createdAt;

    private StudentAchievement(long id, long academicTermId, long studentId, LocalDateTime createdAt) {
        this.id = id;
        this.academicTermId = academicTermId;
        this.studentId = studentId;
        this.createdAt = createdAt;
    }

    public static StudentAchievement create(long academicTermId, long studentId, String achievementText, int sortOrder, Long honorTypeId) {
        if (StudentAchievement.isBlank(achievementText)) {
            throw new DomainException("成就描述不能为空");
        }
        if (achievementText.length() > 500) {
            throw new DomainException("成就描述不能超过500字");
        }
        StudentAchievement achievement = new StudentAchievement(0L, academicTermId, studentId, LocalDateTime.now());
        achievement.achievementText = achievementText.trim();
        achievement.sortOrder = Math.max(sortOrder, 0);
        achievement.honorTypeId = honorTypeId;
        return achievement;
    }

    public static StudentAchievement rehydrate(long id, long academicTermId, long studentId, String achievementText, int sortOrder, Long honorTypeId, LocalDateTime createdAt) {
        StudentAchievement achievement = new StudentAchievement(id, academicTermId, studentId, createdAt);
        achievement.achievementText = achievementText;
        achievement.sortOrder = sortOrder;
        achievement.honorTypeId = honorTypeId;
        return achievement;
    }

    public long getId() {
        return this.id;
    }

    public long getAcademicTermId() {
        return this.academicTermId;
    }

    public long getStudentId() {
        return this.studentId;
    }

    public Long getHonorTypeId() {
        return this.honorTypeId;
    }

    public String getAchievementText() {
        return this.achievementText;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

