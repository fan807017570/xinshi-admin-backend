/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.academicterm;

import com.xinshi.admin.domain.academicterm.AcademicTerm;
import java.util.List;
import java.util.Optional;

public interface AcademicTermRepository {
    public Optional<AcademicTerm> findById(long var1);

    public Optional<AcademicTerm> findByCode(String var1);

    public List<AcademicTerm> findAll();

    public AcademicTerm save(AcademicTerm var1);

    public void update(AcademicTerm var1);

    public long count();
}

