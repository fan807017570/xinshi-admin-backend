/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardApplicationService {
    private final JdbcTemplate jdbcTemplate;

    public DashboardApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> summary() {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("userCount", this.count("sys_user", "is_deleted = 0"));
        summary.put("roleCount", this.count("sys_role", "1 = 1"));
        summary.put("termCount", this.count("school_academic_term", "1 = 1"));
        summary.put("classCount", this.count("school_class", "is_deleted = 0"));
        summary.put("subjectCount", this.count("school_subject", "1 = 1"));
        summary.put("studentCount", this.count("school_student", "is_deleted = 0"));
        summary.put("resultCount", this.count("school_student_course_result", "1 = 1"));
        summary.put("commentCount", this.count("school_student_overall_comment", "1 = 1"));
        summary.put("transcriptCount", this.count("school_transcript", "1 = 1"));
        return summary;
    }

    private long count(String table, String whereClause) {
        Long value = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + table + " WHERE " + whereClause, Long.class);
        return value == null ? 0L : value;
    }
}

