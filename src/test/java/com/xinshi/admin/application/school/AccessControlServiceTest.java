package com.xinshi.admin.application.school;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xinshi.admin.interfaces.web.security.AuthContext;
import com.xinshi.admin.interfaces.web.security.ForbiddenException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccessControlServiceTest {

    @AfterEach
    void clearSession() {
        AuthContext.clear();
    }

    @Test
    void administratorCanManageEveryHeadTeacherClassWithoutOwnershipQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AccessControlService service = new AccessControlService(jdbcTemplate);
        setSession("SUPER_ADMIN");

        assertDoesNotThrow(() -> service.ensureCanManageHeadTeacherClass(41L));

        verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(Integer.class), any(Object[].class));
    }

    @Test
    void dualRoleUserCannotUseTeachingAssignmentAsHeadTeacherOwnership() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        AccessControlService service = new AccessControlService(jdbcTemplate);
        setSession("HEAD_TEACHER", "TEACHER");

        assertThrows(ForbiddenException.class,
                () -> service.ensureCanManageHeadTeacherClass(41L));
    }

    private void setSession(String... roles) {
        Map<String, Object> session = new LinkedHashMap<String, Object>();
        session.put("userId", 7L);
        session.put("roles", Arrays.asList(roles));
        AuthContext.set(session);
    }
}
