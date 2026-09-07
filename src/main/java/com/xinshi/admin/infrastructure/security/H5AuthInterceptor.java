package com.xinshi.admin.infrastructure.security;

import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates H5 Cookie sessions independently from the admin token interceptor.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Component
public class H5AuthInterceptor implements HandlerInterceptor {
    public static final String SESSION_COOKIE = "XINSHI_H5_SESSION";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/h5/bootstrap",
            "/api/h5/wechat/login-confirmations",
            "/api/h5/wechat/callback");

    private final JdbcTemplate jdbcTemplate;
    private final H5SecuritySupport securitySupport;

    public H5AuthInterceptor(JdbcTemplate jdbcTemplate, H5SecuritySupport securitySupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.securitySupport = securitySupport;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            return true;
        }
        String sessionToken = cookieValue(request, SESSION_COOKIE);
        if (!StringUtils.hasText(sessionToken)) {
            throw new H5ApiException(HttpStatus.UNAUTHORIZED, "WECHAT_AUTH_REQUIRED", "请重新完成微信授权");
        }
        Map<String, Object> session = loadSession(sessionToken);
        if (session.isEmpty()) {
            throw new H5ApiException(HttpStatus.UNAUTHORIZED, "H5_SESSION_EXPIRED", "登录已过期，请重新授权");
        }
        validateCsrf(request, session);
        H5RequestContext.set(new H5RequestContext.SessionIdentity(
                number(session, "id"),
                number(session, "wechatAccountId"),
                number(session, "parentUserId"),
                String.valueOf(session.get("appid")),
                String.valueOf(session.get("csrfTokenHash"))));
        jdbcTemplate.update(
                "UPDATE school_h5_session SET last_access_at = CURRENT_TIMESTAMP WHERE id = ?",
                number(session, "id"));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        H5RequestContext.clear();
    }

    private Map<String, Object> loadSession(String token) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT hs.id, hs.wechat_account_id AS wechatAccountId, hs.parent_user_id AS parentUserId, "
                        + "hs.appid, hs.csrf_token_hash AS csrfTokenHash "
                        + "FROM school_h5_session hs JOIN school_wechat_account wa ON wa.id = hs.wechat_account_id "
                        + "WHERE hs.token_hash = ? AND hs.status = 1 AND hs.expires_at > CURRENT_TIMESTAMP "
                        + "AND wa.status = 1 AND wa.appid = hs.appid LIMIT 1",
                securitySupport.sha256(token));
        return rows.isEmpty() ? java.util.Collections.emptyMap() : rows.get(0);
    }

    private void validateCsrf(HttpServletRequest request, Map<String, Object> session) {
        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())) {
            return;
        }
        String csrf = request.getHeader("X-H5-CSRF");
        String expected = String.valueOf(session.get("csrfTokenHash"));
        if (!StringUtils.hasText(csrf) || !securitySupport.constantTimeEquals(securitySupport.sha256(csrf), expected)) {
            throw new H5ApiException(HttpStatus.FORBIDDEN, "CSRF_INVALID", "请求校验失败，请刷新后重试");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }
}
