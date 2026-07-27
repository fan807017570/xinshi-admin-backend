/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.subject;

import com.xinshi.admin.domain.shared.DomainException;

public class ScoreRange {
    private final double minScore;
    private final double maxScore;

    private ScoreRange(double minScore, double maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public static ScoreRange of(double minScore, double maxScore) {
        if (minScore < 0.0 || maxScore < 0.0) {
            throw new DomainException("课程分数范围不能小于 0");
        }
        if (minScore > maxScore) {
            throw new DomainException("课程最小分不能大于最大分");
        }
        return new ScoreRange(minScore, maxScore);
    }

    public boolean contains(double score) {
        return score >= this.minScore && score <= this.maxScore;
    }

    public void validateScore(double score) {
        if (!this.contains(score)) {
            throw new DomainException(String.format("成绩必须在 %.2f 到 %.2f 之间", this.minScore, this.maxScore));
        }
    }

    public double getMinScore() {
        return this.minScore;
    }

    public double getMaxScore() {
        return this.maxScore;
    }
}

