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
    public void ensureCommentColumns() {
        this.ensureColumn("school_student_course_result", "strengths", "ALTER TABLE school_student_course_result ADD COLUMN strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Course strengths' AFTER performance_comment");
        this.ensureColumn("school_student_course_result", "improvement_points", "ALTER TABLE school_student_course_result ADD COLUMN improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Course improvement points' AFTER strengths");
        this.ensureColumn("school_student_overall_comment", "strengths", "ALTER TABLE school_student_overall_comment ADD COLUMN strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Overall strengths' AFTER overall_comment");
        this.ensureColumn("school_student_overall_comment", "improvement_points", "ALTER TABLE school_student_overall_comment ADD COLUMN improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Overall improvement points' AFTER strengths");
        this.ensureColumn("school_subject", "min_score", "ALTER TABLE school_subject ADD COLUMN min_score DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT 'Minimum valid score' AFTER subject_name");
        this.ensureColumn("school_subject", "max_score", "ALTER TABLE school_subject ADD COLUMN max_score DECIMAL(8,2) NOT NULL DEFAULT 100 COMMENT 'Maximum valid score' AFTER min_score");
        this.ensureColumn("school_student_achievement", "honor_type_id", "ALTER TABLE school_student_achievement ADD COLUMN honor_type_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Honor type ID' AFTER student_id");
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
}

