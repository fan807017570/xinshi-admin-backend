package com.xinshi.admin.application.h5;

import com.xinshi.admin.application.school.AccessControlService;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the transactional lifecycle of parent-visible comments and achievements.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Service
public class ParentContentLifecycleService {
    public static final String CONTENT_COMMENT = "COMMENT";
    public static final String CONTENT_ACHIEVEMENT = "ACHIEVEMENT";
    public static final String ACTION_PUBLISH = "PUBLISH";
    public static final String ACTION_UNPUBLISH = "UNPUBLISH";
    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_IMPORT = "IMPORT";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_DELETE = "DELETE";

    private static final int DRAFT_STATUS = 1;
    private static final int PUBLISHED_STATUS = 2;
    private static final int COMMENT_MAX_LENGTH = 1000;
    private static final int ACHIEVEMENT_MAX_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AccessControlService accessControlService;
    private final ParentContentAuditRepository auditRepository;

    public ParentContentLifecycleService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            AccessControlService accessControlService,
            ParentContentAuditRepository auditRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.accessControlService = accessControlService;
        this.auditRepository = auditRepository;
    }

    public Map<String, Object> publishComment(long id) {
        return requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            Map<String, Object> row = lockComment(id);
            long classId = longValue(row, "classId");
            accessControlService.ensureCanManageHeadTeacherClass(classId);
            validateActiveCommentScope(row);
            CommentText text = normalizeComment(
                    stringValue(row, "overallComment"),
                    stringValue(row, "strengths"),
                    stringValue(row, "improvementPoints"));
            String teacherName = loadTeacherSnapshot(longValue(row, "evaluatorUserId"));

            boolean alreadyPublished = intValue(row, "status") == PUBLISHED_STATUS
                    && row.get("publishedAt") != null
                    && teacherName.equals(stringValue(row, "teacherNameSnapshot"))
                    && text.matches(row);
            if (!alreadyPublished) {
                int updated = jdbcTemplate.update(
                        "UPDATE school_student_overall_comment SET overall_comment=?,strengths=?,"
                                + "improvement_points=?,status=2,teacher_name_snapshot=?,"
                                + "published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        text.overallComment, text.strengths, text.improvementPoints, teacherName, id);
                requireSingleUpdate(updated, "评语发布失败");
                appendAudit(CONTENT_COMMENT, id, ACTION_PUBLISH);
            }
            return loadCommentState(id);
        });
    }

    public Map<String, Object> unpublishComment(long id) {
        return requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            Map<String, Object> row = lockComment(id);
            accessControlService.ensureCanManageHeadTeacherClass(longValue(row, "classId"));
            boolean alreadyDraft = intValue(row, "status") == DRAFT_STATUS
                    && row.get("publishedAt") == null
                    && isBlank(stringValue(row, "teacherNameSnapshot"));
            if (!alreadyDraft) {
                int updated = jdbcTemplate.update(
                        "UPDATE school_student_overall_comment SET status=1,published_at=NULL,"
                                + "teacher_name_snapshot=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        id);
                requireSingleUpdate(updated, "评语撤回失败");
                appendAudit(CONTENT_COMMENT, id, ACTION_UNPUBLISH);
            }
            return loadCommentState(id);
        });
    }

    public Map<String, Object> saveCommentDraft(
            long academicTermId,
            long classId,
            long studentId,
            String overallComment,
            String strengths,
            String improvementPoints) {
        CommentText text = normalizeComment(overallComment, strengths, improvementPoints);
        return requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            return saveCommentDraftInCurrentTransaction(
                    academicTermId,
                    classId,
                    studentId,
                    text,
                    accessControlService.currentUserId(),
                    ACTION_EDIT);
        });
    }

    public long createAchievementDraft(
            long academicTermId,
            long studentId,
            Long honorTypeId,
            String achievementText,
            int sortOrder) {
        String normalizedText = normalizeAchievement(achievementText);
        return requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            accessControlService.ensureCanManageHeadTeacherStudent(studentId);
            validateActiveAchievementScope(academicTermId, studentId);
            long id = insertAchievement(
                    academicTermId, studentId, honorTypeId, normalizedText, Math.max(sortOrder, 0));
            appendAudit(CONTENT_ACHIEVEMENT, id, ACTION_CREATE);
            return id;
        });
    }

    public void updateAchievementDraft(
            long id,
            Long honorTypeId,
            String achievementText,
            int sortOrder) {
        String normalizedText = normalizeAchievement(achievementText);
        requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            Map<String, Object> row = lockAchievement(id);
            long studentId = longValue(row, "studentId");
            accessControlService.ensureCanManageHeadTeacherStudent(studentId);
            validateActiveAchievementScope(longValue(row, "academicTermId"), studentId);
            int updated = jdbcTemplate.update(
                    "UPDATE school_student_achievement SET honor_type_id=?,achievement_text=?,"
                            + "sort_order=?,status=1,published_at=NULL WHERE id=?",
                    honorTypeId, normalizedText, Math.max(sortOrder, 0), id);
            requireSingleUpdate(updated, "荣誉更新失败");
            appendAudit(CONTENT_ACHIEVEMENT, id, ACTION_EDIT);
            return Boolean.TRUE;
        });
    }

    public void deleteAchievement(long id) {
        requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            Map<String, Object> row = lockAchievement(id);
            long studentId = longValue(row, "studentId");
            accessControlService.ensureCanManageHeadTeacherStudent(studentId);
            if (intValue(row, "status") == PUBLISHED_STATUS || row.get("publishedAt") != null) {
                throw new IllegalArgumentException("已发布荣誉不能删除，请先撤回");
            }
            int updated = jdbcTemplate.update(
                    "DELETE FROM school_student_achievement WHERE id=? AND status=1 AND published_at IS NULL",
                    id);
            requireSingleUpdate(updated, "荣誉删除失败");
            appendAudit(CONTENT_ACHIEVEMENT, id, ACTION_DELETE);
            return Boolean.TRUE;
        });
    }

    public Map<String, Object> publishAchievement(long id) {
        return requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            Map<String, Object> row = lockAchievement(id);
            long studentId = longValue(row, "studentId");
            accessControlService.ensureCanManageHeadTeacherStudent(studentId);
            validateActiveAchievementScope(longValue(row, "academicTermId"), studentId);
            normalizeAchievement(stringValue(row, "achievementText"));
            boolean alreadyPublished = intValue(row, "status") == PUBLISHED_STATUS
                    && row.get("publishedAt") != null;
            if (!alreadyPublished) {
                int updated = jdbcTemplate.update(
                        "UPDATE school_student_achievement SET status=2,"
                                + "published_at=CURRENT_TIMESTAMP WHERE id=?",
                        id);
                requireSingleUpdate(updated, "荣誉发布失败");
                appendAudit(CONTENT_ACHIEVEMENT, id, ACTION_PUBLISH);
            }
            return loadAchievementState(id);
        });
    }

    public Map<String, Object> unpublishAchievement(long id) {
        return requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            Map<String, Object> row = lockAchievement(id);
            long studentId = longValue(row, "studentId");
            accessControlService.ensureCanManageHeadTeacherStudent(studentId);
            boolean alreadyDraft = intValue(row, "status") == DRAFT_STATUS
                    && row.get("publishedAt") == null;
            if (!alreadyDraft) {
                int updated = jdbcTemplate.update(
                        "UPDATE school_student_achievement SET status=1,published_at=NULL WHERE id=?",
                        id);
                requireSingleUpdate(updated, "荣誉撤回失败");
                appendAudit(CONTENT_ACHIEVEMENT, id, ACTION_UNPUBLISH);
            }
            return loadAchievementState(id);
        });
    }

    public void reconcileImportedAchievements(
            long academicTermId,
            long studentId,
            List<AchievementImportItem> importedItems) {
        List<AchievementImportItem> safeItems = importedItems == null
                ? Collections.emptyList() : new ArrayList<>(importedItems);
        requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            reconcileImportedAchievementsInCurrentTransaction(academicTermId, studentId, safeItems);
            return Boolean.TRUE;
        });
    }

    /**
     * Imports one student's comment and complete achievement set atomically.
     * The authenticated operator is always used as evaluator; caller supplied identity is ignored.
     */
    public void importParentContent(
            long academicTermId,
            long classId,
            long studentId,
            boolean hasComment,
            String overallComment,
            String strengths,
            String improvementPoints,
            List<AchievementImportItem> importedItems) {
        importParentContentBatch(Collections.singletonList(new ParentContentImportItem(
                academicTermId,
                classId,
                studentId,
                hasComment,
                overallComment,
                strengths,
                improvementPoints,
                importedItems)));
    }

    /**
     * Imports all students from one Excel file in one transaction. Any student failure
     * propagates to the transaction boundary and rolls back the complete file.
     */
    public void importParentContentBatch(List<ParentContentImportItem> importedContents) {
        if (importedContents == null) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        List<PreparedParentContentImport> preparedImports = new ArrayList<>();
        for (ParentContentImportItem item : importedContents) {
            if (item == null) {
                throw new IllegalArgumentException("导入学生内容不能为空");
            }
            CommentText text = item.hasComment
                    ? normalizeComment(item.overallComment, item.strengths, item.improvementPoints)
                    : null;
            List<AchievementImportItem> safeAchievements = item.importedItems == null
                    ? Collections.emptyList() : new ArrayList<>(item.importedItems);
            preparedImports.add(new PreparedParentContentImport(item, text, safeAchievements));
        }
        requiredTransactionResult(() -> {
            accessControlService.ensureHeadTeacherOrAdmin();
            long evaluatorUserId = accessControlService.currentUserId();
            for (PreparedParentContentImport preparedImport : preparedImports) {
                ParentContentImportItem item = preparedImport.item;
                if (preparedImport.text != null) {
                    saveCommentDraftInCurrentTransaction(
                            item.academicTermId,
                            item.classId,
                            item.studentId,
                            preparedImport.text,
                            evaluatorUserId,
                            ACTION_IMPORT);
                }
                reconcileImportedAchievementsInCurrentTransaction(
                        item.academicTermId, item.studentId, preparedImport.importedItems);
            }
            return Boolean.TRUE;
        });
    }

    /**
     * Relinks comments after a student class change. The caller must already own the
     * student-transfer transaction, so comment changes and the student update commit together.
     */
    public void updateCommentsForStudentClassChangeInCurrentTransaction(
            long studentId,
            long oldClassId,
            long newClassId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,status,published_at AS publishedAt,"
                        + "teacher_name_snapshot AS teacherNameSnapshot "
                        + "FROM school_student_overall_comment "
                        + "WHERE student_id=? AND class_id=? FOR UPDATE",
                studentId, oldClassId);
        for (Map<String, Object> row : rows) {
            long id = longValue(row, "id");
            boolean published = intValue(row, "status") == PUBLISHED_STATUS
                    || row.get("publishedAt") != null
                    || !isBlank(stringValue(row, "teacherNameSnapshot"));
            int updated;
            if (published) {
                updated = jdbcTemplate.update(
                        "UPDATE school_student_overall_comment SET class_id=?,status=1,"
                                + "published_at=NULL,teacher_name_snapshot=NULL,"
                                + "updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        newClassId, id);
                requireSingleUpdate(updated, "学生转班评语撤回失败");
                appendAudit(CONTENT_COMMENT, id, ACTION_UNPUBLISH);
            } else {
                updated = jdbcTemplate.update(
                        "UPDATE school_student_overall_comment SET class_id=?,"
                                + "updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        newClassId, id);
                requireSingleUpdate(updated, "学生转班评语更新失败");
                appendAudit(CONTENT_COMMENT, id, ACTION_EDIT);
            }
        }
    }

    private Map<String, Object> saveCommentDraftInCurrentTransaction(
            long academicTermId,
            long classId,
            long studentId,
            CommentText text,
            long evaluatorUserId,
            String action) {
        accessControlService.ensureCanManageHeadTeacherClass(classId);
        validateActiveScope(academicTermId, studentId, classId);
        Map<String, Object> existing = lockCommentByTermAndStudent(academicTermId, studentId);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        long id;
        if (existing.isEmpty()) {
            id = insertComment(academicTermId, classId, studentId, text, evaluatorUserId, now);
        } else {
            id = longValue(existing, "id");
            int updated = jdbcTemplate.update(
                    "UPDATE school_student_overall_comment SET class_id=?,overall_comment=?,"
                            + "strengths=?,improvement_points=?,evaluator_user_id=?,evaluated_at=?,"
                            + "status=1,published_at=NULL,teacher_name_snapshot=NULL,"
                            + "updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    classId, text.overallComment, text.strengths, text.improvementPoints,
                    evaluatorUserId, now, id);
            requireSingleUpdate(updated, "评语更新失败");
        }
        appendAudit(CONTENT_COMMENT, id, action);
        return loadComment(id);
    }

    private void reconcileImportedAchievementsInCurrentTransaction(
            long academicTermId,
            long studentId,
            List<AchievementImportItem> importedItems) {
        accessControlService.ensureCanManageHeadTeacherStudent(studentId);
        validateActiveAchievementScope(academicTermId, studentId);
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
                "SELECT id,academic_term_id AS academicTermId,student_id AS studentId,"
                        + "honor_type_id AS honorTypeId,achievement_text AS achievementText,"
                        + "sort_order AS sortOrder,status,published_at AS publishedAt "
                        + "FROM school_student_achievement "
                        + "WHERE academic_term_id=? AND student_id=? FOR UPDATE",
                academicTermId, studentId);
        Map<Long, Map<String, Object>> remaining = new LinkedHashMap<>();
        for (Map<String, Object> row : existingRows) {
            remaining.put(longValue(row, "id"), row);
        }

        int nextSortOrder = nextSortOrder(existingRows);
        for (AchievementImportItem item : importedItems) {
            if (item == null) {
                throw new IllegalArgumentException("导入荣誉不能为空");
            }
            String text = normalizeAchievement(item.getAchievementText());
            if (item.getId() == null) {
                Map.Entry<Long, Map<String, Object>> matched = findMatchingAchievement(
                        remaining, item.getHonorTypeId(), text);
                if (matched == null) {
                    long id = insertAchievement(
                            academicTermId, studentId, item.getHonorTypeId(), text, nextSortOrder++);
                    appendAudit(CONTENT_ACHIEVEMENT, id, ACTION_IMPORT);
                } else {
                    remaining.remove(matched.getKey());
                    int updated = jdbcTemplate.update(
                            "UPDATE school_student_achievement SET status=1,published_at=NULL WHERE id=?",
                            matched.getKey());
                    requireSingleUpdate(updated, "导入荣誉更新失败");
                    appendAudit(CONTENT_ACHIEVEMENT, matched.getKey(), ACTION_IMPORT);
                }
                continue;
            }
            Map<String, Object> existing = remaining.remove(item.getId());
            if (existing == null) {
                throw new IllegalArgumentException("导入荣誉不存在或不属于当前学生和学期");
            }
            int updated = jdbcTemplate.update(
                    "UPDATE school_student_achievement SET honor_type_id=?,achievement_text=?,"
                            + "sort_order=?,status=1,published_at=NULL WHERE id=?",
                    item.getHonorTypeId(), text, intValue(existing, "sortOrder"), item.getId());
            requireSingleUpdate(updated, "导入荣誉更新失败");
            appendAudit(CONTENT_ACHIEVEMENT, item.getId(), ACTION_IMPORT);
        }

        for (Map.Entry<Long, Map<String, Object>> entry : remaining.entrySet()) {
            Map<String, Object> row = entry.getValue();
            if (intValue(row, "status") == PUBLISHED_STATUS || row.get("publishedAt") != null) {
                int updated = jdbcTemplate.update(
                        "UPDATE school_student_achievement SET status=1,published_at=NULL WHERE id=?",
                        entry.getKey());
                requireSingleUpdate(updated, "导入荣誉撤回失败");
            } else {
                int updated = jdbcTemplate.update(
                        "DELETE FROM school_student_achievement "
                                + "WHERE id=? AND status=1 AND published_at IS NULL",
                        entry.getKey());
                requireSingleUpdate(updated, "导入荣誉删除失败");
            }
            appendAudit(CONTENT_ACHIEVEMENT, entry.getKey(), ACTION_IMPORT);
        }
    }

    private Map.Entry<Long, Map<String, Object>> findMatchingAchievement(
            Map<Long, Map<String, Object>> remaining,
            Long honorTypeId,
            String achievementText) {
        for (Map.Entry<Long, Map<String, Object>> entry : remaining.entrySet()) {
            Map<String, Object> row = entry.getValue();
            Long existingHonorTypeId = row.get("honorTypeId") instanceof Number
                    ? ((Number)row.get("honorTypeId")).longValue() : null;
            if (Objects.equals(honorTypeId, existingHonorTypeId)
                    && achievementText.equals(stringValue(row, "achievementText"))) {
                return entry;
            }
        }
        return null;
    }

    private Map<String, Object> lockComment(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,academic_term_id AS academicTermId,class_id AS classId,student_id AS studentId,"
                        + "overall_comment AS overallComment,strengths,improvement_points AS improvementPoints,"
                        + "evaluator_user_id AS evaluatorUserId,status,"
                        + "teacher_name_snapshot AS teacherNameSnapshot,published_at AS publishedAt "
                        + "FROM school_student_overall_comment WHERE id=? FOR UPDATE",
                id);
        return requireRow(rows, "评语不存在");
    }

    private Map<String, Object> lockCommentByTermAndStudent(long academicTermId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,status,published_at AS publishedAt,"
                        + "teacher_name_snapshot AS teacherNameSnapshot "
                        + "FROM school_student_overall_comment "
                        + "WHERE academic_term_id=? AND student_id=? FOR UPDATE",
                academicTermId, studentId);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    private Map<String, Object> lockAchievement(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,academic_term_id AS academicTermId,student_id AS studentId,"
                        + "achievement_text AS achievementText,status,awarded_on AS awardedOn,"
                        + "published_at AS publishedAt FROM school_student_achievement WHERE id=? FOR UPDATE",
                id);
        return requireRow(rows, "荣誉不存在");
    }

    private void validateActiveCommentScope(Map<String, Object> row) {
        validateActiveScope(
                longValue(row, "academicTermId"),
                longValue(row, "studentId"),
                longValue(row, "classId"));
    }

    private void validateActiveAchievementScope(long academicTermId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.class_id AS classId FROM school_student s "
                        + "JOIN school_class c ON c.id=s.class_id AND c.status=1 AND c.is_deleted=0 "
                        + "JOIN school_academic_term t ON t.id=? AND t.status=1 "
                        + "WHERE s.id=? AND s.status=1 AND s.is_deleted=0",
                academicTermId, studentId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("学生、学期或班级未启用");
        }
    }

    private void validateActiveScope(long academicTermId, long studentId, long classId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM school_student s "
                        + "JOIN school_class c ON c.id=s.class_id "
                        + "JOIN school_academic_term t ON t.id=? "
                        + "WHERE s.id=? AND s.class_id=? AND c.id=? "
                        + "AND s.status=1 AND s.is_deleted=0 "
                        + "AND c.status=1 AND c.is_deleted=0 AND t.status=1",
                Integer.class, academicTermId, studentId, classId, classId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("学生、学期或班级未启用");
        }
    }

    private String loadTeacherSnapshot(long evaluatorUserId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT u.real_name AS realName FROM sys_user u "
                        + "WHERE u.id=? AND u.status=1 AND u.is_deleted=0 "
                        + "AND EXISTS (SELECT 1 FROM sys_user_role ur "
                        + "JOIN sys_role r ON r.id=ur.role_id AND r.status=1 "
                        + "WHERE ur.user_id=u.id AND r.role_code IN ('SUPER_ADMIN','TEACHER','HEAD_TEACHER'))",
                evaluatorUserId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("评语教师不存在或已停用");
        }
        String realName = trimToNull(stringValue(rows.get(0), "realName"));
        if (realName == null) {
            throw new IllegalArgumentException("评语教师姓名不能为空");
        }
        return realName.endsWith("老师") ? realName : realName + "老师";
    }

    private CommentText normalizeComment(String overallComment, String strengths, String improvementPoints) {
        String normalizedOverall = trimToNull(overallComment);
        String normalizedStrengths = trimToNull(strengths);
        String normalizedImprovement = trimToNull(improvementPoints);
        validateMaxLength(normalizedOverall, COMMENT_MAX_LENGTH, "总体评价");
        validateMaxLength(normalizedStrengths, COMMENT_MAX_LENGTH, "优点");
        validateMaxLength(normalizedImprovement, COMMENT_MAX_LENGTH, "改进点");
        if (normalizedOverall == null && normalizedStrengths == null && normalizedImprovement == null) {
            throw new IllegalArgumentException("评语内容不能全部为空");
        }
        return new CommentText(
                normalizedOverall == null ? "" : normalizedOverall,
                normalizedStrengths,
                normalizedImprovement);
    }

    private String normalizeAchievement(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("荣誉内容不能为空");
        }
        validateMaxLength(normalized, ACHIEVEMENT_MAX_LENGTH, "荣誉内容");
        return normalized;
    }

    private void validateMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "字");
        }
    }

    private long insertComment(
            long academicTermId,
            long classId,
            long studentId,
            CommentText text,
            long evaluatorUserId,
            Timestamp evaluatedAt) {
        return insertAndReturnId(
                "INSERT INTO school_student_overall_comment "
                        + "(academic_term_id,class_id,student_id,overall_comment,strengths,"
                        + "improvement_points,evaluator_user_id,evaluated_at,status,"
                        + "teacher_name_snapshot,published_at) VALUES (?,?,?,?,?,?,?,?,1,NULL,NULL)",
                academicTermId, classId, studentId, text.overallComment, text.strengths,
                text.improvementPoints, evaluatorUserId, evaluatedAt);
    }

    private long insertAchievement(
            long academicTermId,
            long studentId,
            Long honorTypeId,
            String achievementText,
            int sortOrder) {
        return insertAndReturnId(
                "INSERT INTO school_student_achievement "
                        + "(academic_term_id,student_id,honor_type_id,achievement_text,sort_order,"
                        + "status,published_at) VALUES (?,?,?,?,?,1,NULL)",
                academicTermId, studentId, honorTypeId, achievementText, sortOrder);
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < args.length; index++) {
                Object value = args[index];
                if (value instanceof Timestamp) {
                    statement.setTimestamp(index + 1, (Timestamp) value);
                } else if (value instanceof Date) {
                    statement.setDate(index + 1, (Date) value);
                } else {
                    statement.setObject(index + 1, value);
                }
            }
            return statement;
        }, keyHolder);
        requireSingleUpdate(updated, "内容新增失败");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Inserted parent content has no generated id");
        }
        return key.longValue();
    }

    private Map<String, Object> loadComment(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,academic_term_id AS academicTermId,class_id AS classId,student_id AS studentId,"
                        + "overall_comment AS overallComment,strengths,"
                        + "improvement_points AS improvementPoints,evaluator_user_id AS evaluatorUserId,"
                        + "evaluated_at AS evaluatedAt,status,teacher_name_snapshot AS teacherNameSnapshot,"
                        + "published_at AS publishedAt,created_at AS createdAt "
                        + "FROM school_student_overall_comment WHERE id=?",
                id);
        return requireRow(rows, "评语不存在");
    }

    private Map<String, Object> loadCommentState(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,status,teacher_name_snapshot AS teacherNameSnapshot,"
                        + "published_at AS publishedAt FROM school_student_overall_comment WHERE id=?",
                id);
        return requireRow(rows, "评语不存在");
    }

    private Map<String, Object> loadAchievementState(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,status,published_at AS publishedAt "
                        + "FROM school_student_achievement WHERE id=?",
                id);
        return requireRow(rows, "荣誉不存在");
    }

    private void appendAudit(String contentType, long contentId, String action) {
        auditRepository.append(contentType, contentId, action, accessControlService.currentUserId());
    }

    private int nextSortOrder(List<Map<String, Object>> rows) {
        int max = -1;
        for (Map<String, Object> row : rows) {
            max = Math.max(max, intValue(row, "sortOrder"));
        }
        return max + 1;
    }

    private Map<String, Object> requireRow(List<Map<String, Object>> rows, String message) {
        if (rows == null || rows.size() != 1) {
            throw new IllegalArgumentException(message);
        }
        return rows.get(0);
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Missing numeric field: " + key);
        }
        return ((Number) value).longValue();
    }

    private int intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String stringValue(Map<String, Object> row, String key) {
        return Objects.toString(row.get(key), null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private void requireSingleUpdate(int updated, String message) {
        if (updated != 1) {
            throw new IllegalStateException(message);
        }
    }

    private <T> T requiredTransactionResult(TransactionWork<T> work) {
        T result = transactionTemplate.execute(status -> work.execute());
        if (result == null) {
            throw new IllegalStateException("Parent content transaction returned no result");
        }
        return result;
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T execute();
    }

    /** Imported achievement values after Excel validation. */
    public static final class AchievementImportItem {
        private final Long id;
        private final Long honorTypeId;
        private final String achievementText;

        public AchievementImportItem(Long id, Long honorTypeId, String achievementText) {
            this.id = id;
            this.honorTypeId = honorTypeId;
            this.achievementText = achievementText;
        }

        public Long getId() {
            return id;
        }

        public Long getHonorTypeId() {
            return honorTypeId;
        }

        public String getAchievementText() {
            return achievementText;
        }
    }

    /** One student's validated values from a complete parent-content Excel file. */
    public static final class ParentContentImportItem {
        private final long academicTermId;
        private final long classId;
        private final long studentId;
        private final boolean hasComment;
        private final String overallComment;
        private final String strengths;
        private final String improvementPoints;
        private final List<AchievementImportItem> importedItems;

        public ParentContentImportItem(
                long academicTermId,
                long classId,
                long studentId,
                boolean hasComment,
                String overallComment,
                String strengths,
                String improvementPoints,
                List<AchievementImportItem> importedItems) {
            this.academicTermId = academicTermId;
            this.classId = classId;
            this.studentId = studentId;
            this.hasComment = hasComment;
            this.overallComment = overallComment;
            this.strengths = strengths;
            this.improvementPoints = improvementPoints;
            this.importedItems = importedItems == null
                    ? Collections.emptyList() : new ArrayList<>(importedItems);
        }
    }

    private static final class PreparedParentContentImport {
        private final ParentContentImportItem item;
        private final CommentText text;
        private final List<AchievementImportItem> importedItems;

        private PreparedParentContentImport(
                ParentContentImportItem item,
                CommentText text,
                List<AchievementImportItem> importedItems) {
            this.item = item;
            this.text = text;
            this.importedItems = importedItems;
        }
    }

    private static final class CommentText {
        private final String overallComment;
        private final String strengths;
        private final String improvementPoints;

        private CommentText(String overallComment, String strengths, String improvementPoints) {
            this.overallComment = overallComment;
            this.strengths = strengths;
            this.improvementPoints = improvementPoints;
        }

        private boolean matches(Map<String, Object> row) {
            return Objects.equals(overallComment, Objects.toString(row.get("overallComment"), ""))
                    && Objects.equals(strengths, Objects.toString(row.get("strengths"), null))
                    && Objects.equals(
                            improvementPoints,
                            Objects.toString(row.get("improvementPoints"), null));
        }
    }
}
