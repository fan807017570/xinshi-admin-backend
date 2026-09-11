package com.xinshi.admin.application.courseresult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.interfaces.dto.PageRequest;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourseResultServiceTest {

    @Test
    void examTypeFilterStaysInLeftJoinSoMissingScoresRemainVisible() {
        JdbcTemplate jdbcTemplate = jdbcTemplateStub();
        AccessControlService accessControlService = mock(AccessControlService.class);
        when(accessControlService.currentUserId()).thenReturn(7L);
        when(accessControlService.hasRole("SUPER_ADMIN")).thenReturn(true);
        CourseResultService service = new CourseResultService(jdbcTemplate, accessControlService);

        service.listTeacherScoreEntries(
                31L, 41L, 51L, 61L, null, "teacher", new PageRequest(1, 20));

        String countSql = mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "queryForObject".equals(invocation.getMethod().getName()))
                .map(invocation -> (String)invocation.getArgument(0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("count query was not executed"));
        int whereIndex = countSql.indexOf(" WHERE ");
        assertTrue(countSql.substring(0, whereIndex).contains("AND r.exam_type_id = ?"));
        assertFalse(countSql.substring(whereIndex).contains("r.exam_type_id = ?"));
    }

    @Test
    void deletingScoreChecksResourcePermissionBeforeDelete() {
        JdbcTemplate jdbcTemplate = jdbcTemplateStub();
        AccessControlService accessControlService = mock(AccessControlService.class);
        CourseResultService service = new CourseResultService(jdbcTemplate, accessControlService);

        service.deleteStudentResult(91L);

        verify(accessControlService).ensureTeacherCanWriteResults();
        verify(accessControlService).ensureCanAccessResult(91L);
        long updates = mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "update".equals(invocation.getMethod().getName()))
                .count();
        assertEquals(1L, updates);
    }

    private JdbcTemplate jdbcTemplateStub() {
        return mock(JdbcTemplate.class, invocation -> {
            String methodName = invocation.getMethod().getName();
            if ("queryForObject".equals(methodName)) return 0L;
            if ("queryForList".equals(methodName)) return Collections.emptyList();
            if ("update".equals(methodName)) return 1;
            return null;
        });
    }
}
