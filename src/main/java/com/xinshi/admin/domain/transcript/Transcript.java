/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.transcript;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDateTime;

public class Transcript {
    private final long id;
    private String transcriptNo;
    private final long academicTermId;
    private String termName;
    private long classId;
    private String className;
    private final long studentId;
    private String studentName;
    private String pdfFileName;
    private String pdfFilePath;
    private long generatedBy;
    private String generatedByName;
    private LocalDateTime generatedAt;
    private int status;
    private final LocalDateTime createdAt;

    private Transcript(long id, long academicTermId, long classId, long studentId, LocalDateTime createdAt) {
        this.id = id;
        this.academicTermId = academicTermId;
        this.classId = classId;
        this.studentId = studentId;
        this.createdAt = createdAt;
    }

    public static Transcript create(String transcriptNo, long academicTermId, long classId, long studentId, String pdfFileName, String pdfFilePath, long generatedBy) {
        if (Transcript.isBlank(transcriptNo)) {
            throw new DomainException("成绩单编号不能为空");
        }
        Transcript transcript = new Transcript(0L, academicTermId, classId, studentId, LocalDateTime.now());
        transcript.transcriptNo = transcriptNo;
        transcript.pdfFileName = pdfFileName;
        transcript.pdfFilePath = pdfFilePath;
        transcript.generatedBy = generatedBy;
        transcript.generatedAt = LocalDateTime.now();
        transcript.status = 1;
        return transcript;
    }

    public static Transcript rehydrate(long id, String transcriptNo, long academicTermId, String termName, long classId, String className, long studentId, String studentName, String pdfFileName, String pdfFilePath, long generatedBy, String generatedByName, LocalDateTime generatedAt, int status, LocalDateTime createdAt) {
        Transcript transcript = new Transcript(id, academicTermId, classId, studentId, createdAt);
        transcript.transcriptNo = transcriptNo;
        transcript.termName = termName;
        transcript.className = className;
        transcript.studentName = studentName;
        transcript.pdfFileName = pdfFileName;
        transcript.pdfFilePath = pdfFilePath;
        transcript.generatedBy = generatedBy;
        transcript.generatedByName = generatedByName;
        transcript.generatedAt = generatedAt;
        transcript.status = status;
        return transcript;
    }

    public void regenerate(String pdfFileName, String pdfFilePath, long generatedBy) {
        this.pdfFileName = pdfFileName;
        this.pdfFilePath = pdfFilePath;
        this.generatedBy = generatedBy;
        this.generatedAt = LocalDateTime.now();
        this.status = 1;
    }

    public long getId() {
        return this.id;
    }

    public String getTranscriptNo() {
        return this.transcriptNo;
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

    public String getPdfFileName() {
        return this.pdfFileName;
    }

    public String getPdfFilePath() {
        return this.pdfFilePath;
    }

    public long getGeneratedBy() {
        return this.generatedBy;
    }

    public String getGeneratedByName() {
        return this.generatedByName;
    }

    public LocalDateTime getGeneratedAt() {
        return this.generatedAt;
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

