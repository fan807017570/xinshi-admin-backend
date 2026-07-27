/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.academicterm;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AcademicTerm {
    private final long id;
    private String termCode;
    private String academicYear;
    private String termName;
    private LocalDate startDate;
    private LocalDate endDate;
    private int status;
    private final LocalDateTime createdAt;

    private AcademicTerm(long id, String termCode, String academicYear, String termName, LocalDate startDate, LocalDate endDate, int status, LocalDateTime createdAt) {
        this.id = id;
        this.termCode = termCode;
        this.academicYear = academicYear;
        this.termName = termName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static AcademicTerm create(String termCode, String academicYear, String termName, LocalDate startDate, LocalDate endDate, int status) {
        if (AcademicTerm.isBlank(termCode)) {
            throw new DomainException("学期编码不能为空");
        }
        if (AcademicTerm.isBlank(academicYear)) {
            throw new DomainException("学年不能为空");
        }
        if (AcademicTerm.isBlank(termName)) {
            throw new DomainException("学期名称不能为空");
        }
        return new AcademicTerm(0L, termCode.trim(), academicYear.trim(), termName.trim(), startDate, endDate, status, LocalDateTime.now());
    }

    public static AcademicTerm rehydrate(long id, String termCode, String academicYear, String termName, LocalDate startDate, LocalDate endDate, int status, LocalDateTime createdAt) {
        return new AcademicTerm(id, termCode, academicYear, termName, startDate, endDate, status, createdAt);
    }

    public void update(String termName, LocalDate startDate, LocalDate endDate, Integer status) {
        if (termName != null) {
            this.termName = termName;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public long getId() {
        return this.id;
    }

    public String getTermCode() {
        return this.termCode;
    }

    public String getAcademicYear() {
        return this.academicYear;
    }

    public String getTermName() {
        return this.termName;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
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

