/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.overallcomment;

import com.xinshi.admin.domain.overallcomment.OverallComment;
import java.util.List;
import java.util.Optional;

public interface OverallCommentRepository {
    public Optional<OverallComment> findById(long var1);

    public List<OverallComment> findByParams(Long var1, Long var2, Long var3, long var4, List<String> var6);

    public Optional<OverallComment> findByTermAndStudent(long var1, long var3);

    public OverallComment save(OverallComment var1);

    public void update(OverallComment var1);
}

