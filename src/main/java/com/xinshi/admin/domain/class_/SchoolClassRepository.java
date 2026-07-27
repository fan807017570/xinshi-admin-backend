/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.class_;

import com.xinshi.admin.domain.class_.SchoolClass;
import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository {
    public Optional<SchoolClass> findById(long var1);

    public Optional<SchoolClass> findByCode(String var1);

    public List<SchoolClass> findAll();

    public List<SchoolClass> findAll(String var1, String var2, long var3, List<String> var5, int var6, long var7);

    public long count(String var1, String var2, long var3, List<String> var5);

    public List<SchoolClass> findByHeadTeacher(long var1);

    public SchoolClass save(SchoolClass var1);

    public void update(SchoolClass var1);

    public void deactivate(long var1);

    public long count();

    public boolean existsById(long var1);
}

