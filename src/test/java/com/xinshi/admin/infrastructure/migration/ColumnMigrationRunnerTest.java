package com.xinshi.admin.infrastructure.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ColumnMigrationRunnerTest {

    @Test
    void addsAchievementPublishColumnsWhenExistingTableIsOutdated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), anyString())).thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    return sql.contains("information_schema.TABLES") ? 1 : 0;
                });
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), anyString(), anyString())).thenReturn(0);

        new ColumnMigrationRunner(jdbcTemplate).ensureRequiredColumnsAndIndexes();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
        List<String> executedSql = sqlCaptor.getAllValues();
        assertTrue(executedSql.stream().anyMatch(sql ->
                        sql.contains("school_student_achievement ADD COLUMN status")),
                "An outdated achievement table must be upgraded with the status column");
        assertTrue(executedSql.stream().anyMatch(sql ->
                        sql.contains("school_student_achievement ADD COLUMN published_at")),
                "An outdated achievement table must be upgraded with the published_at column");
        assertTrue(executedSql.stream().anyMatch(sql ->
                        sql.contains("school_student_achievement ADD COLUMN awarded_on")),
                "An outdated achievement table must be upgraded with the awarded_on column");
        assertTrue(executedSql.stream().anyMatch(sql ->
                        sql.contains("school_student_overall_comment ADD COLUMN teacher_name_snapshot")),
                "An outdated comment table must be upgraded with the teacher snapshot column");
        assertTrue(executedSql.stream().anyMatch(sql ->
                        sql.contains("school_student_overall_comment ADD COLUMN published_at")),
                "An outdated comment table must be upgraded with the published_at column");
        assertTrue(executedSql.stream().anyMatch(sql ->
                        sql.contains("CREATE INDEX idx_achievement_h5")),
                "The achievement H5 lookup index must be created");
        assertTrue(executedSql.stream().anyMatch(sql ->
                        sql.contains("CREATE INDEX idx_overall_comment_h5")),
                "The overall comment H5 lookup index must be created");
    }

    @Test
    void skipsSchemaChangesWhenColumnsAndIndexesAlreadyExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), anyString())).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), anyString(), anyString())).thenReturn(1);

        new ColumnMigrationRunner(jdbcTemplate).ensureRequiredColumnsAndIndexes();

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
