/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.transcript;

import com.xinshi.admin.domain.transcript.Transcript;
import java.util.List;
import java.util.Optional;

public interface TranscriptRepository {
    public Optional<Transcript> findById(long var1);

    public Optional<Transcript> findByTermAndStudent(long var1, long var3);

    public List<Transcript> findAll(Long var1, Long var2, Long var3, long var4, List<String> var6, int var7, long var8);

    public long count(Long var1, Long var2, Long var3, long var4, List<String> var6);

    public Transcript save(Transcript var1);

    public void update(Transcript var1);
}

