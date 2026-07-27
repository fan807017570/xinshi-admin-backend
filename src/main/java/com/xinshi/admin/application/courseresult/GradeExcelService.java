package com.xinshi.admin.application.courseresult;

import com.xinshi.admin.application.commentpolish.CommentPolishService;
import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GradeExcelService extends SchoolBaseService {

    private static final Logger log = LoggerFactory.getLogger(GradeExcelService.class);
    private static final String EXCEL_OUTPUT_DIR = "/tmp/xinshi-excel";
    private static final short ROW_HEIGHT = 420; // 行高（单位：1/20 磅，420≈21pt）
    private static final short HEADER_ROW_HEIGHT = 500;

    private final CommentPolishService commentPolishService;
    private final AccessControlService accessControlService;

    public GradeExcelService(JdbcTemplate jdbcTemplate,
                             CommentPolishService commentPolishService,
                             AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.commentPolishService = commentPolishService;
        this.accessControlService = accessControlService;
    }

    // ==================== 场景 A：单科成绩导出 ====================

    public Path exportCourseResultTemplate(long academicTermId, long classId,
                                           long subjectId, Long examTypeId) {
        accessControlService.ensureTeacherCanWriteResults();
        // 任课老师只能导出自己任教课程的模版，班主任/管理员可导出班级全部课程
        ensureCanExportSubject(academicTermId, classId, subjectId);

        // 查询科目信息
        List<Map<String, Object>> subjects = jdbcTemplate.queryForList(
            "SELECT subject_name AS subjectName, min_score AS minScore, max_score AS maxScore " +
            "FROM school_subject WHERE id = ?", subjectId);
        if (subjects.isEmpty()) throw new IllegalArgumentException("科目不存在");
        String subjectName = (String) subjects.get(0).get("subjectName");
        double minScore = ((Number) subjects.get(0).get("minScore")).doubleValue();
        double maxScore = ((Number) subjects.get(0).get("maxScore")).doubleValue();

        String termName = getTermName(academicTermId);
        String className = getClassName(classId);
        String examTypeName = examTypeId != null ? getExamTypeName(examTypeId) : "";

        List<Map<String, Object>> csList = jdbcTemplate.queryForList(
            "SELECT id FROM school_class_subject " +
            "WHERE academic_term_id = ? AND class_id = ? AND subject_id = ? AND status = 1",
            academicTermId, classId, subjectId);
        if (csList.isEmpty()) throw new IllegalArgumentException("该班级未配置此科目，请先在课程管理中为班级添加科目");
        long classSubjectId = ((Number) csList.get(0).get("id")).longValue();

        // 查询考试类型列表（用于下拉）
        List<String> examTypeNames = getExamTypeNames();

        // 查询学生及已有成绩（子查询确保一个学生最多匹配一条记录）
        String sql = "SELECT s.id AS studentId, s.student_no AS studentNo, " +
            "s.student_name AS studentName, r.score, r.performance_comment AS performanceComment, " +
            "r.strengths, r.improvement_points AS improvementPoints, r.exam_type_id AS examTypeId " +
            "FROM school_class_subject cs " +
            "JOIN school_student s ON s.class_id = cs.class_id AND s.is_deleted = 0 AND s.status = 1 " +
            "LEFT JOIN school_student_course_result r ON r.id = ( " +
            "  SELECT r2.id FROM school_student_course_result r2 " +
            "  WHERE r2.academic_term_id = cs.academic_term_id " +
            "  AND r2.class_subject_id = cs.id AND r2.student_id = s.id " +
            "  AND (? IS NULL OR r2.exam_type_id = ?) " +
            "  ORDER BY r2.updated_at DESC LIMIT 1 " +
            ") " +
            "WHERE cs.id = ? ORDER BY s.student_no";
        List<Map<String, Object>> students = jdbcTemplate.queryForList(sql, examTypeId, examTypeId, classSubjectId);

        try {
            Files.createDirectories(Paths.get(EXCEL_OUTPUT_DIR));
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("成绩模版");
            sheet.setDefaultRowHeight(ROW_HEIGHT);

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle hintStyle = createHintStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);
            CellStyle numberStyle = createNumberStyle(wb);

            // 第 1 行：标题
            Row titleRow = sheet.createRow(0);
            titleRow.setHeight(HEADER_ROW_HEIGHT);
            String[] titles = {"学号", "姓名", "学期", "班级", "科目", "考试类型（下拉选择）",
                "成绩（" + String.format("%.2f", minScore) + "-" + String.format("%.2f", maxScore) + "）",
                "课程表现", "优点", "改进点"};
            for (int i = 0; i < titles.length; i++) {
                Cell cell = titleRow.createCell(i);
                cell.setCellValue(titles[i]);
                cell.setCellStyle(headerStyle);
            }

            // 第 2 行：说明
            Row hintRow = sheet.createRow(1);
            Cell hintCell = hintRow.createCell(0);
            hintCell.setCellValue("A-E 列为系统预填，请勿修改。F 列考试类型请从下拉选择。请在 G-J 列填写成绩和评语。K-M 列为系统数据已隐藏。");
            hintCell.setCellStyle(hintStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));

            // 数据行
            int rowNum = 2;
            for (Map<String, Object> s : students) {
                Row row = sheet.createRow(rowNum);
                row.setHeight(ROW_HEIGHT);
                setCell(row, 0, strVal(s, "studentNo"), dataStyle);
                setCell(row, 1, strVal(s, "studentName"), dataStyle);
                setCell(row, 2, termName, dataStyle);
                setCell(row, 3, className, dataStyle);
                setCell(row, 4, subjectName, dataStyle);
                // F 列：优先用已有成绩的考试类型名称，否则用筛选条件的考试类型
                String rowExamTypeName = examTypeName;
                Long rowExamTypeId = examTypeId;
                if (s.get("examTypeId") != null) {
                    rowExamTypeId = ((Number) s.get("examTypeId")).longValue();
                    rowExamTypeName = getExamTypeName(rowExamTypeId);
                }
                setCell(row, 5, rowExamTypeName, dataStyle);
                if (s.get("score") != null) {
                    Cell sc = row.createCell(6);
                    sc.setCellValue(((Number) s.get("score")).doubleValue());
                    sc.setCellStyle(numberStyle);
                } else {
                    row.createCell(6).setCellStyle(numberStyle);
                }
                setCell(row, 7, strVal(s, "performanceComment"), dataStyle);
                setCell(row, 8, strVal(s, "strengths"), dataStyle);
                setCell(row, 9, strVal(s, "improvementPoints"), dataStyle);
                Cell sid = row.createCell(10); sid.setCellValue(((Number) s.get("studentId")).longValue());
                Cell cid = row.createCell(11); cid.setCellValue(classSubjectId);
                Cell eid = row.createCell(12);
                if (rowExamTypeId != null) eid.setCellValue(rowExamTypeId);
                rowNum++;
            }

            // 考试类型下拉（F列，从第3行到数据末尾）
            if (!examTypeNames.isEmpty()) {
                addDropdownValidation(sheet, examTypeNames, 5, 2, rowNum - 1);
            }

            // 列宽
            sheet.setColumnWidth(0, 14 * 256); sheet.setColumnWidth(1, 14 * 256);
            sheet.setColumnWidth(2, 20 * 256); sheet.setColumnWidth(3, 16 * 256);
            sheet.setColumnWidth(4, 14 * 256); sheet.setColumnWidth(5, 18 * 256);
            sheet.setColumnWidth(6, 18 * 256); sheet.setColumnWidth(7, 32 * 256);
            sheet.setColumnWidth(8, 32 * 256); sheet.setColumnWidth(9, 32 * 256);

            sheet.setColumnHidden(10, true); sheet.setColumnHidden(11, true); sheet.setColumnHidden(12, true);
            sheet.createFreezePane(0, 2);

            String fileName = "成绩模版_" + className + "_" + subjectName + ".xlsx";
            Path filePath = Paths.get(EXCEL_OUTPUT_DIR, fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) { wb.write(fos); }
            wb.close();
            log.info("导出单科成绩模版: {}", filePath.toAbsolutePath());
            return filePath;
        } catch (IOException e) {
            log.error("导出单科成绩模版失败", e);
            throw new IllegalStateException("导出模版失败: " + e.getMessage(), e);
        }
    }

    // ==================== 场景 A：单科成绩导入 ====================

    public Map<String, Object> importCourseResults(MultipartFile file, long evaluatorUserId, boolean enableAiPolish) {
        accessControlService.ensureTeacherCanWriteResults();
        int total = 0, success = 0, skipped = 0, failed = 0;
        // 单科成绩不进行 AI 润色，仅综合评价场景支持
        List<Map<String, String>> errors = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    total++;
                    Double score = getNumericCellValue(row, 6);
                    String perf = getStringCellValue(row, 7);
                    String strengths = getStringCellValue(row, 8);
                    String improv = getStringCellValue(row, 9);
                    boolean hasComment = !isEmpty(perf) || !isEmpty(strengths) || !isEmpty(improv);
                    if (score == null && hasComment) { failed++; errors.add(createError(i + 1, getCellString(row, 1), "成绩不能为空")); continue; }
                    if (score == null) { skipped++; continue; }

                    Long studentId = getLongCellValue(row, 10);
                    Long classSubjectId = getLongCellValue(row, 11);
                    // 优先从 F 列（下拉选择）读取考试类型名称并转 ID，为空则回退到隐藏列 M
                    Long etId = resolveExamTypeId(getStringCellValue(row, 5), getLongCellValue(row, 12));
                    if (studentId == null || classSubjectId == null) { failed++; errors.add(createError(i + 1, getCellString(row, 1), "模版格式错误，缺少系统数据列")); continue; }

                    validateSubjectScore(score, classSubjectId);
                    upsertCourseResult(academicTermIdFromClassSubject(classSubjectId), classSubjectId, studentId, etId, score, perf, strengths, improv, evaluatorUserId);
                    success++;
                } catch (Exception e) { failed++; errors.add(createError(i + 1, getCellString(row, 1), e.getMessage())); }
            }
        } catch (IOException e) { throw new IllegalArgumentException("无法读取 Excel 文件，请确认文件格式正确", e); }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total); result.put("success", success); result.put("skipped", skipped);
        result.put("failed", failed); result.put("aiPolished", 0); result.put("errors", errors);
        return result;
    }

    // ==================== 场景 B：综合评价+荣誉导出（固定2行/学生，合并单元格，荣誉下拉） ====================

    public Path exportHeadTeacherTemplate(long academicTermId, long classId) {
        accessControlService.ensureHeadTeacherOrAdmin();

        String termName = getTermName(academicTermId);
        String className = getClassName(classId);

        List<Map<String, Object>> students = jdbcTemplate.queryForList(
            "SELECT id AS studentId, student_no AS studentNo, student_name AS studentName " +
            "FROM school_student WHERE class_id = ? AND is_deleted = 0 AND status = 1 ORDER BY student_no", classId);

        Map<Long, Map<String, Object>> commentMap = new HashMap<>();
        jdbcTemplate.queryForList(
            "SELECT student_id AS studentId, overall_comment AS overallComment, strengths, improvement_points AS improvementPoints " +
            "FROM school_student_overall_comment WHERE academic_term_id = ? AND class_id = ?", academicTermId, classId)
            .forEach(c -> commentMap.put(((Number) c.get("studentId")).longValue(), c));

        Map<Long, List<Map<String, Object>>> achievementMap = new HashMap<>();
        jdbcTemplate.queryForList(
            "SELECT a.id AS achievementId, a.student_id AS studentId, a.achievement_text AS achievementText, " +
            "ht.honor_type_name AS honorTypeName, a.honor_type_id AS honorTypeId " +
            "FROM school_student_achievement a LEFT JOIN school_honor_type ht ON ht.id = a.honor_type_id " +
            "WHERE a.academic_term_id = ? AND a.student_id IN " +
            "(SELECT id FROM school_student WHERE class_id = ? AND is_deleted = 0 AND status = 1) " +
            "ORDER BY a.student_id, a.sort_order, a.id", academicTermId, classId)
            .forEach(a -> achievementMap.computeIfAbsent(((Number) a.get("studentId")).longValue(), k -> new ArrayList<>()).add(a));

        // 荣誉类型下拉列表
        List<String> honorTypeNames = getHonorTypeNames();

        try {
            Files.createDirectories(Paths.get(EXCEL_OUTPUT_DIR));
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("综合评价与荣誉");
            sheet.setDefaultRowHeight(ROW_HEIGHT);

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle hintStyle = createHintStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);
            CellStyle wrapStyle = createWrapStyle(wb);
            CellStyle mergedStyle = createMergedCellStyle(wb);

            // 第 1 行：标题
            Row titleRow = sheet.createRow(0);
            titleRow.setHeight(HEADER_ROW_HEIGHT);
            String[] titles = {"学号", "姓名", "学期", "班级", "总体评价", "优点", "改进点", "荣誉类型（下拉选择）", "荣誉详细内容"};
            for (int i = 0; i < titles.length; i++) {
                Cell cell = titleRow.createCell(i);
                cell.setCellValue(titles[i]);
                cell.setCellStyle(headerStyle);
            }

            // 第 2 行：说明
            Row hintRow = sheet.createRow(1);
            Cell hintCell = hintRow.createCell(0);
            hintCell.setCellValue("A-D 列系统预填，请勿修改。E-G 列填写综合评语。每个学生固定 2 行荣誉（H-I 列），H 列荣誉类型请从下拉选择。如需更多荣誉请复制行。J-M 列隐藏。");
            hintCell.setCellStyle(hintStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));

            int rowNum = 2;
            for (Map<String, Object> s : students) {
                long studentId = ((Number) s.get("studentId")).longValue();
                Map<String, Object> comment = commentMap.get(studentId);
                List<Map<String, Object>> achievements = achievementMap.getOrDefault(studentId, Collections.emptyList());

                // 固定 2 行
                int startRow = rowNum;
                int endRow = rowNum + 1; // 2行

                for (int r = 0; r < 2; r++) {
                    Row row = sheet.createRow(rowNum);
                    row.setHeight(ROW_HEIGHT);

                    if (r == 0) {
                        setCell(row, 0, strVal(s, "studentNo"), dataStyle);
                        setCell(row, 1, strVal(s, "studentName"), dataStyle);
                        setCell(row, 2, termName, dataStyle);
                        setCell(row, 3, className, dataStyle);
                        setCell(row, 4, comment != null ? strVal(comment, "overallComment") : "", wrapStyle);
                        setCell(row, 5, comment != null ? strVal(comment, "strengths") : "", wrapStyle);
                        setCell(row, 6, comment != null ? strVal(comment, "improvementPoints") : "", wrapStyle);
                    } else {
                        // 合并区域的第二行：保持边框
                        for (int c = 0; c < 9; c++) {
                            Cell cell = row.createCell(c);
                            cell.setCellStyle(mergedStyle);
                        }
                    }

                    // 荣誉（第 r 个）
                    if (r < achievements.size()) {
                        Map<String, Object> ach = achievements.get(r);
                        setCell(row, 7, strVal(ach, "honorTypeName"), dataStyle);
                        setCell(row, 8, strVal(ach, "achievementText"), wrapStyle);
                        Cell achId = row.createCell(12);
                        achId.setCellValue(((Number) ach.get("achievementId")).longValue());
                        achId.setCellStyle(mergedStyle);
                    }

                    // 隐藏列（统一加边框）
                    Cell sidCell = row.createCell(9); sidCell.setCellValue(studentId); sidCell.setCellStyle(mergedStyle);
                    Cell tidCell = row.createCell(10); tidCell.setCellValue(academicTermId); tidCell.setCellStyle(mergedStyle);
                    Cell cidCell = row.createCell(11); cidCell.setCellValue(classId); cidCell.setCellStyle(mergedStyle);
                    if (r >= achievements.size()) {
                        Cell achCell = row.createCell(12); achCell.setCellStyle(mergedStyle);
                    }

                    rowNum++;
                }

                // 合并 A-D 列 + E-G 列（跨 2 行）
                for (int col = 0; col < 4; col++)
                    sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, col, col));
                for (int col = 4; col < 7; col++)
                    sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, col, col));
            }

            // 荣誉类型下拉（H列，从第3行到数据末尾）
            if (!honorTypeNames.isEmpty()) {
                addDropdownValidation(sheet, honorTypeNames, 7, 2, rowNum - 1);
            }

            // 列宽
            sheet.setColumnWidth(0, 14 * 256); sheet.setColumnWidth(1, 14 * 256);
            sheet.setColumnWidth(2, 20 * 256); sheet.setColumnWidth(3, 16 * 256);
            sheet.setColumnWidth(4, 36 * 256); sheet.setColumnWidth(5, 32 * 256);
            sheet.setColumnWidth(6, 32 * 256); sheet.setColumnWidth(7, 16 * 256);
            sheet.setColumnWidth(8, 36 * 256);

            for (int col = 9; col <= 12; col++) sheet.setColumnHidden(col, true);
            sheet.createFreezePane(0, 2);

            String fileName = "综合评价与荣誉_" + className + ".xlsx";
            Path filePath = Paths.get(EXCEL_OUTPUT_DIR, fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) { wb.write(fos); }
            wb.close();
            log.info("导出综合评价与荣誉模版: {}", filePath.toAbsolutePath());
            return filePath;
        } catch (IOException e) {
            log.error("导出综合评价与荣誉模版失败", e);
            throw new IllegalStateException("导出模版失败: " + e.getMessage(), e);
        }
    }

    // ==================== 场景 B：综合评价+荣誉导入 ====================

    public Map<String, Object> importHeadTeacherData(MultipartFile file, long evaluatorUserId, boolean enableAiPolish) {
        accessControlService.ensureHeadTeacherOrAdmin();
        int total = 0, success = 0, skipped = 0, failed = 0, aiPolished = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Map<Long, List<RowData>> studentRows = new LinkedHashMap<>();

            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;
                Long studentId = getLongCellValue(row, 9);
                if (studentId == null) studentId = getMergedCellLong(sheet, i, 9);
                if (studentId == null) { failed++; errors.add(createError(i + 1, "", "模版格式错误，缺少系统数据")); continue; }

                RowData rd = new RowData();
                rd.rowNum = i + 1; rd.studentId = studentId;
                rd.studentName = getMergedCellString(sheet, i, 1);
                rd.overallComment = getMergedCellString(sheet, i, 4);
                rd.strengths = getMergedCellString(sheet, i, 5);
                rd.improvementPoints = getMergedCellString(sheet, i, 6);
                rd.honorTypeName = getStringCellValue(row, 7);
                rd.achievementText = getStringCellValue(row, 8);
                rd.academicTermId = getLongCellValue(row, 10);
                rd.classId = getLongCellValue(row, 11);
                rd.achievementId = getLongCellValue(row, 12);
                studentRows.computeIfAbsent(studentId, k -> new ArrayList<>()).add(rd);
            }

            for (Map.Entry<Long, List<RowData>> e : studentRows.entrySet()) {
                List<RowData> rows = e.getValue();
                if (rows.isEmpty()) continue;
                try {
                    RowData first = rows.get(0);
                    String oc = first.overallComment, st = first.strengths, ip = first.improvementPoints;
                    boolean hasComment = !isEmpty(oc), hasDetail = !isEmpty(st) || !isEmpty(ip);

                    log.info("导入评语 studentId={}, overallComment=[{}], strengths=[{}], improvementPoints=[{}], hasComment={}",
                        first.studentId, oc, st, ip, hasComment);

                    if (!hasComment && hasDetail) { failed++; errors.add(createError(first.rowNum, first.studentName, "总体评价不能为空")); continue; }

                    if (hasComment) {
                        if (enableAiPolish) {
                            oc = safePolish(oc); aiPolished++;
                            if (!isEmpty(st)) { st = safePolish(st); aiPolished++; }
                            if (!isEmpty(ip)) { ip = safePolish(ip); aiPolished++; }
                        }
                        // class_id 优先从隐藏列读，失败则从 student 表反查
                        Long cid = first.classId;
                        if (cid == null) cid = getClassIdForStudent(first.studentId);
                        if (cid == null) cid = findClassIdByStudent(first.studentId);
                        Long tid = first.academicTermId != null ? first.academicTermId : getDefaultAcademicTermId();
                        if (tid == null) throw new IllegalArgumentException("无法确定学期，请检查模版是否完整");
                        log.info("保存评语 academicTermId={}, classId={}, studentId={}", tid, cid, first.studentId);
                        upsertOverallComment(tid, cid, first.studentId, oc, st, ip, evaluatorUserId);
                    }

                    // 先判断是否有荣誉需要导入，有则先清除该学生本学期已有荣誉再重新插入
                    boolean hasAnyAchievement = rows.stream().anyMatch(r -> !isEmpty(r.honorTypeName));
                    Long termId = first.academicTermId != null ? first.academicTermId : getDefaultAcademicTermId();
                    if (hasAnyAchievement && termId != null) {
                        deleteExistingAchievements(termId, first.studentId);
                    }

                    for (RowData rd : rows) {
                        boolean hasType = !isEmpty(rd.honorTypeName), hasText = !isEmpty(rd.achievementText);
                        if (hasType != hasText) { failed++; errors.add(createError(rd.rowNum, rd.studentName, "荣誉类型和荣誉详细内容须同时填写")); continue; }
                        if (!hasType) { if (rd == first && !hasComment) skipped++; continue; }

                        Long honorTypeId = findHonorTypeId(rd.honorTypeName);
                        if (honorTypeId == null) { failed++; errors.add(createError(rd.rowNum, rd.studentName, "荣誉类型 '" + rd.honorTypeName + "' 不存在")); continue; }

                        // 荣誉成就内容不进行 AI 润色，直接保存原文
                        Long tid = rd.academicTermId != null ? rd.academicTermId : getDefaultAcademicTermId();
                        if (rd.achievementId != null) updateAchievement(rd.achievementId, honorTypeId, rd.achievementText);
                        else insertAchievement(tid, rd.studentId, honorTypeId, rd.achievementText);
                    }
                    if (hasComment || rows.stream().anyMatch(r -> !isEmpty(r.honorTypeName))) success++;
                } catch (Exception ex) { failed++; errors.add(createError(rows.get(0).rowNum, rows.get(0).studentName, ex.getMessage())); }
            }
        } catch (IOException ex) { throw new IllegalArgumentException("无法读取 Excel 文件", ex); }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total); result.put("success", success); result.put("skipped", skipped);
        result.put("failed", failed); result.put("aiPolished", aiPolished); result.put("errors", errors);
        return result;
    }

    // ==================== 下拉验证 ====================

    private void addDropdownValidation(Sheet sheet, List<String> options, int col, int firstRow, int lastRow) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options.toArray(new String[0]));
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.createErrorBox("无效输入", "请从下拉列表中选择有效选项");
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private List<String> getExamTypeNames() {
        List<String> names = new ArrayList<>();
        jdbcTemplate.queryForList("SELECT exam_type_name FROM school_exam_type WHERE status = 1 ORDER BY sort_order")
            .forEach(r -> names.add((String) r.get("exam_type_name")));
        return names;
    }

    private List<String> getHonorTypeNames() {
        List<String> names = new ArrayList<>();
        jdbcTemplate.queryForList("SELECT honor_type_name FROM school_honor_type WHERE status = 1 ORDER BY sort_order")
            .forEach(r -> names.add((String) r.get("honor_type_name")));
        return names;
    }

    // ==================== 辅助方法 ====================

    private String safePolish(String text) {
        try { return commentPolishService.polish(text); }
        catch (Exception e) { log.warn("AI 润色失败，保留原文: {}", e.getMessage()); return text; }
    }

    /**
     * 任课老师只能导出自己任教课程的模版，班主任和管理员不受限
     */
    private void ensureCanExportSubject(long academicTermId, long classId, long subjectId) {
        if (accessControlService.hasRole("SUPER_ADMIN")) return;
        if (accessControlService.hasRole("HEAD_TEACHER")) return; // 班主任可导出班级全部课程
        // 纯任课老师：必须确认在此班级教授该科目
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM school_class_subject WHERE academic_term_id = ? AND class_id = ? AND subject_id = ? AND teacher_user_id = ? AND status = 1",
            Integer.class, academicTermId, classId, subjectId, accessControlService.currentUserId());
        if (count == null || count == 0) {
            throw new IllegalArgumentException("您只能导出自己任教课程的模版，如需导出其他科目请联系班主任或管理员");
        }
    }

    private void validateSubjectScore(double score, Long classSubjectId) {
        Map<String, Object> cs = first(jdbcTemplate.queryForList("SELECT cs.subject_id AS subjectId FROM school_class_subject cs WHERE cs.id = ?", classSubjectId));
        if (cs.isEmpty()) throw new IllegalArgumentException("班级课程不存在");
        Long subjectId = ((Number) cs.get("subjectId")).longValue();
        Map<String, Object> sub = first(jdbcTemplate.queryForList("SELECT min_score AS minScore, max_score AS maxScore FROM school_subject WHERE id = ?", subjectId));
        if (sub.isEmpty()) throw new IllegalArgumentException("科目不存在");
        double min = ((Number) sub.get("minScore")).doubleValue(), max = ((Number) sub.get("maxScore")).doubleValue();
        if (score < min || score > max) throw new IllegalArgumentException(String.format("成绩 %.2f 超出范围 %.2f-%.2f", score, min, max));
    }

    private Long academicTermIdFromClassSubject(long classSubjectId) {
        Map<String, Object> cs = first(jdbcTemplate.queryForList("SELECT academic_term_id AS academicTermId FROM school_class_subject WHERE id = ?", classSubjectId));
        return cs.isEmpty() ? null : ((Number) cs.get("academicTermId")).longValue();
    }

    private Long getClassIdForStudent(long studentId) {
        Map<String, Object> s = first(jdbcTemplate.queryForList("SELECT class_id AS classId FROM school_student WHERE id = ?", studentId));
        return s.isEmpty() ? null : ((Number) s.get("classId")).longValue();
    }

    private Long findClassIdByStudent(long studentId) {
        Map<String, Object> s = first(jdbcTemplate.queryForList("SELECT class_id AS classId FROM school_student WHERE id = ? AND is_deleted = 0", studentId));
        return s.isEmpty() ? null : ((Number) s.get("classId")).longValue();
    }

    private Long getDefaultAcademicTermId() {
        Map<String, Object> t = first(jdbcTemplate.queryForList("SELECT id FROM school_academic_term WHERE status = 1 ORDER BY id DESC LIMIT 1"));
        return t.isEmpty() ? null : ((Number) t.get("id")).longValue();
    }

    private Long findHonorTypeId(String name) {
        List<Map<String, Object>> types = jdbcTemplate.queryForList("SELECT id FROM school_honor_type WHERE LOWER(honor_type_name) = LOWER(?) AND status = 1", name.trim());
        return types.isEmpty() ? null : ((Number) types.get(0).get("id")).longValue();
    }

    /**
     * 解析考试类型 ID：优先从 F 列名称匹配，为空则回退到隐藏列 M
     */
    private Long resolveExamTypeId(String examTypeName, Long hiddenExamTypeId) {
        if (!isEmpty(examTypeName)) {
            Long id = findExamTypeIdByName(examTypeName);
            if (id != null) return id;
            // 名称匹配不到就报错，防止因名称拼写错误导致 exam_type_id 丢失
            throw new IllegalArgumentException("考试类型 '" + examTypeName + "' 不存在，请检查下拉选择的名称是否正确");
        }
        return hiddenExamTypeId;
    }

    private Long findExamTypeIdByName(String name) {
        List<Map<String, Object>> types = jdbcTemplate.queryForList(
            "SELECT id FROM school_exam_type WHERE LOWER(exam_type_name) = LOWER(?) AND status = 1", name.trim());
        return types.isEmpty() ? null : ((Number) types.get(0).get("id")).longValue();
    }

    private String getTermName(long id) {
        Map<String, Object> t = first(jdbcTemplate.queryForList("SELECT term_name AS termName FROM school_academic_term WHERE id = ?", id));
        return t.isEmpty() ? "" : (String) t.getOrDefault("termName", "");
    }

    private String getClassName(long id) {
        Map<String, Object> c = first(jdbcTemplate.queryForList("SELECT class_name AS className FROM school_class WHERE id = ?", id));
        return c.isEmpty() ? "" : (String) c.getOrDefault("className", "");
    }

    private String getExamTypeName(long id) {
        Map<String, Object> et = first(jdbcTemplate.queryForList("SELECT exam_type_name AS examTypeName FROM school_exam_type WHERE id = ?", id));
        return et.isEmpty() ? "" : (String) et.getOrDefault("examTypeName", "");
    }

    // ==================== 数据库操作 ====================

    private void upsertCourseResult(long academicTermId, long classSubjectId, long studentId, Long examTypeId,
                                     double score, String perf, String strengths, String improv, long evaluatorId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Map<String, Object>> ex = examTypeId != null ?
            jdbcTemplate.queryForList("SELECT id FROM school_student_course_result WHERE academic_term_id=? AND class_subject_id=? AND student_id=? AND exam_type_id=?", academicTermId, classSubjectId, studentId, examTypeId) :
            jdbcTemplate.queryForList("SELECT id FROM school_student_course_result WHERE academic_term_id=? AND class_subject_id=? AND student_id=? AND exam_type_id IS NULL", academicTermId, classSubjectId, studentId);
        if (ex.isEmpty())
            insert("school_student_course_result", "INSERT INTO school_student_course_result (academic_term_id, class_subject_id, student_id, exam_type_id, score, performance_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status) VALUES (?,?,?,?,?,?,?,?,?,?,1)", academicTermId, classSubjectId, studentId, examTypeId, score, perf, strengths, improv, evaluatorId, now);
        else
            jdbcTemplate.update("UPDATE school_student_course_result SET score=?, performance_comment=?, strengths=?, improvement_points=?, exam_type_id=?, evaluator_user_id=?, evaluated_at=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", score, perf, strengths, improv, examTypeId, evaluatorId, now, ((Number) ex.get(0).get("id")).longValue());
    }

    private void upsertOverallComment(long academicTermId, Long classId, long studentId, String oc, String st, String ip, long evaluatorId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Map<String, Object>> ex = jdbcTemplate.queryForList("SELECT id FROM school_student_overall_comment WHERE academic_term_id=? AND student_id=?", academicTermId, studentId);
        Long cid = classId != null ? classId : getClassIdForStudent(studentId);
        if (ex.isEmpty())
            insert("school_student_overall_comment", "INSERT INTO school_student_overall_comment (academic_term_id, class_id, student_id, overall_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status) VALUES (?,?,?,?,?,?,?,?,1)", academicTermId, cid, studentId, oc, st, ip, evaluatorId, now);
        else
            jdbcTemplate.update("UPDATE school_student_overall_comment SET overall_comment=?, strengths=?, improvement_points=?, class_id=?, evaluator_user_id=?, evaluated_at=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", oc, st, ip, cid, evaluatorId, now, ((Number) ex.get(0).get("id")).longValue());
    }

    private void deleteExistingAchievements(long academicTermId, long studentId) {
        jdbcTemplate.update("DELETE FROM school_student_achievement WHERE academic_term_id = ? AND student_id = ?",
            academicTermId, studentId);
    }

    private void insertAchievement(long tid, long sid, long honorTypeId, String text) {
        int max = 0;
        List<Map<String, Object>> ex = jdbcTemplate.queryForList("SELECT COALESCE(MAX(sort_order),-1) AS maxSort FROM school_student_achievement WHERE academic_term_id=? AND student_id=?", tid, sid);
        if (!ex.isEmpty() && ex.get(0).get("maxSort") != null) max = ((Number) ex.get(0).get("maxSort")).intValue();
        insert("school_student_achievement", "INSERT INTO school_student_achievement (academic_term_id, student_id, honor_type_id, achievement_text, sort_order) VALUES (?,?,?,?,?)", tid, sid, honorTypeId, text, max + 1);
    }

    private void updateAchievement(long id, long honorTypeId, String text) {
        jdbcTemplate.update("UPDATE school_student_achievement SET honor_type_id=?, achievement_text=? WHERE id=?", honorTypeId, text, id);
    }

    // ==================== POI 单元格读取 ====================

    private String getStringCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: double v = cell.getNumericCellValue(); return (v == Math.floor(v) && !Double.isInfinite(v)) ? String.valueOf((long) v) : String.valueOf(v);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    private Double getNumericCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
            if (cell.getCellType() == CellType.STRING) { String s = cell.getStringCellValue().trim(); return s.isEmpty() ? null : Double.parseDouble(s); }
            if (cell.getCellType() == CellType.FORMULA) return cell.getNumericCellValue();
        } catch (Exception e) { return null; }
        return null;
    }

    private Long getLongCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return (long) cell.getNumericCellValue();
            if (cell.getCellType() == CellType.STRING) { String s = cell.getStringCellValue().trim(); return s.isEmpty() ? null : Long.parseLong(s); }
            if (cell.getCellType() == CellType.FORMULA) return (long) cell.getNumericCellValue();
        } catch (Exception e) { return null; }
        return null;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
            if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        } catch (Exception e) { return ""; }
        return "";
    }

    private String getMergedCellString(Sheet sheet, int rowNum, int col) {
        Row row = sheet.getRow(rowNum);
        if (row != null) { Cell cell = row.getCell(col); if (cell != null && cell.getCellType() != CellType.BLANK) { String v = getStringCellValue(row, col); if (!v.isEmpty()) return v; } }
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress r = sheet.getMergedRegion(i);
            if (r.getFirstColumn() <= col && col <= r.getLastColumn() && r.getFirstRow() <= rowNum && rowNum <= r.getLastRow()) {
                Row fr = sheet.getRow(r.getFirstRow());
                if (fr != null) return getStringCellValue(fr, col);
            }
        }
        return "";
    }

    private Long getMergedCellLong(Sheet sheet, int rowNum, int col) {
        String v = getMergedCellString(sheet, rowNum, col);
        if (v.isEmpty()) return null;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return null; }
    }

    // ==================== 样式 ====================

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createHintStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createMergedCellStyle(XSSFWorkbook wb) {
        // 合并区域中的非首行单元格样式（仅边框，无内容居中需求）
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createWrapStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ==================== 小工具 ====================

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private String strVal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private Map<String, String> createError(int row, String name, String reason) {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("row", String.valueOf(row)); e.put("studentName", name != null ? name : ""); e.put("reason", reason);
        return e;
    }

    private static class RowData {
        int rowNum; long studentId; String studentName;
        String overallComment, strengths, improvementPoints, honorTypeName, achievementText;
        Long academicTermId, classId, achievementId;
    }
}
