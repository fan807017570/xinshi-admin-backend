/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Component
 */
package com.xinshi.admin.infrastructure.migration;

import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ColumnMigrationRunner {
    private final JdbcTemplate jdbcTemplate;

    public ColumnMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureRequiredColumnsAndIndexes() {
        this.ensureColumn("school_student_course_result", "strengths", "ALTER TABLE school_student_course_result ADD COLUMN strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Course strengths' AFTER performance_comment");
        this.ensureColumn("school_student_course_result", "improvement_points", "ALTER TABLE school_student_course_result ADD COLUMN improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Course improvement points' AFTER strengths");
        this.ensureColumn("school_student_overall_comment", "strengths", "ALTER TABLE school_student_overall_comment ADD COLUMN strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Overall strengths' AFTER overall_comment");
        this.ensureColumn("school_student_overall_comment", "improvement_points", "ALTER TABLE school_student_overall_comment ADD COLUMN improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Overall improvement points' AFTER strengths");
        this.ensureColumn("school_student_overall_comment", "teacher_name_snapshot", "ALTER TABLE school_student_overall_comment ADD COLUMN teacher_name_snapshot VARCHAR(128) DEFAULT NULL COMMENT 'Teacher name snapshot shown to parents' AFTER status");
        this.ensureColumn("school_student_overall_comment", "published_at", "ALTER TABLE school_student_overall_comment ADD COLUMN published_at DATETIME DEFAULT NULL COMMENT 'First published time for parents' AFTER teacher_name_snapshot");
        this.ensureColumn("school_subject", "min_score", "ALTER TABLE school_subject ADD COLUMN min_score DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT 'Minimum valid score' AFTER subject_name");
        this.ensureColumn("school_subject", "max_score", "ALTER TABLE school_subject ADD COLUMN max_score DECIMAL(8,2) NOT NULL DEFAULT 100 COMMENT 'Maximum valid score' AFTER min_score");
        this.ensureColumn("school_student_achievement", "honor_type_id", "ALTER TABLE school_student_achievement ADD COLUMN honor_type_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Honor type ID' AFTER student_id");
        this.ensureColumn("school_student_achievement", "status", "ALTER TABLE school_student_achievement ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT 'Achievement status: 1-draft, 2-published' AFTER sort_order");
        this.ensureColumn("school_student_achievement", "awarded_on", "ALTER TABLE school_student_achievement ADD COLUMN awarded_on DATE DEFAULT NULL COMMENT 'Award date' AFTER status");
        this.ensureColumn("school_student_achievement", "published_at", "ALTER TABLE school_student_achievement ADD COLUMN published_at DATETIME DEFAULT NULL COMMENT 'First published time for parents' AFTER awarded_on");
        this.ensureIndex("school_student_overall_comment", "idx_overall_comment_h5", "CREATE INDEX idx_overall_comment_h5 ON school_student_overall_comment (student_id, academic_term_id, status, published_at)");
        this.ensureIndex("school_student_achievement", "idx_achievement_h5", "CREATE INDEX idx_achievement_h5 ON school_student_achievement (student_id, academic_term_id, status, awarded_on, id)");
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        Integer tableCount = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, new Object[]{tableName});
        if (tableCount == null || tableCount == 0) {
            return;
        }
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", Integer.class, new Object[]{tableName, columnName});
        if (count == null || count == 0) {
            this.jdbcTemplate.execute(alterSql);
        }
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        Integer tableCount = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, new Object[]{tableName});
        if (tableCount == null || tableCount == 0) {
            return;
        }
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?", Integer.class, new Object[]{tableName, indexName});
        if (count == null || count == 0) {
            this.jdbcTemplate.execute(createSql);
        }
    }
}
