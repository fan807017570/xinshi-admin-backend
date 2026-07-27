/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.subject;

import com.xinshi.admin.domain.shared.DomainException;
import com.xinshi.admin.domain.subject.ScoreRange;
import java.time.LocalDateTime;

public class Subject {
    private final long id;
    private String subjectCode;
    private String subjectName;
    private ScoreRange scoreRange;
    private int status;
    private final LocalDateTime createdAt;

    private Subject(long id, String subjectCode, String subjectName, ScoreRange scoreRange, int status, LocalDateTime createdAt) {
        this.id = id;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.scoreRange = scoreRange;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Subject create(String subjectCode, String subjectName, double minScore, double maxScore, int status) {
        if (Subject.isBlank(subjectCode)) {
            throw new DomainException("课程编码不能为空");
        }
        if (Subject.isBlank(subjectName)) {
            throw new DomainException("课程名称不能为空");
        }
        ScoreRange range = ScoreRange.of(minScore, maxScore);
        return new Subject(0L, subjectCode.trim(), subjectName.trim(), range, status, LocalDateTime.now());
    }

    public static Subject rehydrate(long id, String subjectCode, String subjectName, double minScore, double maxScore, int status, LocalDateTime createdAt) {
        return new Subject(id, subjectCode, subjectName, ScoreRange.of(minScore, maxScore), status, createdAt);
    }

    public void update(String subjectCode, String subjectName, Double minScore, Double maxScore, Integer status) {
        if (subjectCode != null) {
            this.subjectCode = subjectCode.trim();
        }
        if (subjectName != null) {
            this.subjectName = subjectName.trim();
        }
        if (minScore != null || maxScore != null) {
            double newMin = minScore != null ? minScore.doubleValue() : this.scoreRange.getMinScore();
            double newMax = maxScore != null ? maxScore.doubleValue() : this.scoreRange.getMaxScore();
            this.scoreRange = ScoreRange.of(newMin, newMax);
        }
        if (status != null) {
            this.status = status;
        }
    }

    public void validateScore(double score) {
        this.scoreRange.validateScore(score);
    }

    public void deactivate() {
        this.status = 0;
    }

    public long getId() {
        return this.id;
    }

    public String getSubjectCode() {
        return this.subjectCode;
    }

    public String getSubjectName() {
        return this.subjectName;
    }

    public double getMinScore() {
        return this.scoreRange.getMinScore();
    }

    public double getMaxScore() {
        return this.scoreRange.getMaxScore();
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

