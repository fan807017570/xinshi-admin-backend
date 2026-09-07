package com.xinshi.admin.interfaces.dto.h5;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Positive;

/**
 * Server-candidate query context selection payload.
 *
 * @author Codex
 * @date 2026-08-31
 */
public class H5QueryContextResolveRequest {
    @Positive
    private Long studentId;
    @Positive
    private Long academicTermId;
    @Positive
    private Long examTypeId;

    @AssertTrue(message = "必须提交一个当前待选择项")
    public boolean isSelectionPresent() {
        return studentId != null || academicTermId != null || examTypeId != null;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getAcademicTermId() {
        return academicTermId;
    }

    public void setAcademicTermId(Long academicTermId) {
        this.academicTermId = academicTermId;
    }

    public Long getExamTypeId() {
        return examTypeId;
    }

    public void setExamTypeId(Long examTypeId) {
        this.examTypeId = examTypeId;
    }

    @Override
    public String toString() {
        return "H5QueryContextResolveRequest{studentId=" + studentId
                + ", academicTermId=" + academicTermId
                + ", examTypeId=" + examTypeId + "}";
    }
}
