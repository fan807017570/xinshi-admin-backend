/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.classsubject;

import java.time.LocalDateTime;

public class ClassSubject {
    private final long id;
    private final long academicTermId;
    private final long classId;
    private String className;
    private final long subjectId;
    private String subjectName;
    private Long sourceGradeSubjectId;
    private Long teacherUserId;
    private String teacherName;
    private double minScore;
    private double maxScore;
    private int status;
    private final LocalDateTime createdAt;

    private ClassSubject(long id, long academicTermId, long classId, long subjectId, LocalDateTime createdAt) {
        this.id = id;
        this.academicTermId = academicTermId;
        this.classId = classId;
        this.subjectId = subjectId;
        this.createdAt = createdAt;
    }

    public static ClassSubject create(long academicTermId, long classId, long subjectId, Long teacherUserId, int status) {
        ClassSubject cs = new ClassSubject(0L, academicTermId, classId, subjectId, LocalDateTime.now());
        cs.teacherUserId = teacherUserId;
        cs.status = status;
        return cs;
    }

    public static ClassSubject rehydrate(long id, long academicTermId, long classId, String className, long subjectId, String subjectName, Long sourceGradeSubjectId, Long teacherUserId, String teacherName, double minScore, double maxScore, int status, LocalDateTime createdAt) {
        ClassSubject cs = new ClassSubject(id, academicTermId, classId, subjectId, createdAt);
        cs.className = className;
        cs.subjectName = subjectName;
        cs.sourceGradeSubjectId = sourceGradeSubjectId;
        cs.teacherUserId = teacherUserId;
        cs.teacherName = teacherName;
        cs.minScore = minScore;
        cs.maxScore = maxScore;
        cs.status = status;
        return cs;
    }

    public void assignTeacher(long teacherUserId) {
        this.teacherUserId = teacherUserId;
    }

    public long getId() {
        return this.id;
    }

    public long getAcademicTermId() {
        return this.academicTermId;
    }

    public long getClassId() {
        return this.classId;
    }

    public String getClassName() {
        return this.className;
    }

    public long getSubjectId() {
        return this.subjectId;
    }

    public String getSubjectName() {
        return this.subjectName;
    }

    public Long getSourceGradeSubjectId() {
        return this.sourceGradeSubjectId;
    }

    public Long getTeacherUserId() {
        return this.teacherUserId;
    }

    public String getTeacherName() {
        return this.teacherName;
    }

    public double getMinScore() {
        return this.minScore;
    }

    public double getMaxScore() {
        return this.maxScore;
    }

    public int getStatus() {
        return this.status;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
}

