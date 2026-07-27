/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 *  org.springframework.http.HttpMethod
 *  org.springframework.stereotype.Component
 *  org.springframework.util.AntPathMatcher
 *  org.springframework.util.StringUtils
 *  org.springframework.web.servlet.HandlerInterceptor
 */
package com.xinshi.admin.interfaces.web.security;

import com.xinshi.admin.interfaces.web.security.AuthContext;
import com.xinshi.admin.interfaces.web.security.ForbiddenException;
import com.xinshi.admin.interfaces.web.security.RequestAuthService;
import com.xinshi.admin.interfaces.web.security.UnauthorizedException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor
implements HandlerInterceptor {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private final RequestAuthService requestAuthService;

    public AuthInterceptor(RequestAuthService requestAuthService) {
        this.requestAuthService = requestAuthService;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (this.isPublicPath(path)) {
            return true;
        }
        Map<String, Object> session = this.requestAuthService.resolveSession(this.extractToken(request), request.getParameter("loginName"));
        if (session == null || session.isEmpty()) {
            throw new UnauthorizedException("未登录");
        }
        AuthContext.set(session);
        request.setAttribute("authUser", session);
        String[] requiredRoles = this.requiredRoles(path, request.getMethod());
        if (requiredRoles.length > 0 && !this.requestAuthService.hasAnyRole(session, requiredRoles)) {
            throw new ForbiddenException("无权限访问");
        }
        return true;
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublicPath(String path) {
        return PATH_MATCHER.match("/api/auth/login", path) || PATH_MATCHER.match("/api/health", path);
    }

    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("X-Auth-Token");
        if (!StringUtils.hasText((String)token)) {
            token = request.getHeader("Authorization");
        }
        return this.requestAuthService.normalizeToken(token);
    }

    private String[] requiredRoles(String path, String method) {
        if (PATH_MATCHER.match("/api/auth/**", path) || PATH_MATCHER.match("/api/dashboard/**", path)) {
            return new String[0];
        }
        if (PATH_MATCHER.match("/api/demo/**", path)) {
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/teachers/search", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
        }
        if (PATH_MATCHER.match("/api/users/**", path)) {
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/academic-terms/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER", "PARENT"};
            }
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/roles", path)) {
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/classes/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
            }
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/subjects/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
            }
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/grade-subjects/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
            }
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/menus", path)) {
            return new String[0];
        }
        if (PATH_MATCHER.match("/api/class-subjects/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
            }
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
        }
        if (PATH_MATCHER.match("/api/students/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "PARENT"};
            }
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
        }
        if (PATH_MATCHER.match("/api/student-results/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER", "PARENT"};
            }
            if (HttpMethod.POST.matches(method) || HttpMethod.PATCH.matches(method) || HttpMethod.DELETE.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
            }
        }
        if (PATH_MATCHER.match("/api/teacher-score-entries/**", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
        }
        if (PATH_MATCHER.match("/api/student-overall-comments/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "PARENT"};
            }
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
        }
        if (PATH_MATCHER.match("/api/transcripts/**", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "PARENT"};
        }
        if (PATH_MATCHER.match("/api/comments/**", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
        }
        if (PATH_MATCHER.match("/api/exam-types/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
            }
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/honor-types/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
            }
            return new String[]{"SUPER_ADMIN"};
        }
        if (PATH_MATCHER.match("/api/achievements/**", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
        }
        if (PATH_MATCHER.match("/api/enroll-grades/**", path)) {
            if (HttpMethod.GET.matches(method)) {
                return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
            }
            return new String[]{"SUPER_ADMIN"};
        }
        // Excel 导入导出：单科成绩模版
        if (PATH_MATCHER.match("/api/course-results/export-template", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
        }
        if (PATH_MATCHER.match("/api/course-results/import", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER", "TEACHER"};
        }
        // Excel 导入导出：综合评价与荣誉模版
        if (PATH_MATCHER.match("/api/head-teacher/export-template", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
        }
        if (PATH_MATCHER.match("/api/head-teacher/import", path)) {
            return new String[]{"SUPER_ADMIN", "HEAD_TEACHER"};
        }
        return new String[]{"SUPER_ADMIN"};
    }
}

