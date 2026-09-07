-- Immutable audit trail for parent-visible comments and achievements.
-- The audit deliberately excludes content text and student identifiers.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS school_parent_content_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    content_type VARCHAR(32) NOT NULL,
    content_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator_user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_content_audit_content (content_type, content_id, created_at),
    KEY idx_parent_content_audit_operator (operator_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable lifecycle audit for parent-visible content';
