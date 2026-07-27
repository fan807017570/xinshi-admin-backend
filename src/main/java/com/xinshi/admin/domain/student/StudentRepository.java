/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.student;

import com.xinshi.admin.domain.student.Student;
import java.util.List;
import java.util.Optional;

public interface StudentRepository {
    public Optional<Student> findById(long var1);

    public Optional<Student> findByStudentNo(String var1);

    public List<Student> findAll(Long var1, String var2, Integer var3, long var4, List<String> var6, int var7, long var8);

    public long count(Long var1, String var2, Integer var3, long var4, List<String> var6);

    public boolean existsInClass(long var1, long var3);

    public Student save(Student var1);

    public void update(Student var1);

    public void deactivate(long var1);
}

