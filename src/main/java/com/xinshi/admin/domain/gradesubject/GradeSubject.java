/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.gradesubject;

import java.time.LocalDateTime;

public class GradeSubject {
    private final long id;
    private final long academicTermId;
    private final int gradeLevel;
    private final long subjectId;
    private final String subjectName;
    private int isRequired;
    private int sortOrder;
    private int status;
    private final LocalDateTime createdAt;

    private GradeSubject(long id, long academicTermId, int gradeLevel, long subjectId, String subjectName, int isRequired, int sortOrder, int status, LocalDateTime createdAt) {
        this.id = id;
        this.academicTermId = academicTermId;
        this.gradeLevel = gradeLevel;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.isRequired = isRequired;
        this.sortOrder = sortOrder;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static GradeSubject create(long academicTermId, int gradeLevel, long subjectId, int isRequired, int sortOrder, int status) {
        return new GradeSubject(0L, academicTermId, gradeLevel, subjectId, null, isRequired, sortOrder, status, LocalDateTime.now());
    }

    public static GradeSubject rehydrate(long id, long academicTermId, int gradeLevel, long subjectId, String subjectName, int isRequired, int sortOrder, int status, LocalDateTime createdAt) {
        return new GradeSubject(id, academicTermId, gradeLevel, subjectId, subjectName, isRequired, sortOrder, status, createdAt);
    }

    public long getId() {
        return this.id;
    }

    public long getAcademicTermId() {
        return this.academicTermId;
    }

    public int getGradeLevel() {
        return this.gradeLevel;
    }

    public long getSubjectId() {
        return this.subjectId;
    }

    public String getSubjectName() {
        return this.subjectName;
    }

    public int getIsRequired() {
        return this.isRequired;
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

    public boolean isActive() {
        return this.status == 1;
    }
}

