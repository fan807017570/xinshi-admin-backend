/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.examtype;

import com.xinshi.admin.domain.examtype.ExamType;
import java.util.List;
import java.util.Optional;

public interface ExamTypeRepository {
    public List<ExamType> findAll();

    public Optional<ExamType> findById(long var1);

    public Optional<ExamType> findByCode(String var1);

    public ExamType save(ExamType var1);

    public void update(ExamType var1);

    public void delete(long var1);
}

