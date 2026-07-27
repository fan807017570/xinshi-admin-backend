/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.courseresult;

import com.xinshi.admin.domain.courseresult.CourseResult;
import java.util.List;
import java.util.Optional;

public interface CourseResultRepository {
    public Optional<CourseResult> findById(long var1);

    public List<CourseResult> findByParams(Long var1, Long var2, Long var3, Long var4, long var5, List<String> var7);

    public List<CourseResult> findTeacherScoreEntries(Long var1, Long var2, Long var3, String var4, String var5, long var6, List<String> var8, int var9, long var10);

    public long countTeacherScoreEntries(Long var1, Long var2, Long var3, String var4, String var5, long var6, List<String> var8);

    public Optional<CourseResult> findByTermClassSubjectStudent(long var1, long var3, long var5);

    public CourseResult save(CourseResult var1);

    public void update(CourseResult var1);

    public void publish(long var1);

    public int countByClassSubjectId(long var1);
}

