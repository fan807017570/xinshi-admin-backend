package com.xinshi.admin.application.h5;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persists immutable parent-content lifecycle events without sensitive content.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Repository
public class ParentContentAuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public ParentContentAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Appends an immutable lifecycle event in the caller's transaction.
     *
     * @param contentType content category
     * @param contentId business record identifier
     * @param action lifecycle action
     * @param operatorUserId authenticated operator identifier
     */
    public void append(String contentType, long contentId, String action, long operatorUserId) {
        int updated = jdbcTemplate.update(
                "INSERT INTO school_parent_content_audit "
                        + "(content_type,content_id,action,operator_user_id,created_at) "
                        + "VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                contentType, contentId, action, operatorUserId);
        if (updated != 1) {
            throw new IllegalStateException("Failed to append parent content audit event");
        }
    }
}
