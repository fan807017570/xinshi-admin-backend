/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.user;

import com.xinshi.admin.domain.user.User;
import com.xinshi.admin.domain.user.UserId;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    public User save(User var1);

    public Optional<User> findById(UserId var1);

    public Optional<User> findByUsername(String var1);

    public List<User> findAll();
}

