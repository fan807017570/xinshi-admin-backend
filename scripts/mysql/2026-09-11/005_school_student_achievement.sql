-- MySQL 8.x migration: add school_student_achievement table for tracking student honors/awards
-- This script is idempotent: the table is only created if it does not already exist.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS school_student_achievement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    academic_term_id BIGINT UNSIGNED NOT NULL COMMENT 'Academic term ID',
    student_id BIGINT UNSIGNED NOT NULL COMMENT 'Student ID',
    achievement_text VARCHAR(500) NOT NULL COMMENT 'Achievement description, e.g. 荣获2025-2026学年下学期杰出贡献称号',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (id),
    KEY idx_achievement_term_student (academic_term_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Student honors and achievements';
