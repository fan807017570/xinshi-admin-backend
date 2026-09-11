package com.xinshi.admin.application.courseresult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.xinshi.admin.application.commentpolish.CommentPolishService;
import com.xinshi.admin.application.h5.ParentContentLifecycleService;
import com.xinshi.admin.application.school.AccessControlService;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class GradeExcelServiceTest {

    @Test
    void courseTemplateExportRejectsEveryMissingContextParameter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GradeExcelService service = new GradeExcelService(
                jdbcTemplate,
                mock(CommentPolishService.class),
                mock(AccessControlService.class),
                mock(ParentContentLifecycleService.class));

        assertEquals("请选择学期", assertThrows(IllegalArgumentException.class,
                () -> service.exportCourseResultTemplate(
                        null, "2027", 5, 41L, 51L, 61L)).getMessage());
        assertEquals("请选择届次", assertThrows(IllegalArgumentException.class,
                () -> service.exportCourseResultTemplate(
                        31L, "", 5, 41L, 51L, 61L)).getMessage());
        assertEquals("请选择年级", assertThrows(IllegalArgumentException.class,
                () -> service.exportCourseResultTemplate(
                        31L, "2027", null, 41L, 51L, 61L)).getMessage());
        assertEquals("请选择班级", assertThrows(IllegalArgumentException.class,
                () -> service.exportCourseResultTemplate(
                        31L, "2027", 5, null, 51L, 61L)).getMessage());
        assertEquals("请选择科目", assertThrows(IllegalArgumentException.class,
                () -> service.exportCourseResultTemplate(
                        31L, "2027", 5, 41L, null, 61L)).getMessage());
        assertEquals("请选择考试类型", assertThrows(IllegalArgumentException.class,
                () -> service.exportCourseResultTemplate(
                        31L, "2027", 5, 41L, 51L, null)).getMessage());
    }

    @Test
    void completeFileIsSentToOneLifecycleBatch() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CommentPolishService polishService = mock(CommentPolishService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        ParentContentLifecycleService lifecycleService = mock(ParentContentLifecycleService.class);
        GradeExcelService service = new GradeExcelService(
                jdbcTemplate, polishService, accessControlService, lifecycleService);
        MockMultipartFile file = workbookWithTwoStudents();

        Map<String, Object> result = service.importHeadTeacherData(file, 12345L, false);

        assertEquals(2, result.get("success"));
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(lifecycleService, times(1)).importParentContentBatch(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    private MockMultipartFile workbookWithTwoStudents() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("综合评价与荣誉");
            sheet.createRow(0);
            sheet.createRow(1);
            createStudentRow(sheet, 2, 51L, "学生一", "学生一评语");
            createStudentRow(sheet, 3, 52L, "学生二", "学生二评语");
            workbook.write(output);
            return new MockMultipartFile(
                    "file", "parent-content.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private void createStudentRow(
            Sheet sheet,
            int rowNumber,
            long studentId,
            String studentName,
            String comment) {
        Row row = sheet.createRow(rowNumber);
        row.createCell(1).setCellValue(studentName);
        row.createCell(4).setCellValue(comment);
        row.createCell(9).setCellValue(studentId);
        row.createCell(10).setCellValue(31L);
        row.createCell(11).setCellValue(41L);
    }
}
