/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.gradesubject;

import com.xinshi.admin.domain.gradesubject.GradeSubject;
import java.util.List;

public interface GradeSubjectRepository {
    public List<GradeSubject> findByTermAndGrade(long var1, int var3);

    public List<GradeSubject> findActiveByTermAndGrade(long var1, int var3);

    public void saveBatch(long var1, int var3, List<GradeSubject> var4);
}

