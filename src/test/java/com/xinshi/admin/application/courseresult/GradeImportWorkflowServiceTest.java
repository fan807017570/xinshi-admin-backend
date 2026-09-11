package com.xinshi.admin.application.courseresult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.xinshi.admin.application.commentpolish.CommentPolishService;
import com.xinshi.admin.application.h5.ParentContentLifecycleService;
import com.xinshi.admin.application.school.AccessControlService;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class GradeImportWorkflowServiceTest {
    private JdbcTemplate jdbcTemplate;
    private GradeImportWorkflowService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class, invocation -> {
            String methodName = invocation.getMethod().getName();
            if ("queryForList".equals(methodName)) {
                return queryRows(invocation.getArgument(0));
            }
            if ("update".equals(methodName)) {
                return 1;
            }
            return null;
        });
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        ParentContentLifecycleService lifecycleService = mock(ParentContentLifecycleService.class);
        CommentPolishService commentPolishService = mock(CommentPolishService.class);

        Map<String, Object> session = new LinkedHashMap<String, Object>();
        session.put("token", "session-token");
        when(accessControlService.currentSession()).thenReturn(session);
        when(accessControlService.currentUserId()).thenReturn(7L);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        service = new GradeImportWorkflowService(
                jdbcTemplate, transactionTemplate, accessControlService,
                lifecycleService, commentPolishService);
    }

    @Test
    void currentTemplateAcceptsZeroAndTokenCanOnlyBeCommittedOnce() throws Exception {
        Map<String, Object> precheck = service.precheckCourseResults(
                currentTemplate(0D), 31L, "2027", 5, 41L, 51L, 61L);

        assertEquals(0, precheck.get("errorCount"));
        assertEquals(1, precheck.get("creates"));
        assertNotNull(precheck.get("importToken"));
        assertEquals(0L, updateInvocationCount());

        String token = String.valueOf(precheck.get("importToken"));
        Map<String, Object> committed = service.commitCourseResults(token);

        assertEquals(1, committed.get("success"));
        assertEquals(1L, updateInvocationCount());
        assertThrows(IllegalArgumentException.class, () -> service.commitCourseResults(token));
    }

    @Test
    void legacyTemplateWithoutHiddenColumnsUsesPageContext() throws Exception {
        Map<String, Object> precheck = service.precheckCourseResults(
                legacyTemplate(false), 31L, "2027", 5, 41L, 51L, 61L);

        assertEquals("1.0", precheck.get("templateVersion"));
        assertEquals(0, precheck.get("errorCount"));
        assertNotNull(precheck.get("importToken"));
        assertEquals(0L, updateInvocationCount());
    }

    @Test
    void malformedLegacyHiddenIdIsReturnedAsRowErrorWithoutWriting() throws Exception {
        Map<String, Object> precheck = service.precheckCourseResults(
                legacyTemplate(true), 31L, "2027", 5, 41L, 51L, 61L);

        assertEquals(1, precheck.get("errorCount"));
        assertNull(precheck.get("importToken"));
        List<?> errors = (List<?>)precheck.get("errors");
        assertEquals(1, errors.size());
        assertEquals(0L, updateInvocationCount());
    }

    @Test
    void invalidXlsxContentReturnsValidationError() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-an-excel-file".getBytes());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.precheckCourseResults(
                        file, 31L, "2027", 5, 41L, 51L, 61L));

        assertEquals("无法读取 Excel 文件，请确认文件为有效的 .xlsx 文件",
                exception.getMessage());
        assertEquals(0L, updateInvocationCount());
    }

    private long updateInvocationCount() {
        return mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "update".equals(invocation.getMethod().getName()))
                .count();
    }

    private List<Map<String, Object>> queryRows(String sql) {
        if (sql.contains("SELECT cs.id AS classSubjectId, c.grade_session")) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("classSubjectId", 71L);
            row.put("gradeSession", "2027");
            row.put("gradeLevel", 5);
            row.put("className", "高二(1)班");
            row.put("termName", "2026-2027 第一学期");
            row.put("subjectName", "数学");
            row.put("minScore", 0D);
            row.put("maxScore", 100D);
            row.put("examTypeName", "期中考试");
            return Collections.singletonList(row);
        }
        if (sql.contains("SELECT id, student_no AS studentNo")) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", 81L);
            row.put("studentNo", "S001");
            row.put("studentName", "学生一");
            return Collections.singletonList(row);
        }
        return Collections.emptyList();
    }

    private MockMultipartFile currentTemplate(double score) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet metadata = workbook.createSheet("模版说明");
            String[][] values = {
                    {"template_type", "COURSE_RESULT"},
                    {"template_version", "2.0"},
                    {"academic_term_id", "31"},
                    {"academic_term_name", "2026-2027 第一学期"},
                    {"grade_session", "2027"},
                    {"grade_level", "5"},
                    {"class_id", "41"},
                    {"class_name", "高二(1)班"},
                    {"subject_id", "51"},
                    {"subject_name", "数学"},
                    {"exam_type_id", "61"},
                    {"exam_type_name", "期中考试"}
            };
            for (int index = 0; index < values.length; index++) {
                Row row = metadata.createRow(index);
                row.createCell(0).setCellValue(values[index][0]);
                row.createCell(1).setCellValue(values[index][1]);
            }
            Sheet data = workbook.createSheet("成绩录入");
            Row header = data.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("成绩（0.00-100.00）");
            Row row = data.createRow(1);
            row.createCell(0).setCellValue("S001");
            row.createCell(1).setCellValue("学生一");
            row.createCell(2).setCellValue(score);
            workbook.write(output);
            return file("current.xlsx", output);
        }
    }

    private MockMultipartFile legacyTemplate(boolean malformedHiddenId) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成绩模版");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(6).setCellValue("成绩（0.00-100.00）");
            sheet.createRow(1);
            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue("S001");
            row.createCell(1).setCellValue("学生一");
            row.createCell(2).setCellValue("2026-2027 第一学期");
            row.createCell(3).setCellValue("高二(1)班");
            row.createCell(4).setCellValue("数学");
            row.createCell(5).setCellValue("期中考试");
            row.createCell(6).setCellValue(88D);
            if (malformedHiddenId) {
                row.createCell(10).setCellValue("not-an-id");
            }
            workbook.write(output);
            return file("legacy.xlsx", output);
        }
    }

    private MockMultipartFile file(String name, ByteArrayOutputStream output) {
        return new MockMultipartFile(
                "file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                output.toByteArray());
    }
}
