package com.xinshi.admin.application.h5;

import com.xinshi.admin.infrastructure.security.H5AuthInterceptor;
import com.xinshi.admin.infrastructure.security.H5SecuritySupport;
import com.xinshi.admin.infrastructure.wechat.WechatOAuthClient;
import com.xinshi.admin.infrastructure.wechat.WechatOAuthClient.WechatIdentity;
import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Handles AppID validation, login confirmation, OAuth callback and H5 session creation.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Service
public class H5AuthService {
    public static final String PREAUTH_COOKIE = "XINSHI_H5_PREAUTH";
    private static final String DEFAULT_RETURN_PATH = "/h5/score";

    private final JdbcTemplate jdbcTemplate;
    private final H5SecuritySupport securitySupport;
    private final H5QueryContextService queryContextService;
    private final WechatOAuthClient oauthClient;
    private final boolean enabled;
    private final boolean secureCookie;
    private final long sessionTtlSeconds;
    private final long stateTtlSeconds;
    private final String identityLookupSecret;
    private final String identityEncryptionSecret;

    public H5AuthService(
            JdbcTemplate jdbcTemplate,
            H5SecuritySupport securitySupport,
            H5QueryContextService queryContextService,
            WechatOAuthClient oauthClient,
            @Value("${h5.score-query.enabled:false}") boolean enabled,
            @Value("${h5.cookie.secure:true}") boolean secureCookie,
            @Value("${h5.session.ttl-seconds:3600}") long sessionTtlSeconds,
            @Value("${h5.oauth-state.ttl-seconds:300}") long stateTtlSeconds,
            @Value("${h5.wechat-identity.lookup-hmac-secret:}") String identityLookupSecret,
            @Value("${h5.wechat-identity.encryption-secret:}") String identityEncryptionSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.securitySupport = securitySupport;
        this.queryContextService = queryContextService;
        this.oauthClient = oauthClient;
        this.enabled = enabled;
        this.secureCookie = secureCookie;
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.stateTtlSeconds = stateTtlSeconds;
        this.identityLookupSecret = identityLookupSecret;
        this.identityEncryptionSecret = identityEncryptionSecret;
    }

    public Map<String, Object> bootstrap(
            String appid,
            String sessionToken,
            HttpServletResponse response) {
        requireEnabled();
        Map<String, Object> app = requireApp(appid);
        Map<String, Object> session = loadSession(sessionToken, appid);
        if (session.isEmpty()) {
            return loginConfirmation(app, response);
        }
        String csrfToken = securitySupport.randomToken(32);
        jdbcTemplate.update(
                "UPDATE school_h5_session SET csrf_token_hash = ?, last_access_at = CURRENT_TIMESTAMP WHERE id = ?",
                securitySupport.sha256(csrfToken),
                longValue(session, "id"));
        Long parentUserId = nullableLong(session, "parentUserId");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("csrfToken", csrfToken);
        if (parentUserId == null) {
            data.put("flowState", "NEED_REGISTRATION");
            Map<String, Object> preset = queryContextService.pendingPreset(longValue(session, "wechatAccountId"));
            if (!preset.isEmpty()) {
                data.put("queryPreset", preset);
            }
            return data;
        }
        ensureParentEnabled(parentUserId);
        List<Map<String, Object>> students = listStudents(parentUserId);
        if (students.isEmpty()) {
            data.put("flowState", "ACCOUNT_INCOMPLETE");
            return data;
        }
        H5QueryContextResolution resolution = queryContextService.resolveForParent(
                longValue(session, "wechatAccountId"), parentUserId);
        data.put("flowState", resolution.getFlowState());
        data.put("students", students);
        data.put("defaultStudentId", students.get(0).get("studentId"));
        if (!resolution.getQueryPreset().isEmpty()) {
            data.put("queryPreset", resolution.getQueryPreset());
        }
        if (!resolution.getCandidates().isEmpty()) {
            data.put("candidates", resolution.getCandidates());
        }
        return data;
    }

    public Map<String, Object> confirmLogin(
            String confirmationToken,
            String preauthToken) {
        requireEnabled();
        if (!StringUtils.hasText(confirmationToken) || !StringUtils.hasText(preauthToken)) {
            throw invalidConfirmation();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT os.id, os.appid, os.return_path AS returnPath, wa.oauth_callback_url AS callbackUrl "
                        + "FROM school_wechat_oauth_state os JOIN school_wechat_app wa ON wa.appid = os.appid "
                        + "WHERE os.login_confirmation_token_hash = ? AND os.preauth_cookie_hash = ? "
                        + "AND os.confirmation_status = 'PENDING' AND os.expires_at > CURRENT_TIMESTAMP "
                        + "AND wa.status = 1 AND wa.score_query_enabled = 1 LIMIT 1",
                securitySupport.sha256(confirmationToken),
                securitySupport.sha256(preauthToken));
        if (rows.isEmpty()) {
            throw invalidConfirmation();
        }
        Map<String, Object> row = rows.get(0);
        String state = securitySupport.randomToken(32);
        int updated = jdbcTemplate.update(
                "UPDATE school_wechat_oauth_state SET state_hash = ?, confirmation_status = 'CONFIRMED', "
                        + "confirmed_at = CURRENT_TIMESTAMP WHERE id = ? AND confirmation_status = 'PENDING'",
                securitySupport.sha256(state),
                longValue(row, "id"));
        if (updated != 1) {
            throw invalidConfirmation();
        }
        String appid = String.valueOf(row.get("appid"));
        String callback = String.valueOf(row.get("callbackUrl"));
        String authorizeUrl;
        if (oauthClient.isMockEnabled()) {
            authorizeUrl = callback + "?code=mock-code&state=" + urlEncode(state);
        } else {
            authorizeUrl = "https://open.weixin.qq.com/connect/oauth2/authorize?appid="
                    + urlEncode(appid)
                    + "&redirect_uri=" + urlEncode(callback)
                    + "&response_type=code&scope=snsapi_userinfo&state=" + urlEncode(state)
                    + "#wechat_redirect";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authorizeUrl", authorizeUrl);
        return data;
    }

    public String callback(String code, String state, HttpServletResponse response) {
        requireEnabled();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "微信授权状态无效");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT os.id, os.appid, os.return_path AS returnPath, wa.app_secret_ref AS appSecretRef "
                        + "FROM school_wechat_oauth_state os JOIN school_wechat_app wa ON wa.appid = os.appid "
                        + "WHERE os.state_hash = ? AND os.confirmation_status = 'CONFIRMED' "
                        + "AND os.consumed_at IS NULL AND os.expires_at > CURRENT_TIMESTAMP "
                        + "AND wa.status = 1 AND wa.score_query_enabled = 1 LIMIT 1",
                securitySupport.sha256(state));
        if (rows.isEmpty()) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "微信授权状态无效");
        }
        Map<String, Object> oauthState = rows.get(0);
        int consumed = jdbcTemplate.update(
                "UPDATE school_wechat_oauth_state SET consumed_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND consumed_at IS NULL",
                longValue(oauthState, "id"));
        if (consumed != 1) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "微信授权状态无效");
        }
        String appid = String.valueOf(oauthState.get("appid"));
        WechatIdentity identity = oauthClient.exchange(
                appid,
                String.valueOf(oauthState.get("appSecretRef")),
                code);
        String openidHmac = securitySupport.hmacSha256(
                identityLookupSecret,
                appid + ":" + identity.getOpenid());
        long wechatAccountId = upsertWechatAccount(appid, openidHmac, identity);
        queryContextService.attachLatestContext(appid, identity.getOpenid(), wechatAccountId);
        Long parentUserId = findParentUserId(wechatAccountId);
        if (parentUserId != null) {
            ensureParentEnabled(parentUserId);
        }
        String sessionToken = securitySupport.randomToken(32);
        String csrfToken = securitySupport.randomToken(32);
        jdbcTemplate.update(
                "INSERT INTO school_h5_session "
                        + "(token_hash, csrf_token_hash, wechat_account_id, parent_user_id, appid, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, 1, ?)",
                securitySupport.sha256(sessionToken),
                securitySupport.sha256(csrfToken),
                wechatAccountId,
                parentUserId,
                appid,
                Timestamp.valueOf(LocalDateTime.now().plusSeconds(sessionTtlSeconds)));
        addCookie(response, H5AuthInterceptor.SESSION_COOKIE, sessionToken, sessionTtlSeconds);
        clearCookie(response, PREAUTH_COOKIE);
        return DEFAULT_RETURN_PATH + "?appid=" + urlEncode(appid) + "&oauth=done";
    }

    public void logout(long sessionId, HttpServletResponse response) {
        jdbcTemplate.update("UPDATE school_h5_session SET status = 0 WHERE id = ?", sessionId);
        clearCookie(response, H5AuthInterceptor.SESSION_COOKIE);
    }

    public void replaceSessionCookie(HttpServletResponse response, String sessionToken) {
        addCookie(response, H5AuthInterceptor.SESSION_COOKIE, sessionToken, sessionTtlSeconds);
    }

    private Map<String, Object> loginConfirmation(
            Map<String, Object> app,
            HttpServletResponse response) {
        String confirmationToken = securitySupport.randomToken(32);
        String preauthToken = securitySupport.randomToken(32);
        jdbcTemplate.update(
                "INSERT INTO school_wechat_oauth_state "
                        + "(login_confirmation_token_hash, preauth_cookie_hash, appid, return_path, "
                        + "confirmation_status, expires_at) VALUES (?, ?, ?, ?, 'PENDING', ?)",
                securitySupport.sha256(confirmationToken),
                securitySupport.sha256(preauthToken),
                String.valueOf(app.get("appid")),
                DEFAULT_RETURN_PATH,
                Timestamp.valueOf(LocalDateTime.now().plusSeconds(stateTtlSeconds)));
        addCookie(response, PREAUTH_COOKIE, preauthToken, stateTtlSeconds);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowState", "LOGIN_CONFIRMATION_REQUIRED");
        data.put("loginConfirmationToken", confirmationToken);
        data.put("schoolName", "新实中学");
        data.put("requestedProfile", java.util.Arrays.asList("openid", "nickname"));
        data.put("expiresInSeconds", stateTtlSeconds);
        return data;
    }

    private Map<String, Object> requireApp(String appid) {
        if (!StringUtils.hasText(appid) || !appid.matches("^wx[0-9A-Za-z]{8,64}$")) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_WECHAT_APP", "无效的成绩查询入口");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT appid, status, score_query_enabled AS scoreQueryEnabled "
                        + "FROM school_wechat_app WHERE appid = ? LIMIT 1",
                appid);
        if (rows.isEmpty()) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_WECHAT_APP", "无效的成绩查询入口");
        }
        Map<String, Object> app = rows.get(0);
        if (intValue(app, "status") != 1 || intValue(app, "scoreQueryEnabled") != 1) {
            throw new H5ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_APP_DISABLED", "成绩查询暂未启用");
        }
        return app;
    }

    private Map<String, Object> loadSession(String token, String appid) {
        if (!StringUtils.hasText(token)) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT hs.id, hs.wechat_account_id AS wechatAccountId, hs.parent_user_id AS parentUserId "
                        + "FROM school_h5_session hs JOIN school_wechat_account wa ON wa.id=hs.wechat_account_id AND wa.status=1 "
                        + "WHERE hs.token_hash = ? AND hs.appid = ? AND hs.status = 1 "
                        + "AND hs.expires_at > CURRENT_TIMESTAMP LIMIT 1",
                securitySupport.sha256(token),
                appid);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    private long upsertWechatAccount(String appid, String openidHmac, WechatIdentity identity) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status FROM school_wechat_account WHERE appid = ? AND openid_hmac = ? LIMIT 1",
                appid,
                openidHmac);
        byte[] nickname = securitySupport.encrypt(identityEncryptionSecret, identity.getNickname());
        if (!rows.isEmpty()) {
            Map<String, Object> account = rows.get(0);
            if (intValue(account, "status") != 1) {
                throw new H5ApiException(HttpStatus.FORBIDDEN, "WECHAT_ACCOUNT_DISABLED", "微信账户已停用");
            }
            jdbcTemplate.update(
                    "UPDATE school_wechat_account SET nickname_ciphertext = ?, nickname_updated_at = CURRENT_TIMESTAMP "
                            + "WHERE id = ?",
                    nickname,
                    longValue(account, "id"));
            return longValue(account, "id");
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO school_wechat_account "
                            + "(appid, openid_hmac, openid_ciphertext, nickname_ciphertext, status, nickname_updated_at) "
                            + "VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, appid);
            statement.setString(2, openidHmac);
            statement.setBytes(3, securitySupport.encrypt(identityEncryptionSecret, identity.getOpenid()));
            statement.setBytes(4, nickname);
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("Wechat account insert did not return an ID");
        }
        return keyHolder.getKey().longValue();
    }

    private Long findParentUserId(long wechatAccountId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT parent_user_id AS parentUserId FROM school_wechat_account WHERE id = ?",
                wechatAccountId);
        return rows.isEmpty() ? null : nullableLong(rows.get(0), "parentUserId");
    }

    private void ensureParentEnabled(long parentUserId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND status = 1 AND is_deleted = 0",
                Integer.class,
                parentUserId);
        if (count == null || count == 0) {
            jdbcTemplate.update("UPDATE school_h5_session SET status = 0 WHERE parent_user_id = ?", parentUserId);
            throw new H5ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账户已停用，请联系学校");
        }
    }

    private List<Map<String, Object>> listStudents(long parentUserId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id AS studentId, s.student_name AS studentName, c.class_name AS className "
                        + "FROM school_student_parent sp "
                        + "JOIN school_student s ON s.id = sp.student_id AND s.status = 1 AND s.is_deleted = 0 "
                        + "JOIN school_class c ON c.id = s.class_id AND c.status = 1 AND c.is_deleted = 0 "
                        + "WHERE sp.parent_user_id = ? ORDER BY sp.is_primary DESC, s.student_no, s.id",
                parentUserId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> student = new LinkedHashMap<>();
            student.put("studentId", row.get("studentId"));
            student.put("studentNameMasked", securitySupport.maskName(String.valueOf(row.get("studentName"))));
            student.put("className", row.get("className"));
            result.add(student);
        }
        return result;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new H5ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FEATURE_MAINTENANCE", "成绩查询维护中");
        }
    }

    private H5ApiException invalidConfirmation() {
        return new H5ApiException(
                HttpStatus.BAD_REQUEST,
                "LOGIN_CONFIRMATION_INVALID",
                "登录确认已失效，请重新进入");
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/h5")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", 0L);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is unavailable", exception);
        }
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Missing numeric field " + key);
        }
        return ((Number) value).longValue();
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private int intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
