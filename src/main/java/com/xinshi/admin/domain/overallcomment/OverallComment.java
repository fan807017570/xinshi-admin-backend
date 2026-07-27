/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.overallcomment;

import com.xinshi.admin.domain.courseresult.ResultStatus;
import java.time.LocalDateTime;

public class OverallComment {
    private final long id;
    private final long academicTermId;
    private String termName;
    private long classId;
    private String className;
    private final long studentId;
    private String studentName;
    private String overallComment;
    private String strengths;
    private String improvementPoints;
    private long evaluatorUserId;
    private String evaluatorName;
    private LocalDateTime evaluatedAt;
    private ResultStatus status;
    private final LocalDateTime createdAt;

    private OverallComment(long id, long academicTermId, long classId, long studentId, LocalDateTime createdAt) {
        this.id = id;
        this.academicTermId = academicTermId;
        this.classId = classId;
        this.studentId = studentId;
        this.createdAt = createdAt;
    }

    public static OverallComment record(long academicTermId, long classId, long studentId, String overallComment, String strengths, String improvementPoints, long evaluatorUserId, int status) {
        OverallComment comment = new OverallComment(0L, academicTermId, classId, studentId, LocalDateTime.now());
        comment.overallComment = overallComment;
        comment.strengths = strengths;
        comment.improvementPoints = improvementPoints;
        comment.evaluatorUserId = evaluatorUserId;
        comment.evaluatedAt = LocalDateTime.now();
        comment.status = ResultStatus.fromCode(status);
        return comment;
    }

    public static OverallComment rehydrate(long id, long academicTermId, String termName, long classId, String className, long studentId, String studentName, String overallComment, String strengths, String improvementPoints, long evaluatorUserId, String evaluatorName, LocalDateTime evaluatedAt, int statusCode, LocalDateTime createdAt) {
        OverallComment comment = new OverallComment(id, academicTermId, classId, studentId, createdAt);
        comment.termName = termName;
        comment.className = className;
        comment.studentName = studentName;
        comment.overallComment = overallComment;
        comment.strengths = strengths;
        comment.improvementPoints = improvementPoints;
        comment.evaluatorUserId = evaluatorUserId;
        comment.evaluatorName = evaluatorName;
        comment.evaluatedAt = evaluatedAt;
        comment.status = ResultStatus.fromCode(statusCode);
        return comment;
    }

    public long getId() {
        return this.id;
    }

    public long getAcademicTermId() {
        return this.academicTermId;
    }

    public String getTermName() {
        return this.termName;
    }

    public long getClassId() {
        return this.classId;
    }

    public String getClassName() {
        return this.className;
    }

    public long getStudentId() {
        return this.studentId;
    }

    public String getStudentName() {
        return this.studentName;
    }

    public String getOverallComment() {
        return this.overallComment;
    }

    public String getStrengths() {
        return this.strengths;
    }

    public String getImprovementPoints() {
        return this.improvementPoints;
    }

    public long getEvaluatorUserId() {
        return this.evaluatorUserId;
    }

    public String getEvaluatorName() {
        return this.evaluatorName;
    }

    public LocalDateTime getEvaluatedAt() {
        return this.evaluatedAt;
    }

    public ResultStatus getStatus() {
        return this.status;
    }

    public int getStatusCode() {
        return this.status.getCode();
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
}

