/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.classsubject;

import com.xinshi.admin.domain.classsubject.ClassSubject;
import java.util.List;
import java.util.Optional;

public interface ClassSubjectRepository {
    public Optional<ClassSubject> findById(long var1);

    public List<ClassSubject> findByTermAndClass(long var1, long var3);

    public Optional<ClassSubject> findByTermClassSubject(long var1, long var3, long var5);

    public int countByTermAndClass(long var1, long var3);

    public ClassSubject save(ClassSubject var1);

    public void updateTeacher(long var1, long var3);

    public void delete(long var1);

    public boolean isTeacherOfClass(long var1, long var3);
}

