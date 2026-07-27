/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.honortype;

import com.xinshi.admin.domain.honortype.HonorType;
import java.util.List;
import java.util.Optional;

public interface HonorTypeRepository {
    public List<HonorType> findAll();

    public List<HonorType> findAllEnabled();

    public Optional<HonorType> findById(long var1);

    public Optional<HonorType> findByCode(String var1);

    public HonorType save(HonorType var1);

    public void update(HonorType var1);

    public void delete(long var1);
}

