/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.courseresult;

import com.xinshi.admin.domain.courseresult.ResultStatus;
import java.time.LocalDateTime;

public class CourseResult {
    private final long id;
    private final long academicTermId;
    private String termName;
    private final long classSubjectId;
    private long classId;
    private String className;
    private final long studentId;
    private String studentName;
    private String studentNo;
    private long subjectId;
    private String subjectName;
    private double minScore;
    private double maxScore;
    private double score;
    private String performanceComment;
    private String strengths;
    private String improvementPoints;
    private long teacherUserId;
    private String teacherName;
    private long evaluatorUserId;
    private String evaluatorName;
    private LocalDateTime evaluatedAt;
    private ResultStatus status;
    private final LocalDateTime createdAt;

    private CourseResult(long id, long academicTermId, long classSubjectId, long studentId, LocalDateTime createdAt) {
        this.id = id;
        this.academicTermId = academicTermId;
        this.classSubjectId = classSubjectId;
        this.studentId = studentId;
        this.createdAt = createdAt;
    }

    public static CourseResult record(long academicTermId, long classSubjectId, long studentId, double score, String performanceComment, String strengths, String improvementPoints, long evaluatorUserId, int status) {
        CourseResult result = new CourseResult(0L, academicTermId, classSubjectId, studentId, LocalDateTime.now());
        result.score = score;
        result.performanceComment = performanceComment;
        result.strengths = strengths;
        result.improvementPoints = improvementPoints;
        result.evaluatorUserId = evaluatorUserId;
        result.evaluatedAt = LocalDateTime.now();
        result.status = ResultStatus.fromCode(status);
        return result;
    }

    public static CourseResult rehydrate(long id, long academicTermId, String termName, long classSubjectId, long classId, String className, long studentId, String studentName, String studentNo, long subjectId, String subjectName, double minScore, double maxScore, double score, String performanceComment, String strengths, String improvementPoints, long teacherUserId, String teacherName, long evaluatorUserId, String evaluatorName, LocalDateTime evaluatedAt, int statusCode, LocalDateTime createdAt) {
        CourseResult result = new CourseResult(id, academicTermId, classSubjectId, studentId, createdAt);
        result.termName = termName;
        result.classId = classId;
        result.className = className;
        result.studentName = studentName;
        result.studentNo = studentNo;
        result.subjectId = subjectId;
        result.subjectName = subjectName;
        result.minScore = minScore;
        result.maxScore = maxScore;
        result.score = score;
        result.performanceComment = performanceComment;
        result.strengths = strengths;
        result.improvementPoints = improvementPoints;
        result.teacherUserId = teacherUserId;
        result.teacherName = teacherName;
        result.evaluatorUserId = evaluatorUserId;
        result.evaluatorName = evaluatorName;
        result.evaluatedAt = evaluatedAt;
        result.status = ResultStatus.fromCode(statusCode);
        return result;
    }

    public void publish() {
        this.status = ResultStatus.PUBLISHED;
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

    public long getClassSubjectId() {
        return this.classSubjectId;
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

    public String getStudentNo() {
        return this.studentNo;
    }

    public long getSubjectId() {
        return this.subjectId;
    }

    public String getSubjectName() {
        return this.subjectName;
    }

    public double getMinScore() {
        return this.minScore;
    }

    public double getMaxScore() {
        return this.maxScore;
    }

    public double getScore() {
        return this.score;
    }

    public String getPerformanceComment() {
        return this.performanceComment;
    }

    public String getStrengths() {
        return this.strengths;
    }

    public String getImprovementPoints() {
        return this.improvementPoints;
    }

    public long getTeacherUserId() {
        return this.teacherUserId;
    }

    public String getTeacherName() {
        return this.teacherName;
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

