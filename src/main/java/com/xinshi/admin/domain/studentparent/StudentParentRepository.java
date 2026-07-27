/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.studentparent;

import com.xinshi.admin.domain.studentparent.StudentParent;
import java.util.List;

public interface StudentParentRepository {
    public List<StudentParent> findByStudent(long var1);

    public void saveBatch(long var1, List<StudentParent> var3);

    public boolean isParentOf(long var1, long var3);
}

