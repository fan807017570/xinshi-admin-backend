package com.xinshi.admin.application.courseresult;

import com.xinshi.admin.application.commentpolish.CommentPolishService;
import com.xinshi.admin.application.h5.ParentContentLifecycleService;
import com.xinshi.admin.application.school.AccessControlService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 负责成绩及评语 Excel 的预校验、一次性令牌和原子提交。
 */
@Service
public class GradeImportWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(GradeImportWorkflowService.class);
    private static final String COURSE_TEMPLATE_TYPE = "COURSE_RESULT";
    private static final String PARENT_TEMPLATE_TYPE = "PARENT_CONTENT";
    private static final String CURRENT_COURSE_VERSION = "2.0";
    private static final long TOKEN_TTL_MILLIS = 15L * 60L * 1000L;
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ACTIVE_TOKENS = 1000;
    private static final int COMMENT_MAX_LENGTH = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AccessControlService accessControlService;
    private final ParentContentLifecycleService lifecycleService;
    private final CommentPolishService commentPolishService;
    private final ConcurrentMap<String, ImportSession> importSessions =
            new ConcurrentHashMap<String, ImportSession>();
    private final ThreadLocal<DataFormatter> dataFormatter =
            ThreadLocal.withInitial(() -> new DataFormatter(Locale.ROOT));

    public GradeImportWorkflowService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            AccessControlService accessControlService,
            ParentContentLifecycleService lifecycleService,
            CommentPolishService commentPolishService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.accessControlService = accessControlService;
        this.lifecycleService = lifecycleService;
        this.commentPolishService = commentPolishService;
    }

    public Map<String, Object> precheckCourseResults(
            MultipartFile file,
            Long academicTermId,
            String gradeSession,
            Integer gradeLevel,
            Long classId,
            Long subjectId,
            Long examTypeId) {
        ImportContext context = ImportContext.course(
                academicTermId, gradeSession, gradeLevel, classId, subjectId, examTypeId);
        CourseContext courseContext = loadCourseContext(context);
        byte[] fileBytes = readFile(file);
        ParsedCourseImport parsed = parseCourseWorkbook(fileBytes, context, courseContext);
        String token = null;
        long expiresAt = System.currentTimeMillis() + TOKEN_TTL_MILLIS;
        if (parsed.errors.isEmpty()) {
            ImportSession session = ImportSession.course(
                    currentSessionToken(), accessControlService.currentUserId(), expiresAt,
                    context, parsed.templateVersion, sha256(fileBytes),
                    fingerprintCourseContext(context, courseContext.classSubjectId),
                    parsed.rows, parsed.total, parsed.skipped, parsed.creates, parsed.updates);
            token = storeSession(session);
        }
        return precheckResponse(token, expiresAt, COURSE_TEMPLATE_TYPE, parsed.templateVersion,
                parsed.total, parsed.creates, parsed.updates, parsed.skipped, parsed.errors);
    }

    public Map<String, Object> commitCourseResults(String token) {
        ImportSession session = consumeSession(token, ImportType.COURSE_RESULT);
        Map<String, Object> result = transactionTemplate.execute(status -> {
            CourseContext currentContext = loadCourseContext(session.context);
            String currentVersion = fingerprintCourseContext(
                    session.context, currentContext.classSubjectId);
            if (!session.dataVersion.equals(currentVersion)) {
                throw new IllegalArgumentException("预校验后成绩数据或课程配置已变化，请重新预校验");
            }
            int written = writeCourseRows(session, currentContext.classSubjectId);
            Map<String, Object> committed = new LinkedHashMap<String, Object>();
            committed.put("total", session.total);
            committed.put("success", written);
            committed.put("created", session.creates);
            committed.put("updated", session.updates);
            committed.put("skipped", session.skipped);
            committed.put("failed", 0);
            committed.put("errors", Collections.emptyList());
            return committed;
        });
        if (result == null) {
            throw new IllegalStateException("成绩导入事务未返回结果");
        }
        return result;
    }

    public Map<String, Object> precheckParentContent(
            MultipartFile file,
            Long academicTermId,
            String gradeSession,
            Integer gradeLevel,
            Long classId,
            boolean enableAiPolish) {
        ImportContext context = ImportContext.parent(
                academicTermId, gradeSession, gradeLevel, classId);
        loadParentContext(context);
        byte[] fileBytes = readFile(file);
        ParsedParentImport parsed = parseParentWorkbook(fileBytes, context, enableAiPolish);
        String token = null;
        long expiresAt = System.currentTimeMillis() + TOKEN_TTL_MILLIS;
        if (parsed.errors.isEmpty()) {
            ImportSession session = ImportSession.parent(
                    currentSessionToken(), accessControlService.currentUserId(), expiresAt,
                    context, parsed.templateVersion, sha256(fileBytes),
                    fingerprintParentContext(context), parsed.items,
                    parsed.total, parsed.skipped, parsed.creates, parsed.updates,
                    parsed.aiPolished);
            token = storeSession(session);
        }
        Map<String, Object> response = precheckResponse(
                token, expiresAt, PARENT_TEMPLATE_TYPE, parsed.templateVersion,
                parsed.total, parsed.creates, parsed.updates, parsed.skipped, parsed.errors);
        response.put("aiPolished", parsed.aiPolished);
        return response;
    }

    public Map<String, Object> commitParentContent(String token) {
        ImportSession session = consumeSession(token, ImportType.PARENT_CONTENT);
        Map<String, Object> result = transactionTemplate.execute(status -> {
            loadParentContext(session.context);
            if (!session.dataVersion.equals(fingerprintParentContext(session.context))) {
                throw new IllegalArgumentException("预校验后评语、荣誉或班级数据已变化，请重新预校验");
            }
            lifecycleService.importParentContentBatch(session.parentItems);
            Map<String, Object> committed = new LinkedHashMap<String, Object>();
            committed.put("total", session.total);
            committed.put("success", session.parentItems.size());
            committed.put("created", session.creates);
            committed.put("updated", session.updates);
            committed.put("skipped", session.skipped);
            committed.put("failed", 0);
            committed.put("aiPolished", session.aiPolished);
            committed.put("errors", Collections.emptyList());
            return committed;
        });
        if (result == null) {
            throw new IllegalStateException("评语与荣誉导入事务未返回结果");
        }
        return result;
    }

    private CourseContext loadCourseContext(ImportContext context) {
        validateCommonContext(context);
        if (context.subjectId == null) throw new IllegalArgumentException("请选择科目");
        if (context.examTypeId == null) throw new IllegalArgumentException("请选择考试类型");
        accessControlService.ensureTeacherCanWriteResults();
        accessControlService.ensureCanManageCourseContext(
                context.academicTermId, context.classId, context.subjectId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT cs.id AS classSubjectId, c.grade_session AS gradeSession, "
                        + "c.grade_level AS gradeLevel, c.class_name AS className, "
                        + "t.term_name AS termName, s.subject_name AS subjectName, "
                        + "s.min_score AS minScore, s.max_score AS maxScore, "
                        + "e.exam_type_name AS examTypeName "
                        + "FROM school_class_subject cs "
                        + "JOIN school_academic_term t ON t.id = cs.academic_term_id AND t.status = 1 "
                        + "JOIN school_class c ON c.id = cs.class_id AND c.status = 1 AND c.is_deleted = 0 "
                        + "JOIN school_subject s ON s.id = cs.subject_id AND s.status = 1 "
                        + "JOIN school_exam_type e ON e.id = ? AND e.status = 1 "
                        + "WHERE cs.academic_term_id = ? AND cs.class_id = ? "
                        + "AND cs.subject_id = ? AND cs.status = 1",
                context.examTypeId, context.academicTermId, context.classId, context.subjectId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("所选学期、班级、科目或考试类型无效");
        }
        Map<String, Object> row = rows.get(0);
        validateClassCoordinates(context, row);
        return new CourseContext(
                longValue(row, "classSubjectId"),
                doubleValue(row, "minScore"),
                doubleValue(row, "maxScore"),
                stringValue(row, "termName"),
                stringValue(row, "className"),
                stringValue(row, "subjectName"),
                stringValue(row, "examTypeName"));
    }

    private void loadParentContext(ImportContext context) {
        validateCommonContext(context);
        accessControlService.ensureHeadTeacherOrAdmin();
        accessControlService.ensureCanManageHeadTeacherClass(context.classId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.grade_session AS gradeSession, c.grade_level AS gradeLevel "
                        + "FROM school_class c JOIN school_academic_term t ON t.id = ? AND t.status = 1 "
                        + "WHERE c.id = ? AND c.status = 1 AND c.is_deleted = 0",
                context.academicTermId, context.classId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("所选学期或班级无效");
        }
        validateClassCoordinates(context, rows.get(0));
    }

    private void validateCommonContext(ImportContext context) {
        if (context.academicTermId == null) throw new IllegalArgumentException("请选择学期");
        if (!StringUtils.hasText(context.gradeSession)) throw new IllegalArgumentException("请选择届次");
        if (context.gradeLevel == null) throw new IllegalArgumentException("请选择年级");
        if (context.classId == null) throw new IllegalArgumentException("请选择班级");
    }

    private void validateClassCoordinates(ImportContext context, Map<String, Object> row) {
        if (!context.gradeSession.equals(stringValue(row, "gradeSession"))
                || context.gradeLevel.intValue() != intValue(row, "gradeLevel")) {
            throw new IllegalArgumentException("届次、年级与所选班级不一致");
        }
    }

    private ParsedCourseImport parseCourseWorkbook(
            byte[] fileBytes, ImportContext context, CourseContext courseContext) {
        try (Workbook workbook = openWorkbook(fileBytes)) {
            CourseLayout layout = identifyCourseLayout(workbook);
            layout.expectedClassSubjectId = courseContext.classSubjectId;
            List<Map<String, Object>> errors = new ArrayList<Map<String, Object>>();
            if (layout.currentVersion) {
                validateCourseMetadata(layout.metadata, context, courseContext, errors);
            }
            Map<String, StudentRow> students = loadStudentsByNumber(context.classId);
            Map<Long, Long> existingResults = loadExistingCourseResults(
                    context.academicTermId, courseContext.classSubjectId, context.examTypeId);
            List<CourseImportRow> imports = new ArrayList<CourseImportRow>();
            Set<String> seenStudentNumbers = new HashSet<String>();
            int total = 0;
            int skipped = 0;
            int creates = 0;
            int updates = 0;
            for (int rowIndex = layout.firstDataRow;
                 rowIndex <= layout.sheet.getLastRowNum(); rowIndex++) {
                Row row = layout.sheet.getRow(rowIndex);
                if (row == null || rowIsEmpty(row, layout.maxRelevantColumn)) {
                    continue;
                }
                total++;
                String studentNumber = cellText(row, layout.studentNumberColumn);
                String studentName = cellText(row, layout.studentNameColumn);
                String scoreText = cellText(row, layout.scoreColumn);
                String performanceComment = cellText(row, layout.performanceColumn);
                String strengths = cellText(row, layout.strengthsColumn);
                String improvementPoints = cellText(row, layout.improvementColumn);
                boolean hasComments = hasText(performanceComment)
                        || hasText(strengths) || hasText(improvementPoints);
                if (!hasText(scoreText) && !hasComments) {
                    skipped++;
                    continue;
                }
                if (!hasText(studentNumber)) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName, "学号不能为空"));
                    continue;
                }
                if (!seenStudentNumbers.add(studentNumber)) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName, "同一学号在文件中出现多次"));
                    continue;
                }
                StudentRow student = students.get(studentNumber);
                if (student == null) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName, "学号不属于所选班级"));
                    continue;
                }
                if (!student.name.equals(studentName)) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName, "姓名与系统中的学号不匹配"));
                    continue;
                }
                if (!hasText(scoreText)) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName, "填写评语时成绩不能为空"));
                    continue;
                }
                Double score = parseScore(scoreText, rowIndex + 1, studentNumber, studentName, errors);
                if (score == null) {
                    continue;
                }
                if (score < courseContext.minScore || score > courseContext.maxScore) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName,
                            String.format(Locale.ROOT, "成绩必须在 %.2f 到 %.2f 之间",
                                    courseContext.minScore, courseContext.maxScore)));
                    continue;
                }
                if (!validateCommentLengths(rowIndex + 1, studentNumber, studentName,
                        performanceComment, strengths, improvementPoints, errors)) {
                    continue;
                }
                if (!layout.currentVersion) {
                    try {
                        if (!validateLegacyCourseRow(
                                row, rowIndex, layout, context, courseContext, student, errors)) {
                            continue;
                        }
                    } catch (IllegalArgumentException exception) {
                        errors.add(error(rowIndex + 1, studentNumber, studentName,
                                exception.getMessage()));
                        continue;
                    }
                }
                imports.add(new CourseImportRow(
                        rowIndex + 1, student.id, studentNumber, studentName, score,
                        nullIfBlank(performanceComment), nullIfBlank(strengths),
                        nullIfBlank(improvementPoints)));
                if (existingResults.containsKey(student.id)) {
                    updates++;
                } else {
                    creates++;
                }
            }
            return new ParsedCourseImport(
                    layout.templateVersion, total, skipped, creates, updates, imports, errors);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Excel 文件，请确认文件为有效的 .xlsx 文件", exception);
        }
    }

    private CourseLayout identifyCourseLayout(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("文件不是系统成绩模版");
        }
        if (workbook.getSheet("综合评价与荣誉") != null) {
            throw new IllegalArgumentException("模版类型不匹配，请从导入评语与荣誉入口上传");
        }
        Sheet metadataSheet = workbook.getSheet("模版说明");
        Sheet currentSheet = workbook.getSheet("成绩录入");
        if (metadataSheet != null || currentSheet != null) {
            if (metadataSheet == null || currentSheet == null) {
                throw new IllegalArgumentException("成绩模版结构不完整，请重新下载最新版模版");
            }
            Map<String, String> metadata = readMetadata(metadataSheet);
            if (!COURSE_TEMPLATE_TYPE.equals(metadata.get("template_type"))) {
                throw new IllegalArgumentException("模版类型不匹配，请从正确的导入入口上传");
            }
            if (!CURRENT_COURSE_VERSION.equals(metadata.get("template_version"))) {
                throw new IllegalArgumentException("不支持该成绩模版版本，请重新下载最新版模版");
            }
            requireCourseHeader(currentSheet, 0, 0, "学号");
            requireCourseHeader(currentSheet, 0, 1, "姓名");
            requireHeaderPrefix(currentSheet, 0, 2, "成绩");
            return CourseLayout.current(currentSheet, metadata);
        }
        Sheet legacySheet = workbook.getSheet("成绩模版");
        if (legacySheet == null) {
            legacySheet = workbook.getSheetAt(0);
        }
        requireCourseHeader(legacySheet, 0, 0, "学号");
        requireCourseHeader(legacySheet, 0, 1, "姓名");
        requireHeaderPrefix(legacySheet, 0, 6, "成绩");
        return CourseLayout.legacy(legacySheet);
    }

    private void validateCourseMetadata(
            Map<String, String> metadata,
            ImportContext context,
            CourseContext courseContext,
            List<Map<String, Object>> errors) {
        validateMetadata(metadata, "academic_term_id", String.valueOf(context.academicTermId), "学期", errors);
        validateMetadata(metadata, "grade_session", context.gradeSession, "届次", errors);
        validateMetadata(metadata, "grade_level", String.valueOf(context.gradeLevel), "年级", errors);
        validateMetadata(metadata, "class_id", String.valueOf(context.classId), "班级", errors);
        validateMetadata(metadata, "subject_id", String.valueOf(context.subjectId), "科目", errors);
        validateMetadata(metadata, "exam_type_id", String.valueOf(context.examTypeId), "考试类型", errors);
        validateMetadata(metadata, "academic_term_name", courseContext.termName, "学期名称", errors);
        validateMetadata(metadata, "class_name", courseContext.className, "班级名称", errors);
        validateMetadata(metadata, "subject_name", courseContext.subjectName, "科目名称", errors);
        validateMetadata(metadata, "exam_type_name", courseContext.examTypeName, "考试类型名称", errors);
    }

    private void validateMetadata(Map<String, String> metadata, String key, String expected,
                                  String label, List<Map<String, Object>> errors) {
        String actual = metadata.get(key);
        if (!Objects.equals(expected, actual)) {
            errors.add(error(0, "", "", "模版中的" + label + "与页面选择不一致"));
        }
    }

    private boolean validateLegacyCourseRow(
            Row row,
            int rowIndex,
            CourseLayout layout,
            ImportContext context,
            CourseContext courseContext,
            StudentRow student,
            List<Map<String, Object>> errors) {
        String termName = cellText(row, 2);
        String className = cellText(row, 3);
        String subjectName = cellText(row, 4);
        String examTypeName = cellText(row, 5);
        if (hasText(termName) && !courseContext.termName.equals(termName)) {
            errors.add(error(rowIndex + 1, student.number, student.name, "模版学期与页面选择不一致"));
            return false;
        }
        if (hasText(className) && !courseContext.className.equals(className)) {
            errors.add(error(rowIndex + 1, student.number, student.name, "模版班级与页面选择不一致"));
            return false;
        }
        if (hasText(subjectName) && !courseContext.subjectName.equals(subjectName)) {
            errors.add(error(rowIndex + 1, student.number, student.name, "模版科目与页面选择不一致"));
            return false;
        }
        if (hasText(examTypeName) && !courseContext.examTypeName.equals(examTypeName)) {
            errors.add(error(rowIndex + 1, student.number, student.name, "模版考试类型与页面选择不一致"));
            return false;
        }
        Long hiddenStudentId = optionalCellLong(row, 10);
        Long hiddenClassSubjectId = optionalCellLong(row, 11);
        Long hiddenExamTypeId = optionalCellLong(row, 12);
        if (hiddenStudentId != null && hiddenStudentId.longValue() != student.id) {
            errors.add(error(rowIndex + 1, student.number, student.name, "模版学生系统标识与学号不一致"));
            return false;
        }
        if (hiddenClassSubjectId != null
                && hiddenClassSubjectId.longValue() != layout.expectedClassSubjectId) {
            errors.add(error(rowIndex + 1, student.number, student.name, "模版班级课程与页面选择不一致"));
            return false;
        }
        if (hiddenExamTypeId != null && hiddenExamTypeId.longValue() != context.examTypeId) {
            errors.add(error(rowIndex + 1, student.number, student.name, "模版考试类型与页面选择不一致"));
            return false;
        }
        return true;
    }

    private ParsedParentImport parseParentWorkbook(
            byte[] fileBytes, ImportContext context, boolean enableAiPolish) {
        try (Workbook workbook = openWorkbook(fileBytes)) {
            Sheet sheet = identifyParentSheet(workbook);
            Map<String, StudentRow> students = loadStudentsByNumber(context.classId);
            Map<String, Long> honorTypes = loadHonorTypes();
            Map<Long, AchievementRow> achievements = loadAchievements(context);
            Set<Long> existingComments = loadExistingComments(context);
            String expectedTermName = querySingleString(
                    "SELECT term_name FROM school_academic_term WHERE id = ?", context.academicTermId);
            String expectedClassName = querySingleString(
                    "SELECT class_name FROM school_class WHERE id = ?", context.classId);
            Map<String, ParentAccumulator> grouped = new LinkedHashMap<String, ParentAccumulator>();
            List<Map<String, Object>> errors = new ArrayList<Map<String, Object>>();
            Set<Long> seenAchievementIds = new HashSet<Long>();
            int total = 0;
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || rowIsEmpty(row, 12)) {
                    continue;
                }
                total++;
                String studentNumber = mergedCellText(sheet, rowIndex, 0);
                String studentName = mergedCellText(sheet, rowIndex, 1);
                if (!hasText(studentNumber)) {
                    errors.add(error(rowIndex + 1, "", studentName, "学号不能为空"));
                    continue;
                }
                StudentRow student = students.get(studentNumber);
                if (student == null) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName, "学号不属于所选班级"));
                    continue;
                }
                if (!student.name.equals(studentName)) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName, "姓名与系统中的学号不匹配"));
                    continue;
                }
                try {
                    if (!validateLegacyParentContext(
                            sheet, row, rowIndex, context, student,
                            expectedTermName, expectedClassName, errors)) {
                        continue;
                    }
                    Long hiddenStudentId = optionalCellLong(row, 9);
                    if (hiddenStudentId != null && hiddenStudentId.longValue() != student.id) {
                        errors.add(error(rowIndex + 1, studentNumber, studentName,
                                "模版学生系统标识与学号不一致"));
                        continue;
                    }
                } catch (IllegalArgumentException exception) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName,
                            exception.getMessage()));
                    continue;
                }
                ParentAccumulator accumulator = grouped.get(studentNumber);
                if (accumulator == null) {
                    accumulator = new ParentAccumulator(
                            student,
                            mergedCellText(sheet, rowIndex, 4),
                            mergedCellText(sheet, rowIndex, 5),
                            mergedCellText(sheet, rowIndex, 6));
                    grouped.put(studentNumber, accumulator);
                }
                String honorTypeName = cellText(row, 7);
                String achievementText = cellText(row, 8);
                if (hasText(honorTypeName) != hasText(achievementText)) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName,
                            "荣誉类型和荣誉详细内容须同时填写"));
                    continue;
                }
                if (!hasText(honorTypeName)) {
                    continue;
                }
                Long honorTypeId = honorTypes.get(honorTypeName.toLowerCase(Locale.ROOT));
                if (honorTypeId == null) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName,
                            "荣誉类型 '" + honorTypeName + "' 不存在"));
                    continue;
                }
                Long achievementId;
                try {
                    achievementId = optionalCellLong(row, 12);
                } catch (IllegalArgumentException exception) {
                    errors.add(error(rowIndex + 1, studentNumber, studentName,
                            exception.getMessage()));
                    continue;
                }
                if (achievementId != null) {
                    AchievementRow existing = achievements.get(achievementId);
                    if (existing == null || existing.studentId != student.id) {
                        errors.add(error(rowIndex + 1, studentNumber, studentName,
                                "导入荣誉不存在或不属于当前学生和学期"));
                        continue;
                    }
                    if (!seenAchievementIds.add(achievementId)) {
                        errors.add(error(rowIndex + 1, studentNumber, studentName,
                                "同一荣誉记录在文件中出现多次"));
                        continue;
                    }
                } else {
                    achievementId = findMatchingAchievementId(
                            achievements, student.id, honorTypeId, achievementText,
                            seenAchievementIds);
                    if (achievementId != null) {
                        seenAchievementIds.add(achievementId);
                    }
                }
                accumulator.achievements.add(new ParentContentLifecycleService.AchievementImportItem(
                        achievementId, honorTypeId, achievementText));
            }

            List<ParentContentLifecycleService.ParentContentImportItem> items =
                    new ArrayList<ParentContentLifecycleService.ParentContentImportItem>();
            int skipped = 0;
            int creates = 0;
            int updates = 0;
            int aiPolished = 0;
            for (ParentAccumulator accumulator : grouped.values()) {
                boolean hasComment = hasText(accumulator.overallComment)
                        || hasText(accumulator.strengths) || hasText(accumulator.improvementPoints);
                if (!hasComment && accumulator.achievements.isEmpty()) {
                    skipped++;
                    continue;
                }
                if (!validateCommentLengths(
                        0, accumulator.student.number, accumulator.student.name,
                        accumulator.overallComment, accumulator.strengths,
                        accumulator.improvementPoints, errors)) {
                    continue;
                }
                String overallComment = accumulator.overallComment;
                String strengths = accumulator.strengths;
                String improvementPoints = accumulator.improvementPoints;
                if (enableAiPolish) {
                    if (hasText(overallComment)) {
                        overallComment = polish(overallComment);
                        aiPolished++;
                    }
                    if (hasText(strengths)) {
                        strengths = polish(strengths);
                        aiPolished++;
                    }
                    if (hasText(improvementPoints)) {
                        improvementPoints = polish(improvementPoints);
                        aiPolished++;
                    }
                }
                if (hasComment) {
                    if (existingComments.contains(accumulator.student.id)) updates++;
                    else creates++;
                }
                for (ParentContentLifecycleService.AchievementImportItem achievement
                        : accumulator.achievements) {
                    if (achievement.getId() == null) creates++;
                    else updates++;
                }
                items.add(new ParentContentLifecycleService.ParentContentImportItem(
                        context.academicTermId, context.classId, accumulator.student.id,
                        hasComment, nullIfBlank(overallComment), nullIfBlank(strengths),
                        nullIfBlank(improvementPoints), accumulator.achievements));
            }
            return new ParsedParentImport(
                    "1.0", total, skipped, creates, updates, aiPolished, items, errors);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Excel 文件，请确认文件为有效的 .xlsx 文件", exception);
        }
    }

    private Sheet identifyParentSheet(Workbook workbook) {
        if (workbook.getSheet("模版说明") != null || workbook.getSheet("成绩录入") != null
                || workbook.getSheet("成绩模版") != null) {
            throw new IllegalArgumentException("模版类型不匹配，请从导入成绩入口上传");
        }
        Sheet sheet = workbook.getSheet("综合评价与荣誉");
        if (sheet == null) {
            throw new IllegalArgumentException("文件不是系统评语与荣誉模版");
        }
        requireHeader(sheet, 0, 0, "学号");
        requireHeader(sheet, 0, 1, "姓名");
        requireHeader(sheet, 0, 4, "总体评价");
        return sheet;
    }

    private boolean validateLegacyParentContext(
            Sheet sheet,
            Row row,
            int rowIndex,
            ImportContext context,
            StudentRow student,
            String expectedTermName,
            String expectedClassName,
            List<Map<String, Object>> errors) {
        String termName = mergedCellText(sheet, rowIndex, 2);
        String className = mergedCellText(sheet, rowIndex, 3);
        if (hasText(termName) && !expectedTermName.equals(termName)) {
            errors.add(error(rowIndex + 1, student.number, student.name,
                    "模版学期与页面选择不一致"));
            return false;
        }
        if (hasText(className) && !expectedClassName.equals(className)) {
            errors.add(error(rowIndex + 1, student.number, student.name,
                    "模版班级与页面选择不一致"));
            return false;
        }
        Long hiddenTermId = optionalCellLong(row, 10);
        Long hiddenClassId = optionalCellLong(row, 11);
        if (hiddenTermId != null && hiddenTermId.longValue() != context.academicTermId) {
            errors.add(error(rowIndex + 1, student.number, student.name,
                    "模版学期系统标识与页面选择不一致"));
            return false;
        }
        if (hiddenClassId != null && hiddenClassId.longValue() != context.classId) {
            errors.add(error(rowIndex + 1, student.number, student.name,
                    "模版班级系统标识与页面选择不一致"));
            return false;
        }
        return true;
    }

    private int writeCourseRows(ImportSession session, long classSubjectId) {
        int written = 0;
        long evaluatorUserId = accessControlService.currentUserId();
        for (CourseImportRow row : session.courseRows) {
            accessControlService.ensureStudentBelongsToClass(row.studentId, session.context.classId);
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM school_student_course_result "
                            + "WHERE academic_term_id = ? AND class_subject_id = ? "
                            + "AND student_id = ? AND exam_type_id = ? FOR UPDATE",
                    session.context.academicTermId, classSubjectId,
                    row.studentId, session.context.examTypeId);
            if (existing.size() > 1) {
                throw new IllegalStateException("目标成绩存在重复数据，请先完成数据治理");
            }
            int affected;
            if (existing.isEmpty()) {
                affected = jdbcTemplate.update(
                        "INSERT INTO school_student_course_result "
                                + "(academic_term_id,class_subject_id,student_id,exam_type_id,score,"
                                + "performance_comment,strengths,improvement_points,evaluator_user_id,"
                                + "evaluated_at,status) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,1)",
                        session.context.academicTermId, classSubjectId, row.studentId,
                        session.context.examTypeId, row.score, row.performanceComment,
                        row.strengths, row.improvementPoints, evaluatorUserId);
                if (affected != 1) {
                    throw new IllegalStateException("第" + row.rowNumber + "行成绩新增失败");
                }
            } else {
                long resultId = longValue(existing.get(0), "id");
                affected = jdbcTemplate.update(
                        "UPDATE school_student_course_result SET score=?,performance_comment=?,"
                                + "strengths=?,improvement_points=?,evaluator_user_id=?,"
                                + "evaluated_at=CURRENT_TIMESTAMP,status=1,updated_at=CURRENT_TIMESTAMP "
                                + "WHERE id=?",
                        row.score, row.performanceComment, row.strengths,
                        row.improvementPoints, evaluatorUserId, resultId);
                if (affected < 0 || affected > 1) {
                    throw new IllegalStateException("第" + row.rowNumber + "行成绩更新失败");
                }
            }
            written++;
        }
        return written;
    }

    private Map<String, StudentRow> loadStudentsByNumber(long classId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, student_no AS studentNo, student_name AS studentName "
                        + "FROM school_student WHERE class_id = ? AND status = 1 AND is_deleted = 0 "
                        + "ORDER BY student_no, id",
                classId);
        Map<String, StudentRow> students = new LinkedHashMap<String, StudentRow>();
        for (Map<String, Object> row : rows) {
            StudentRow student = new StudentRow(
                    longValue(row, "id"), stringValue(row, "studentNo"),
                    stringValue(row, "studentName"));
            students.put(student.number, student);
        }
        return students;
    }

    private Map<Long, Long> loadExistingCourseResults(
            long academicTermId, long classSubjectId, long examTypeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, student_id AS studentId FROM school_student_course_result "
                        + "WHERE academic_term_id = ? AND class_subject_id = ? AND exam_type_id = ?",
                academicTermId, classSubjectId, examTypeId);
        Map<Long, Long> results = new HashMap<Long, Long>();
        for (Map<String, Object> row : rows) {
            long studentId = longValue(row, "studentId");
            if (results.put(studentId, longValue(row, "id")) != null) {
                throw new IllegalStateException("目标成绩存在重复数据，请先完成数据治理");
            }
        }
        return results;
    }

    private Map<String, Long> loadHonorTypes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, honor_type_name AS honorTypeName FROM school_honor_type WHERE status = 1");
        Map<String, Long> result = new HashMap<String, Long>();
        for (Map<String, Object> row : rows) {
            result.put(stringValue(row, "honorTypeName").toLowerCase(Locale.ROOT),
                    longValue(row, "id"));
        }
        return result;
    }

    private Map<Long, AchievementRow> loadAchievements(ImportContext context) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT a.id, a.student_id AS studentId,a.honor_type_id AS honorTypeId,"
                        + "a.achievement_text AS achievementText FROM school_student_achievement a "
                        + "JOIN school_student s ON s.id = a.student_id "
                        + "WHERE a.academic_term_id = ? AND s.class_id = ?",
                context.academicTermId, context.classId);
        Map<Long, AchievementRow> result = new HashMap<Long, AchievementRow>();
        for (Map<String, Object> row : rows) {
            long id = longValue(row, "id");
            Long honorTypeId = row.get("honorTypeId") instanceof Number
                    ? ((Number)row.get("honorTypeId")).longValue() : null;
            result.put(id, new AchievementRow(
                    id, longValue(row, "studentId"), honorTypeId,
                    stringValue(row, "achievementText")));
        }
        return result;
    }

    private Long findMatchingAchievementId(
            Map<Long, AchievementRow> achievements,
            long studentId,
            Long honorTypeId,
            String achievementText,
            Set<Long> claimedIds) {
        for (AchievementRow achievement : achievements.values()) {
            if (achievement.studentId == studentId
                    && !claimedIds.contains(achievement.id)
                    && Objects.equals(honorTypeId, achievement.honorTypeId)
                    && Objects.equals(achievementText, achievement.achievementText)) {
                return achievement.id;
            }
        }
        return null;
    }

    private Set<Long> loadExistingComments(ImportContext context) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT student_id AS studentId FROM school_student_overall_comment "
                        + "WHERE academic_term_id = ? AND class_id = ?",
                context.academicTermId, context.classId);
        Set<Long> result = new HashSet<Long>();
        for (Map<String, Object> row : rows) {
            result.add(longValue(row, "studentId"));
        }
        return result;
    }

    private String fingerprintCourseContext(ImportContext context, long classSubjectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id AS studentId,s.student_no AS studentNo,s.student_name AS studentName,"
                        + "s.updated_at AS studentUpdatedAt,r.id AS resultId,r.updated_at AS resultUpdatedAt "
                        + "FROM school_student s LEFT JOIN school_student_course_result r "
                        + "ON r.academic_term_id=? AND r.class_subject_id=? "
                        + "AND r.student_id=s.id AND r.exam_type_id=? "
                        + "WHERE s.class_id=? AND s.status=1 AND s.is_deleted=0 "
                        + "ORDER BY s.id,r.id",
                context.academicTermId, classSubjectId, context.examTypeId, context.classId);
        List<Map<String, Object>> config = jdbcTemplate.queryForList(
                "SELECT cs.updated_at AS classSubjectUpdatedAt,c.updated_at AS classUpdatedAt,"
                        + "t.updated_at AS termUpdatedAt,s.updated_at AS subjectUpdatedAt,"
                        + "e.updated_at AS examUpdatedAt,cs.teacher_user_id AS teacherUserId,"
                        + "s.min_score AS minScore,s.max_score AS maxScore "
                        + "FROM school_class_subject cs JOIN school_class c ON c.id=cs.class_id "
                        + "JOIN school_academic_term t ON t.id=cs.academic_term_id "
                        + "JOIN school_subject s ON s.id=cs.subject_id "
                        + "JOIN school_exam_type e ON e.id=? WHERE cs.id=?",
                context.examTypeId, classSubjectId);
        return sha256(serializeRows(config) + serializeRows(rows));
    }

    private String fingerprintParentContext(ImportContext context) {
        List<Map<String, Object>> roster = jdbcTemplate.queryForList(
                "SELECT s.id,s.student_no AS studentNo,s.student_name AS studentName,"
                        + "s.updated_at AS studentUpdatedAt,c.updated_at AS classUpdatedAt,"
                        + "t.updated_at AS termUpdatedAt FROM school_student s "
                        + "JOIN school_class c ON c.id=s.class_id "
                        + "JOIN school_academic_term t ON t.id=? "
                        + "WHERE s.class_id=? AND s.status=1 AND s.is_deleted=0 ORDER BY s.id",
                context.academicTermId, context.classId);
        List<Map<String, Object>> comments = jdbcTemplate.queryForList(
                "SELECT id,student_id AS studentId,updated_at AS updatedAt "
                        + "FROM school_student_overall_comment WHERE academic_term_id=? AND class_id=? "
                        + "ORDER BY id",
                context.academicTermId, context.classId);
        List<Map<String, Object>> achievements = jdbcTemplate.queryForList(
                "SELECT a.id,a.student_id AS studentId,a.honor_type_id AS honorTypeId,"
                        + "a.achievement_text AS achievementText,a.sort_order AS sortOrder,"
                        + "a.status,a.published_at AS publishedAt "
                        + "FROM school_student_achievement a JOIN school_student s ON s.id=a.student_id "
                        + "WHERE a.academic_term_id=? AND s.class_id=? ORDER BY a.id",
                context.academicTermId, context.classId);
        List<Map<String, Object>> honorTypes = jdbcTemplate.queryForList(
                "SELECT id,honor_type_name AS honorTypeName,status,updated_at AS updatedAt "
                        + "FROM school_honor_type ORDER BY id");
        return sha256(serializeRows(roster) + serializeRows(comments)
                + serializeRows(achievements) + serializeRows(honorTypes));
    }

    private String serializeRows(List<Map<String, Object>> rows) {
        StringBuilder value = new StringBuilder();
        for (Map<String, Object> row : rows) {
            List<String> keys = new ArrayList<String>(row.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                value.append(key).append('=').append(Objects.toString(row.get(key), "<null>"));
                value.append('|');
            }
            value.append('\n');
        }
        return value.toString();
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Excel 文件不能超过 10MB");
        }
        String fileName = Objects.toString(file.getOriginalFilename(), "");
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .xlsx 格式的 Excel 文件");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Excel 文件", exception);
        }
    }

    private Workbook openWorkbook(byte[] fileBytes) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(fileBytes));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "无法读取 Excel 文件，请确认文件为有效的 .xlsx 文件", exception);
        }
    }

    private String storeSession(ImportSession session) {
        cleanupExpiredSessions();
        if (importSessions.size() >= MAX_ACTIVE_TOKENS) {
            throw new IllegalStateException("当前待提交导入任务过多，请稍后重试");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        importSessions.put(token, session);
        return token;
    }

    private ImportSession consumeSession(String token, ImportType expectedType) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("importToken 不能为空");
        }
        ImportSession session = importSessions.remove(token.trim());
        if (session == null) {
            throw new IllegalArgumentException("导入令牌无效或已使用，请重新预校验");
        }
        if (session.expiresAt < System.currentTimeMillis()) {
            throw new IllegalArgumentException("导入令牌已过期，请重新预校验");
        }
        if (session.type != expectedType) {
            throw new IllegalArgumentException("导入令牌类型不匹配，请重新预校验");
        }
        if (session.userId != accessControlService.currentUserId()
                || !session.sessionToken.equals(currentSessionToken())) {
            throw new IllegalArgumentException("导入令牌与当前登录会话不匹配，请重新预校验");
        }
        return session;
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ImportSession> entry : importSessions.entrySet()) {
            if (entry.getValue().expiresAt < now) {
                importSessions.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private String currentSessionToken() {
        String token = Objects.toString(accessControlService.currentSession().get("token"), "");
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("当前登录会话缺少令牌");
        }
        return token;
    }

    private Map<String, Object> precheckResponse(
            String token,
            long expiresAt,
            String templateType,
            String templateVersion,
            int total,
            int creates,
            int updates,
            int skipped,
            List<Map<String, Object>> errors) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("importToken", token);
        result.put("expiresAt", ZonedDateTime.now().plusNanos(
                Math.max(0L, expiresAt - System.currentTimeMillis()) * 1000000L).toString());
        result.put("templateType", templateType);
        result.put("templateVersion", templateVersion);
        result.put("total", total);
        result.put("creates", creates);
        result.put("updates", updates);
        result.put("skipped", skipped);
        result.put("errorCount", errors.size());
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }

    private Map<String, String> readMetadata(Sheet sheet) {
        Map<String, String> metadata = new HashMap<String, String>();
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String key = cellText(row, 0);
            if (hasText(key)) {
                metadata.put(key, cellText(row, 1));
            }
        }
        return metadata;
    }

    private void requireHeader(Sheet sheet, int rowIndex, int columnIndex, String expected) {
        Row row = sheet.getRow(rowIndex);
        String actual = row == null ? "" : cellText(row, columnIndex);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("文件不是系统模版，缺少列：“" + expected + "”");
        }
    }

    private void requireCourseHeader(
            Sheet sheet, int rowIndex, int columnIndex, String expected) {
        Row row = sheet.getRow(rowIndex);
        String actual = row == null ? "" : cellText(row, columnIndex);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "文件不是系统成绩模版，缺少列：‘" + expected + "’");
        }
    }

    private void requireHeaderPrefix(Sheet sheet, int rowIndex, int columnIndex, String expectedPrefix) {
        Row row = sheet.getRow(rowIndex);
        String actual = row == null ? "" : cellText(row, columnIndex);
        if (!actual.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("文件不是系统成绩模版，缺少成绩列");
        }
    }

    private boolean validateCommentLengths(
            int rowNumber,
            String studentNumber,
            String studentName,
            String performanceComment,
            String strengths,
            String improvementPoints,
            List<Map<String, Object>> errors) {
        if (performanceComment.length() > COMMENT_MAX_LENGTH
                || strengths.length() > COMMENT_MAX_LENGTH
                || improvementPoints.length() > COMMENT_MAX_LENGTH) {
            errors.add(error(rowNumber, studentNumber, studentName, "评语字段不能超过1000字"));
            return false;
        }
        return true;
    }

    private Double parseScore(
            String value,
            int rowNumber,
            String studentNumber,
            String studentName,
            List<Map<String, Object>> errors) {
        try {
            double score = Double.parseDouble(value);
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                throw new NumberFormatException("not finite");
            }
            return score;
        } catch (NumberFormatException exception) {
            errors.add(error(rowNumber, studentNumber, studentName, "成绩必须是有效数字"));
            return null;
        }
    }

    private String mergedCellText(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        String direct = row == null ? "" : cellText(row, columnIndex);
        if (hasText(direct)) return direct;
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIndex, columnIndex)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                return firstRow == null ? "" : cellText(firstRow, region.getFirstColumn());
            }
        }
        return "";
    }

    private String cellText(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return "";
        return dataFormatter.get().formatCellValue(cell).trim();
    }

    private Long optionalCellLong(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        String value = dataFormatter.get().formatCellValue(cell).trim();
        if (!hasText(value)) return null;
        try {
            return Long.valueOf(value.replaceAll("\\.0+$", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("系统标识列格式错误");
        }
    }

    private boolean rowIsEmpty(Row row, int maxColumn) {
        for (int column = 0; column <= maxColumn; column++) {
            if (hasText(cellText(row, column))) return false;
        }
        return true;
    }

    private Map<String, Object> error(
            int rowNumber, String studentNumber, String studentName, String reason) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("row", rowNumber);
        error.put("studentNo", Objects.toString(studentNumber, ""));
        error.put("studentName", Objects.toString(studentName, ""));
        error.put("reason", reason);
        return error;
    }

    private String polish(String text) {
        try {
            return commentPolishService.polish(text);
        } catch (RuntimeException exception) {
            log.warn("Failed to polish imported comment; original text retained");
            return text;
        }
    }

    private String querySingleString(String sql, Object argument) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, argument);
        if (rows.size() != 1 || rows.get(0).isEmpty()) return "";
        return Objects.toString(rows.get(0).values().iterator().next(), "");
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value);
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
        }
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullIfBlank(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Missing numeric field: " + key);
        }
        return ((Number)value).longValue();
    }

    private int intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Missing numeric field: " + key);
        }
        return ((Number)value).intValue();
    }

    private double doubleValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Missing numeric field: " + key);
        }
        return ((Number)value).doubleValue();
    }

    private String stringValue(Map<String, Object> row, String key) {
        return Objects.toString(row.get(key), "");
    }

    private enum ImportType {
        COURSE_RESULT,
        PARENT_CONTENT
    }

    private static final class ImportContext {
        private final Long academicTermId;
        private final String gradeSession;
        private final Integer gradeLevel;
        private final Long classId;
        private final Long subjectId;
        private final Long examTypeId;

        private ImportContext(Long academicTermId, String gradeSession, Integer gradeLevel,
                              Long classId, Long subjectId, Long examTypeId) {
            this.academicTermId = academicTermId;
            this.gradeSession = gradeSession == null ? null : gradeSession.trim();
            this.gradeLevel = gradeLevel;
            this.classId = classId;
            this.subjectId = subjectId;
            this.examTypeId = examTypeId;
        }

        private static ImportContext course(Long termId, String session, Integer level,
                                            Long classId, Long subjectId, Long examTypeId) {
            return new ImportContext(termId, session, level, classId, subjectId, examTypeId);
        }

        private static ImportContext parent(Long termId, String session,
                                            Integer level, Long classId) {
            return new ImportContext(termId, session, level, classId, null, null);
        }
    }

    private static final class CourseContext {
        private final long classSubjectId;
        private final double minScore;
        private final double maxScore;
        private final String termName;
        private final String className;
        private final String subjectName;
        private final String examTypeName;

        private CourseContext(long classSubjectId, double minScore, double maxScore,
                              String termName, String className,
                              String subjectName, String examTypeName) {
            this.classSubjectId = classSubjectId;
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.termName = termName;
            this.className = className;
            this.subjectName = subjectName;
            this.examTypeName = examTypeName;
        }
    }

    private static final class CourseLayout {
        private final Sheet sheet;
        private final boolean currentVersion;
        private final String templateVersion;
        private final Map<String, String> metadata;
        private final int firstDataRow;
        private final int studentNumberColumn;
        private final int studentNameColumn;
        private final int scoreColumn;
        private final int performanceColumn;
        private final int strengthsColumn;
        private final int improvementColumn;
        private final int maxRelevantColumn;
        private long expectedClassSubjectId;

        private CourseLayout(Sheet sheet, boolean currentVersion, String templateVersion,
                             Map<String, String> metadata, int firstDataRow,
                             int studentNumberColumn, int studentNameColumn, int scoreColumn,
                             int performanceColumn, int strengthsColumn, int improvementColumn,
                             int maxRelevantColumn) {
            this.sheet = sheet;
            this.currentVersion = currentVersion;
            this.templateVersion = templateVersion;
            this.metadata = metadata;
            this.firstDataRow = firstDataRow;
            this.studentNumberColumn = studentNumberColumn;
            this.studentNameColumn = studentNameColumn;
            this.scoreColumn = scoreColumn;
            this.performanceColumn = performanceColumn;
            this.strengthsColumn = strengthsColumn;
            this.improvementColumn = improvementColumn;
            this.maxRelevantColumn = maxRelevantColumn;
        }

        private static CourseLayout current(Sheet sheet, Map<String, String> metadata) {
            return new CourseLayout(sheet, true, CURRENT_COURSE_VERSION, metadata,
                    1, 0, 1, 2, 3, 4, 5, 5);
        }

        private static CourseLayout legacy(Sheet sheet) {
            return new CourseLayout(sheet, false, "1.0", Collections.emptyMap(),
                    2, 0, 1, 6, 7, 8, 9, 12);
        }
    }

    private static final class StudentRow {
        private final long id;
        private final String number;
        private final String name;

        private StudentRow(long id, String number, String name) {
            this.id = id;
            this.number = number;
            this.name = name;
        }
    }

    private static final class CourseImportRow {
        private final int rowNumber;
        private final long studentId;
        private final String studentNumber;
        private final String studentName;
        private final double score;
        private final String performanceComment;
        private final String strengths;
        private final String improvementPoints;

        private CourseImportRow(int rowNumber, long studentId, String studentNumber,
                                String studentName, double score, String performanceComment,
                                String strengths, String improvementPoints) {
            this.rowNumber = rowNumber;
            this.studentId = studentId;
            this.studentNumber = studentNumber;
            this.studentName = studentName;
            this.score = score;
            this.performanceComment = performanceComment;
            this.strengths = strengths;
            this.improvementPoints = improvementPoints;
        }
    }

    private static final class AchievementRow {
        private final long id;
        private final long studentId;
        private final Long honorTypeId;
        private final String achievementText;

        private AchievementRow(long id, long studentId, Long honorTypeId,
                               String achievementText) {
            this.id = id;
            this.studentId = studentId;
            this.honorTypeId = honorTypeId;
            this.achievementText = achievementText;
        }
    }

    private static final class ParentAccumulator {
        private final StudentRow student;
        private final String overallComment;
        private final String strengths;
        private final String improvementPoints;
        private final List<ParentContentLifecycleService.AchievementImportItem> achievements =
                new ArrayList<ParentContentLifecycleService.AchievementImportItem>();

        private ParentAccumulator(StudentRow student, String overallComment,
                                  String strengths, String improvementPoints) {
            this.student = student;
            this.overallComment = overallComment;
            this.strengths = strengths;
            this.improvementPoints = improvementPoints;
        }
    }

    private static final class ParsedCourseImport {
        private final String templateVersion;
        private final int total;
        private final int skipped;
        private final int creates;
        private final int updates;
        private final List<CourseImportRow> rows;
        private final List<Map<String, Object>> errors;

        private ParsedCourseImport(String templateVersion, int total, int skipped,
                                   int creates, int updates, List<CourseImportRow> rows,
                                   List<Map<String, Object>> errors) {
            this.templateVersion = templateVersion;
            this.total = total;
            this.skipped = skipped;
            this.creates = creates;
            this.updates = updates;
            this.rows = rows;
            this.errors = errors;
        }
    }

    private static final class ParsedParentImport {
        private final String templateVersion;
        private final int total;
        private final int skipped;
        private final int creates;
        private final int updates;
        private final int aiPolished;
        private final List<ParentContentLifecycleService.ParentContentImportItem> items;
        private final List<Map<String, Object>> errors;

        private ParsedParentImport(String templateVersion, int total, int skipped,
                                   int creates, int updates, int aiPolished,
                                   List<ParentContentLifecycleService.ParentContentImportItem> items,
                                   List<Map<String, Object>> errors) {
            this.templateVersion = templateVersion;
            this.total = total;
            this.skipped = skipped;
            this.creates = creates;
            this.updates = updates;
            this.aiPolished = aiPolished;
            this.items = items;
            this.errors = errors;
        }
    }

    private static final class ImportSession {
        private final ImportType type;
        private final String sessionToken;
        private final long userId;
        private final long expiresAt;
        private final ImportContext context;
        private final String templateVersion;
        private final String fileDigest;
        private final String dataVersion;
        private final List<CourseImportRow> courseRows;
        private final List<ParentContentLifecycleService.ParentContentImportItem> parentItems;
        private final int total;
        private final int skipped;
        private final int creates;
        private final int updates;
        private final int aiPolished;

        private ImportSession(
                ImportType type, String sessionToken, long userId, long expiresAt,
                ImportContext context, String templateVersion, String fileDigest,
                String dataVersion, List<CourseImportRow> courseRows,
                List<ParentContentLifecycleService.ParentContentImportItem> parentItems,
                int total, int skipped, int creates, int updates, int aiPolished) {
            this.type = type;
            this.sessionToken = sessionToken;
            this.userId = userId;
            this.expiresAt = expiresAt;
            this.context = context;
            this.templateVersion = templateVersion;
            this.fileDigest = fileDigest;
            this.dataVersion = dataVersion;
            this.courseRows = courseRows;
            this.parentItems = parentItems;
            this.total = total;
            this.skipped = skipped;
            this.creates = creates;
            this.updates = updates;
            this.aiPolished = aiPolished;
        }

        private static ImportSession course(
                String sessionToken, long userId, long expiresAt, ImportContext context,
                String templateVersion, String fileDigest, String dataVersion,
                List<CourseImportRow> rows, int total, int skipped, int creates, int updates) {
            return new ImportSession(
                    ImportType.COURSE_RESULT, sessionToken, userId, expiresAt, context,
                    templateVersion, fileDigest, dataVersion,
                    Collections.unmodifiableList(new ArrayList<CourseImportRow>(rows)),
                    Collections.emptyList(), total, skipped, creates, updates, 0);
        }

        private static ImportSession parent(
                String sessionToken, long userId, long expiresAt, ImportContext context,
                String templateVersion, String fileDigest, String dataVersion,
                List<ParentContentLifecycleService.ParentContentImportItem> items,
                int total, int skipped, int creates, int updates, int aiPolished) {
            return new ImportSession(
                    ImportType.PARENT_CONTENT, sessionToken, userId, expiresAt, context,
                    templateVersion, fileDigest, dataVersion, Collections.emptyList(),
                    Collections.unmodifiableList(
                            new ArrayList<ParentContentLifecycleService.ParentContentImportItem>(items)),
                    total, skipped, creates, updates, aiPolished);
        }
    }
}
