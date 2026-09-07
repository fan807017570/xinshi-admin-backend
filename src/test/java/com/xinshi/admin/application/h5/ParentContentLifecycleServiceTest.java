package com.xinshi.admin.application.h5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xinshi.admin.application.school.AccessControlService;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ParentContentLifecycleServiceTest {
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private AccessControlService accessControlService;
    private ParentContentAuditRepository auditRepository;
    private ParentContentLifecycleService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionTemplate = mock(TransactionTemplate.class);
        accessControlService = mock(AccessControlService.class);
        auditRepository = mock(ParentContentAuditRepository.class);
        service = new ParentContentLifecycleService(
                jdbcTemplate, transactionTemplate, accessControlService, auditRepository);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(accessControlService.currentUserId()).thenReturn(900L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(1);
    }

    @Test
    void publishCommentUsesEvaluatorTeacherInsteadOfOperatorSnapshot() {
        Map<String, Object> comment = commentRow(11L, 1, null, null);
        comment.put("overallComment", "  学习认真  ");
        comment.put("strengths", "  ");
        comment.put("improvementPoints", "继续努力 ");
        stubCommentQueries(comment, row("realName", "王五"));
        when(jdbcTemplate.update(
                contains("teacher_name_snapshot=?"),
                any(), any(), any(), any(), anyLong())).thenReturn(1);

        Map<String, Object> result = service.publishComment(11L);

        assertEquals(2, result.get("status"));
        verify(jdbcTemplate).update(
                contains("teacher_name_snapshot=?"),
                eq("学习认真"),
                isNull(),
                eq("继续努力"),
                eq("王五老师"),
                eq(11L));
        verify(auditRepository).append(
                ParentContentLifecycleService.CONTENT_COMMENT,
                11L,
                ParentContentLifecycleService.ACTION_PUBLISH,
                900L);
    }

    @Test
    void publishCommentRejectsAllBlankContent() {
        Map<String, Object> comment = commentRow(12L, 1, null, null);
        comment.put("overallComment", " ");
        comment.put("strengths", "\t");
        comment.put("improvementPoints", null);
        stubCommentQueries(comment, row("realName", "王五"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publishComment(12L));

        assertEquals("评语内容不能全部为空", error.getMessage());
        verify(auditRepository, never()).append(anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    void editPublishedCommentAutomaticallyReturnsItToDraft() {
        when(jdbcTemplate.queryForList(contains("WHERE academic_term_id=? AND student_id=? FOR UPDATE"),
                anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(row(
                        "id", 21L,
                        "status", 2,
                        "publishedAt", Timestamp.valueOf("2026-08-31 10:00:00"),
                        "teacherNameSnapshot", "旧教师老师")));
        when(jdbcTemplate.queryForList(contains("FROM school_student_overall_comment WHERE id=?"),
                anyLong()))
                .thenReturn(Collections.singletonList(row("id", 21L, "status", 1)));
        when(jdbcTemplate.update(
                contains("status=1,published_at=NULL,teacher_name_snapshot=NULL"),
                anyLong(), any(), any(), any(), anyLong(), any(Timestamp.class), anyLong()))
                .thenReturn(1);

        service.saveCommentDraft(
                31L, 41L, 51L, " 新评语 ", null, " 改进 ");

        verify(jdbcTemplate).update(
                contains("status=1,published_at=NULL,teacher_name_snapshot=NULL"),
                eq(41L),
                eq("新评语"),
                isNull(),
                eq("改进"),
                eq(900L),
                any(Timestamp.class),
                eq(21L));
        verify(auditRepository).append(
                ParentContentLifecycleService.CONTENT_COMMENT,
                21L,
                ParentContentLifecycleService.ACTION_EDIT,
                900L);
    }

    @Test
    void importWithdrawsButDoesNotDeletePublishedAchievement() {
        when(jdbcTemplate.queryForList(contains("JOIN school_class c"), anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(row("classId", 41L)));
        when(jdbcTemplate.queryForList(contains("FROM school_student_achievement WHERE academic_term_id=?"),
                anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(row(
                        "id", 71L,
                        "academicTermId", 31L,
                        "studentId", 51L,
                        "sortOrder", 0,
                        "status", 2,
                        "publishedAt", Timestamp.valueOf("2026-08-31 10:00:00"))));
        when(jdbcTemplate.update(
                contains("UPDATE school_student_achievement SET status=1,published_at=NULL"),
                anyLong())).thenReturn(1);

        service.reconcileImportedAchievements(31L, 51L, Collections.emptyList());

        verify(jdbcTemplate).update(
                contains("UPDATE school_student_achievement SET status=1,published_at=NULL"),
                eq(71L));
        verify(jdbcTemplate, never()).update(
                contains("DELETE FROM school_student_achievement"),
                anyLong());
        verify(auditRepository).append(
                ParentContentLifecycleService.CONTENT_ACHIEVEMENT,
                71L,
                ParentContentLifecycleService.ACTION_IMPORT,
                900L);
    }

    @Test
    void repeatedPublishIsIdempotent() {
        Timestamp publishedAt = Timestamp.valueOf("2026-08-31 10:00:00");
        Map<String, Object> comment = commentRow(81L, 2, publishedAt, "王五老师");
        stubCommentQueries(comment, row("realName", "王五"));

        service.publishComment(81L);
        service.publishComment(81L);

        verify(transactionTemplate, times(2)).execute(any());
        verify(jdbcTemplate, never()).update(
                contains("UPDATE school_student_overall_comment"),
                any(Object[].class));
        verify(auditRepository, never()).append(anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    void auditFailurePropagatesFromTransactionCallback() {
        Map<String, Object> comment = commentRow(91L, 1, null, null);
        stubCommentQueries(comment, row("realName", "王五"));
        when(jdbcTemplate.update(
                contains("teacher_name_snapshot=?"),
                any(), any(), any(), any(), anyLong())).thenReturn(1);
        doThrow(new IllegalStateException("audit failed"))
                .when(auditRepository)
                .append(
                        ParentContentLifecycleService.CONTENT_COMMENT,
                        91L,
                        ParentContentLifecycleService.ACTION_PUBLISH,
                        900L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.publishComment(91L));

        assertEquals("audit failed", error.getMessage());
        verify(transactionTemplate).execute(any());
    }

    @Test
    void commentAndAchievementImportUseOneTransaction() {
        when(jdbcTemplate.queryForList(
                contains("WHERE academic_term_id=? AND student_id=? FOR UPDATE"),
                anyLong(), anyLong())).thenReturn(Collections.singletonList(row("id", 101L)));
        when(jdbcTemplate.queryForList(contains("FROM school_student_overall_comment WHERE id=?"),
                anyLong())).thenReturn(Collections.singletonList(row("id", 101L, "status", 1)));
        when(jdbcTemplate.queryForList(contains("JOIN school_class c"), anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(row("classId", 41L)));
        when(jdbcTemplate.queryForList(
                contains("FROM school_student_achievement WHERE academic_term_id=?"),
                anyLong(), anyLong())).thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(
                contains("status=1,published_at=NULL,teacher_name_snapshot=NULL"),
                anyLong(), any(), any(), any(), anyLong(), any(Timestamp.class), anyLong()))
                .thenReturn(1);

        service.importParentContent(
                31L, 41L, 51L, true, " 新评语 ", null, null, Collections.emptyList());

        verify(transactionTemplate, times(1)).execute(any());
        verify(auditRepository).append(
                ParentContentLifecycleService.CONTENT_COMMENT,
                101L,
                ParentContentLifecycleService.ACTION_IMPORT,
                900L);
    }

    @Test
    void secondStudentFailureRollsBackCompleteImportBatch() {
        JdbcTemplate batchJdbcTemplate = mock(JdbcTemplate.class);
        AccessControlService batchAccessControl = mock(AccessControlService.class);
        ParentContentAuditRepository batchAuditRepository = mock(ParentContentAuditRepository.class);
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        ParentContentLifecycleService batchService = new ParentContentLifecycleService(
                batchJdbcTemplate,
                new TransactionTemplate(transactionManager),
                batchAccessControl,
                batchAuditRepository);
        when(batchAccessControl.currentUserId()).thenReturn(900L);
        when(batchJdbcTemplate.queryForObject(
                contains("SELECT COUNT(*)"), eq(Integer.class), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(1);
        when(batchJdbcTemplate.queryForList(
                contains("WHERE academic_term_id=? AND student_id=? FOR UPDATE"),
                anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(row("id", 101L)));
        when(batchJdbcTemplate.queryForList(
                contains("FROM school_student_overall_comment WHERE id=?"), anyLong()))
                .thenReturn(Collections.singletonList(row("id", 101L, "status", 1)));
        when(batchJdbcTemplate.queryForList(
                contains("JOIN school_class c"), anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(row("classId", 41L)));
        when(batchJdbcTemplate.queryForList(
                contains("FROM school_student_achievement WHERE academic_term_id=?"),
                anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());
        when(batchJdbcTemplate.update(
                contains("status=1,published_at=NULL,teacher_name_snapshot=NULL"),
                anyLong(), any(), any(), any(), anyLong(), any(Timestamp.class), anyLong()))
                .thenReturn(1, 0);

        List<ParentContentLifecycleService.ParentContentImportItem> items = Arrays.asList(
                new ParentContentLifecycleService.ParentContentImportItem(
                        31L, 41L, 51L, true, "学生一评语", null, null,
                        Collections.emptyList()),
                new ParentContentLifecycleService.ParentContentImportItem(
                        31L, 41L, 52L, true, "学生二评语", null, null,
                        Collections.emptyList()));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> batchService.importParentContentBatch(items));

        assertEquals("评语更新失败", error.getMessage());
        assertTrue(transactionManager.rolledBack);
        assertFalse(transactionManager.committed);
        verify(batchJdbcTemplate, times(2)).update(
                contains("status=1,published_at=NULL,teacher_name_snapshot=NULL"),
                anyLong(), any(), any(), any(), anyLong(), any(Timestamp.class), anyLong());
    }

    @Test
    void classChangeWithdrawsPublishedCommentAndAppendsAudit() {
        when(jdbcTemplate.queryForList(
                contains("WHERE student_id=? AND class_id=? FOR UPDATE"),
                eq(51L), eq(41L)))
                .thenReturn(Collections.singletonList(row(
                        "id", 111L,
                        "status", 2,
                        "publishedAt", Timestamp.valueOf("2026-08-31 10:00:00"),
                        "teacherNameSnapshot", "王五老师")));
        when(jdbcTemplate.update(
                contains("SET class_id=?,status=1,published_at=NULL,teacher_name_snapshot=NULL"),
                anyLong(), anyLong())).thenReturn(1);

        service.updateCommentsForStudentClassChangeInCurrentTransaction(51L, 41L, 42L);

        verify(jdbcTemplate).update(
                contains("SET class_id=?,status=1,published_at=NULL,teacher_name_snapshot=NULL"),
                eq(42L), eq(111L));
        verify(auditRepository).append(
                ParentContentLifecycleService.CONTENT_COMMENT,
                111L,
                ParentContentLifecycleService.ACTION_UNPUBLISH,
                900L);
    }

    @Test
    void auditMigrationContainsOnlyMinimalImmutableEventFields() throws Exception {
        String sql = new String(
                Files.readAllBytes(Paths.get("scripts/mysql/016_parent_content_audit.sql")),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS school_parent_content_audit"));
        assertTrue(sql.contains("content_type VARCHAR(32) NOT NULL"));
        assertTrue(sql.contains("content_id BIGINT UNSIGNED NOT NULL"));
        assertTrue(sql.contains("operator_user_id BIGINT UNSIGNED NOT NULL"));
        assertFalse(sql.contains("student_id"));
        assertFalse(sql.contains("achievement_text"));
        assertFalse(sql.contains("overall_comment"));
    }

    private void stubCommentQueries(Map<String, Object> comment, Map<String, Object> teacher) {
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM school_student_overall_comment WHERE id=? FOR UPDATE")) {
                return Collections.singletonList(comment);
            }
            if (sql.contains("FROM sys_user u")) {
                return Collections.singletonList(teacher);
            }
            if (sql.contains("SELECT id,status,teacher_name_snapshot")) {
                return Collections.singletonList(row(
                        "id", comment.get("id"),
                        "status", 2,
                        "teacherNameSnapshot", "王五老师",
                        "publishedAt", Timestamp.valueOf("2026-08-31 11:00:00")));
            }
            return Collections.emptyList();
        });
    }

    private Map<String, Object> commentRow(
            long id,
            int status,
            Timestamp publishedAt,
            String teacherNameSnapshot) {
        return row(
                "id", id,
                "academicTermId", 31L,
                "classId", 41L,
                "studentId", 51L,
                "overallComment", "学习认真",
                "strengths", null,
                "improvementPoints", null,
                "evaluatorUserId", 61L,
                "status", status,
                "publishedAt", publishedAt,
                "teacherNameSnapshot", teacherNameSnapshot);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    private static final class TrackingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean committed;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No resource is required; this test verifies transaction boundary behavior.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
