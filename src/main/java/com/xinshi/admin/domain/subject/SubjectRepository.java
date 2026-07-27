/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.subject;

import com.xinshi.admin.domain.subject.Subject;
import java.util.List;
import java.util.Optional;

public interface SubjectRepository {
    public Optional<Subject> findById(long var1);

    public Optional<Subject> findByCode(String var1);

    public List<Subject> findAll();

    public List<Subject> findAllAccessibleByTeacher(long var1, boolean var3);

    public Subject save(Subject var1);

    public void update(Subject var1);

    public void deactivate(long var1);

    public long count();
}

