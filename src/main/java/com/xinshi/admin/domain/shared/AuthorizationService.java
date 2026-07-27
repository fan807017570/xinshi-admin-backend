/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.shared;

public interface AuthorizationService {
    public void ensureSuperAdmin();

    public void ensureHeadTeacherOrAdmin();

    public void ensureTeacherCanWriteResults();

    public void ensureCanManageStudents();

    public void ensureCanGenerateTranscript();

    public void ensureReadableAcademicConfig();

    public void ensureCanAccessClass(long var1);

    public void ensureCanManageClass(long var1);

    public void ensureCanAccessStudent(long var1);

    public void ensureCanManageStudent(long var1);

    public void ensureCanAccessClassSubject(long var1);

    public void ensureCanManageClassSubject(long var1);

    public void ensureCanAccessResult(long var1);

    public void ensureCanAccessComment(long var1);

    public void ensureCanAccessTranscript(long var1);

    public void ensureStudentBelongsToClass(long var1, long var3);

    public void ensureMutableUser(long var1);

    public void ensureReadableResultScope(Long var1, Long var2, Long var3, Long var4);

    public void ensureReadableCommentScope(Long var1, Long var2, Long var3);

    public void ensureReadableTranscriptScope(Long var1, Long var2, Long var3);
}

