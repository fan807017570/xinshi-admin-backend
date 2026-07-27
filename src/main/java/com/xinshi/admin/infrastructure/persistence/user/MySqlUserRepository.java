/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.user;

import com.xinshi.admin.domain.user.User;
import com.xinshi.admin.domain.user.UserId;
import com.xinshi.admin.domain.user.UserRepository;
import com.xinshi.admin.domain.user.UserStatus;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlUserRepository
implements UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public User save(User user) {
        if (user.getId() == null || user.getId().getValue() == null || user.getId().getValue().trim().isEmpty() || !this.isNumeric(user.getId().getValue()) || !this.existsById(Long.valueOf(user.getId().getValue()))) {
            Long id = this.insert(user);
            return this.findById(UserId.of(String.valueOf(id))).orElseThrow(() -> new IllegalStateException("Inserted user not found"));
        }
        this.upsert(user);
        return user;
    }

    @Override
    public Optional<User> findById(UserId id) {
        if (id == null || !this.isNumeric(id.getValue())) {
            return Optional.empty();
        }
        List users = this.jdbcTemplate.query("SELECT id, login_name, real_name, email, status, created_at FROM sys_user WHERE id = ? AND is_deleted = 0", new Object[]{Long.valueOf(id.getValue())}, (rs, rowNum) -> User.rehydrate(UserId.of(String.valueOf(rs.getLong("id"))), rs.getString("login_name"), rs.getString("real_name"), rs.getString("email"), rs.getInt("status") == 1 ? UserStatus.ENABLED : UserStatus.DISABLED, rs.getTimestamp("created_at").toLocalDateTime()));
        return users.stream().findFirst();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        List users = this.jdbcTemplate.query("SELECT id, login_name, real_name, email, status, created_at FROM sys_user WHERE LOWER(login_name) = LOWER(?) AND is_deleted = 0", new Object[]{username.trim()}, (rs, rowNum) -> User.rehydrate(UserId.of(String.valueOf(rs.getLong("id"))), rs.getString("login_name"), rs.getString("real_name"), rs.getString("email"), rs.getInt("status") == 1 ? UserStatus.ENABLED : UserStatus.DISABLED, rs.getTimestamp("created_at").toLocalDateTime()));
        return users.stream().findFirst();
    }

    @Override
    public List<User> findAll() {
        return this.jdbcTemplate.query("SELECT id, login_name, real_name, email, status, created_at FROM sys_user WHERE is_deleted = 0 ORDER BY id DESC", (rs, rowNum) -> User.rehydrate(UserId.of(String.valueOf(rs.getLong("id"))), rs.getString("login_name"), rs.getString("real_name"), rs.getString("email"), rs.getInt("status") == 1 ? UserStatus.ENABLED : UserStatus.DISABLED, rs.getTimestamp("created_at").toLocalDateTime()));
    }

    private Long insert(User user) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO sys_user (login_name, password_hash, real_name, mobile, email, status, is_deleted) VALUES (?, ?, ?, ?, ?, ?, 0)", 1);
            ps.setString(1, user.getUsername());
            ps.setString(2, "{bcrypt}$2a$10$change-me-default");
            ps.setString(3, user.getDisplayName());
            ps.setString(4, null);
            ps.setString(5, user.getEmail());
            ps.setInt(6, user.getStatus() == UserStatus.ENABLED ? 1 : 0);
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : Long.valueOf(key.longValue());
    }

    private void upsert(User user) {
        this.jdbcTemplate.update("UPDATE sys_user SET login_name = ?, real_name = ?, email = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0", new Object[]{user.getUsername(), user.getDisplayName(), user.getEmail(), user.getStatus() == UserStatus.ENABLED ? 1 : 0, Long.valueOf(user.getId().getValue())});
    }

    private boolean existsById(Long id) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user WHERE id = ? AND is_deleted = 0", Integer.class, new Object[]{id});
        return count != null && count > 0;
    }

    private boolean isNumeric(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); ++i) {
            if (Character.isDigit(value.charAt(i))) continue;
            return false;
        }
        return true;
    }
}

