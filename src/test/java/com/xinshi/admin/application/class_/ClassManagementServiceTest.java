package com.xinshi.admin.application.class_;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.xinshi.admin.application.school.AccessControlService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ClassManagementServiceTest {

    @Test
    void classFilterOptionsUseEnabledConfiguredGradeNames() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class, invocation -> {
            if (!"queryForList".equals(invocation.getMethod().getName())) {
                return null;
            }
            return Arrays.asList(
                    filterRow("2028", 10, "高中一年级"),
                    filterRow("2027", 10, "高中一年级"),
                    filterRow("2027", 11, "高中二年级"));
        });
        AccessControlService accessControlService = mock(AccessControlService.class);
        when(accessControlService.hasRole("SUPER_ADMIN")).thenReturn(true);
        ClassManagementService service = new ClassManagementService(jdbcTemplate, accessControlService);

        Map<String, Object> result = service.listClassFilterOptions("headTeacher");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gradeOptions = (List<Map<String, Object>>) result.get("gradeOptions");
        assertEquals(2, gradeOptions.size());
        assertEquals(10, gradeOptions.get(0).get("gradeLevel"));
        assertEquals("高中一年级", gradeOptions.get(0).get("gradeName"));
        assertEquals(11, gradeOptions.get(1).get("gradeLevel"));
        assertEquals("高中二年级", gradeOptions.get(1).get("gradeName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> combinations = (List<Map<String, Object>>) result.get("combinations");
        assertEquals("高中一年级", combinations.get(0).get("gradeName"));

        String sql = mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "queryForList".equals(invocation.getMethod().getName()))
                .map(invocation -> (String) invocation.getArgument(0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("filter-options query was not executed"));
        assertTrue(sql.contains("JOIN school_enroll_grade eg"));
        assertTrue(sql.contains("eg.status = 1"));
    }

    private Map<String, Object> filterRow(String gradeSession, int gradeLevel, String gradeName) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("gradeSession", gradeSession);
        row.put("gradeLevel", gradeLevel);
        row.put("gradeName", gradeName);
        return row;
    }
}
