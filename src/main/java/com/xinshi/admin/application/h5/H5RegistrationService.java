package com.xinshi.admin.application.h5;

import com.xinshi.admin.infrastructure.security.H5RequestContext;
import com.xinshi.admin.infrastructure.security.H5SecuritySupport;
import com.xinshi.admin.interfaces.dto.h5.H5RegistrationRequest;
import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Atomic H5 mobile verification, parent creation and student binding. */
@Service
public class H5RegistrationService {
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int MAX_IDENTITY_FAILURES = 5;
    private static final int IDENTITY_LOCK_MINUTES = 30;
    private static final String REGISTRATION_PURPOSE = "REGISTER";
    private static final String VERIFICATION_SENT = "SENT";
    private static final String VERIFICATION_USED = "USED";
    private static final String VERIFICATION_LOCKED = "LOCKED";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final H5SecuritySupport securitySupport;
    private final H5QueryContextService contextService;
    private final String codeSecret;
    private final String studentSecret;
    private final String guardianSecret;
    private final String smsMode;
    private final String testCode;

    public H5RegistrationService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            H5SecuritySupport securitySupport,
            H5QueryContextService contextService,
            @Value("${h5.sms.code-hmac-secret:}") String codeSecret,
            @Value("${h5.student-id.hmac-secret:}") String studentSecret,
            @Value("${h5.guardian-mobile.hmac-secret:}") String guardianSecret,
            @Value("${h5.sms.mode:disabled}") String smsMode,
            @Value("${h5.sms.test-code:}") String testCode) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.securitySupport = securitySupport;
        this.contextService = contextService;
        this.codeSecret = codeSecret;
        this.studentSecret = studentSecret;
        this.guardianSecret = guardianSecret;
        this.smsMode = smsMode;
        this.testCode = testCode;
    }

    public Map<String, Object> sendVerification(String rawMobile, String requestIp) {
        H5RequestContext.SessionIdentity identity = requireUnregistered();
        String mobile = securitySupport.normalizeMobile(rawMobile);
        if (!"test".equalsIgnoreCase(smsMode) || !testCode.matches("^\\d{6}$")) {
            throw new H5ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_DEPENDENCY_UNAVAILABLE", "短信服务暂不可用");
        }
        String ipHash = securitySupport.hmacSha256(codeSecret, "ip:" + String.valueOf(requestIp));
        if (count("SELECT COUNT(*) FROM school_mobile_verification WHERE mobile=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND)", mobile) > 0
                || count("SELECT COUNT(*) FROM school_mobile_verification WHERE mobile=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 HOUR)", mobile) >= 5
                || count("SELECT COUNT(*) FROM school_mobile_verification WHERE mobile=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY)", mobile) >= 10
                || count("SELECT COUNT(*) FROM school_mobile_verification WHERE h5_session_id=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 HOUR)", identity.getSessionId()) >= 10
                || count("SELECT COUNT(*) FROM school_mobile_verification WHERE request_ip_hash=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 HOUR)", ipHash) >= 30) {
            throw new H5ApiException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_TOO_FREQUENT", "请稍后再获取验证码");
        }
        jdbcTemplate.update(
                "UPDATE school_mobile_verification SET status='SUPERSEDED' WHERE h5_session_id=? AND status='SENT'",
                identity.getSessionId());
        int inserted = jdbcTemplate.update(
                "INSERT INTO school_mobile_verification(h5_session_id,mobile,request_ip_hash,code_hmac,purpose,status,verify_attempts,sent_at,expires_at) "
                        + "VALUES(?,?,?,?,'REGISTER','SENT',0,CURRENT_TIMESTAMP,?)",
                identity.getSessionId(), mobile, ipHash, codeHmac(identity.getSessionId(), mobile, testCode),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));
        requireSingleUpdate(inserted, "create mobile verification");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retryAfterSeconds", 60);
        data.put("expiresInSeconds", 300);
        return data;
    }

    public RegistrationResult register(H5RegistrationRequest request) {
        H5RequestContext.SessionIdentity identity = requireUnregistered();
        RegistrationInput input = normalizeInput(request);
        RegistrationAttempt attempt = transactionTemplate.execute(status ->
                registerInTransaction(identity, input));
        if (attempt == null) {
            throw new IllegalStateException("Registration transaction returned no result");
        }
        if (attempt.getFailure() != null) {
            throw attempt.getFailure();
        }
        RegistrationResult result = attempt.getResult();
        applyPendingQueryContext(identity, result);
        return result;
    }

    private RegistrationAttempt registerInTransaction(
            H5RequestContext.SessionIdentity identity,
            RegistrationInput input) {
        SessionState session = lockSession(identity.getSessionId(), identity.getWechatAccountId());
        VerificationState verification = lockVerification(identity.getSessionId(), input.getMobile());
        H5ApiException verificationFailure = validateVerification(verification, input);
        if (verificationFailure != null) {
            return RegistrationAttempt.failure(verificationFailure);
        }
        lockUnregisteredWechatAccount(identity.getWechatAccountId());

        StudentVerification studentVerification = verifyStudent(input);
        if (studentVerification.getFailure() != null) {
            if (studentVerification.shouldCountIdentityFailure()) {
                recordIdentityFailure(session);
            }
            return RegistrationAttempt.failure(studentVerification.getFailure());
        }

        long parentId = findOrCreateParent(input.getMobile());
        ensureParentRole(parentId);
        bindWechatAccount(identity.getWechatAccountId(), parentId);
        bindStudent(studentVerification.getStudentId(), parentId);
        saveAgreement(parentId, identity.getWechatAccountId(), input.getAgreementVersion());
        consumeVerification(verification);
        RegistrationResult result = rotateAndBindSession(
                identity.getSessionId(), input.getMobile(), studentVerification.getStudentId(), parentId);
        return RegistrationAttempt.success(result);
    }

    private RegistrationInput normalizeInput(H5RegistrationRequest request) {
        return new RegistrationInput(
                securitySupport.normalizeMobile(request.getMobile()),
                securitySupport.normalizeName(request.getStudentName()),
                request.getVerificationCode().trim(),
                request.getStudentNo().trim(),
                securitySupport.normalizeIdCardLast4(request.getIdCardLast4()),
                request.getAgreementVersion().trim());
    }

    private SessionState lockSession(long sessionId, long wechatAccountId) {
        Map<String, Object> row = first(jdbcTemplate.queryForList(
                "SELECT id,parent_user_id AS parentUserId,identity_fail_count AS failCount,"
                        + "identity_locked_until AS lockedUntil FROM school_h5_session "
                        + "WHERE id=? AND wechat_account_id=? AND status=1 "
                        + "AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",
                sessionId, wechatAccountId));
        if (row.isEmpty()) {
            throw new H5ApiException(HttpStatus.UNAUTHORIZED, "H5_SESSION_EXPIRED", "H5会话已失效");
        }
        if (row.get("parentUserId") != null) {
            throw new H5ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "当前微信已完成注册");
        }
        int failCount = intValue(row.get("failCount"));
        LocalDateTime lockedUntil = localDateTime(row.get("lockedUntil"));
        LocalDateTime now = LocalDateTime.now();
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            long retryAfter = Math.max(1L, Duration.between(now, lockedUntil).getSeconds());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("retryAfterSeconds", retryAfter);
            throw new H5ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "核验失败次数过多，请稍后重试", data);
        }
        if (lockedUntil != null || failCount >= MAX_IDENTITY_FAILURES) {
            int reset = jdbcTemplate.update(
                    "UPDATE school_h5_session SET identity_fail_count=0,identity_locked_until=NULL "
                            + "WHERE id=? AND status=1 AND parent_user_id IS NULL AND identity_fail_count=?",
                    sessionId, failCount);
            requireSingleUpdate(reset, "reset expired identity lock");
            failCount = 0;
        }
        return new SessionState(sessionId, failCount);
    }

    private VerificationState lockVerification(long sessionId, String mobile) {
        Map<String, Object> row = first(jdbcTemplate.queryForList(
                "SELECT id,code_hmac AS codeHmac,status,verify_attempts AS attempts,expires_at AS expiresAt "
                        + "FROM school_mobile_verification WHERE h5_session_id=? AND mobile=? AND purpose=? "
                        + "ORDER BY id DESC LIMIT 1 FOR UPDATE",
                sessionId, mobile, REGISTRATION_PURPOSE));
        if (row.isEmpty()) {
            return VerificationState.missing();
        }
        return new VerificationState(
                ((Number) row.get("id")).longValue(),
                sessionId,
                String.valueOf(row.get("codeHmac")),
                String.valueOf(row.get("status")),
                intValue(row.get("attempts")),
                localDateTime(row.get("expiresAt")));
    }

    private H5ApiException validateVerification(VerificationState verification, RegistrationInput input) {
        LocalDateTime now = LocalDateTime.now();
        boolean eligible = verification.isPresent()
                && VERIFICATION_SENT.equals(verification.getStatus())
                && verification.getAttempts() < MAX_VERIFICATION_ATTEMPTS
                && verification.getExpiresAt() != null
                && verification.getExpiresAt().isAfter(now);
        boolean hmacMatches = eligible && securitySupport.constantTimeEquals(
                codeHmac(verification.getSessionId(), input.getMobile(), input.getVerificationCode()),
                verification.getCodeHmac());
        if (hmacMatches) {
            return null;
        }
        if (eligible) {
            int nextAttempts = verification.getAttempts() + 1;
            String nextStatus = nextAttempts >= MAX_VERIFICATION_ATTEMPTS
                    ? VERIFICATION_LOCKED : VERIFICATION_SENT;
            int updated = jdbcTemplate.update(
                    "UPDATE school_mobile_verification SET verify_attempts=?,status=? "
                            + "WHERE id=? AND status='SENT' AND verify_attempts=?",
                    nextAttempts, nextStatus, verification.getId(), verification.getAttempts());
            requireSingleUpdate(updated, "record verification failure");
        }
        return new H5ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "VERIFY_CODE_INVALID", "验证码错误、过期或已使用");
    }

    private void lockUnregisteredWechatAccount(long wechatAccountId) {
        Map<String, Object> account = first(jdbcTemplate.queryForList(
                "SELECT id,parent_user_id AS parentUserId FROM school_wechat_account "
                        + "WHERE id=? AND status=1 FOR UPDATE",
                wechatAccountId));
        if (account.isEmpty()) {
            throw new H5ApiException(HttpStatus.FORBIDDEN, "WECHAT_ACCOUNT_DISABLED", "微信账户已停用");
        }
        if (account.get("parentUserId") != null) {
            throw new H5ApiException(HttpStatus.CONFLICT, "WECHAT_ACCOUNT_CONFLICT", "微信账户已绑定其他家长账号");
        }
    }

    private StudentVerification verifyStudent(RegistrationInput input) {
        List<Map<String, Object>> students = jdbcTemplate.queryForList(
                "SELECT s.id,s.student_name AS studentName,s.id_card_last4_hmac AS idHmac "
                        + "FROM school_student s JOIN school_class c ON c.id=s.class_id "
                        + "WHERE s.student_no=? AND s.status=1 AND s.is_deleted=0 "
                        + "AND c.status=1 AND c.is_deleted=0 LIMIT 2 FOR UPDATE",
                input.getStudentNo());
        if (students.size() != 1) {
            return StudentVerification.identityFailure(studentVerificationFailure());
        }
        Map<String, Object> student = students.get(0);
        boolean nameMatches = securitySupport.constantTimeEquals(
                input.getStudentName(), securitySupport.normalizeName(String.valueOf(student.get("studentName"))));
        if (!nameMatches) {
            return StudentVerification.identityFailure(studentVerificationFailure());
        }
        long studentId = ((Number) student.get("id")).longValue();
        String guardianHmac = securitySupport.hmacSha256(
                guardianSecret, studentId + ":" + input.getMobile());
        List<Map<String, Object>> guardians = jdbcTemplate.queryForList(
                "SELECT id FROM school_student_guardian_contact "
                        + "WHERE student_id=? AND mobile_hmac=? AND status=1 FOR UPDATE",
                studentId, guardianHmac);
        if (guardians.size() != 1) {
            return StudentVerification.nonIdentityFailure(new H5ApiException(
                    HttpStatus.FORBIDDEN, "GUARDIAN_AUTH_REQUIRED", "手机号未通过学校监护关系核验"));
        }
        if (student.get("idHmac") == null) {
            return StudentVerification.nonIdentityFailure(new H5ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "STUDENT_ID_NOT_READY", "学生证件摘要未维护，请联系学校"));
        }
        String expectedId = securitySupport.hmacSha256(
                studentSecret, input.getStudentNo() + ":" + input.getIdCardLast4());
        boolean idMatches = securitySupport.constantTimeEquals(
                expectedId, String.valueOf(student.get("idHmac")));
        if (!idMatches) {
            return StudentVerification.identityFailure(studentVerificationFailure());
        }
        return StudentVerification.success(studentId);
    }

    private H5ApiException studentVerificationFailure() {
        return new H5ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "STUDENT_VERIFY_FAILED", "学生信息核验失败，请检查后重试");
    }

    private void recordIdentityFailure(SessionState session) {
        int nextCount = session.getFailCount() + 1;
        Timestamp lockedUntil = nextCount >= MAX_IDENTITY_FAILURES
                ? Timestamp.valueOf(LocalDateTime.now().plusMinutes(IDENTITY_LOCK_MINUTES)) : null;
        int updated = jdbcTemplate.update(
                "UPDATE school_h5_session SET identity_fail_count=?,identity_locked_until=? "
                        + "WHERE id=? AND status=1 AND parent_user_id IS NULL AND identity_fail_count=?",
                nextCount, lockedUntil, session.getSessionId(), session.getFailCount());
        requireSingleUpdate(updated, "record identity failure");
    }

    private long findOrCreateParent(String mobile) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id,status FROM sys_user WHERE mobile=? AND is_deleted=0 FOR UPDATE", mobile);
        if (users.size() > 1) {
            throw new H5ApiException(HttpStatus.CONFLICT, "MOBILE_ACCOUNT_CONFLICT", "手机号对应多个账户，请联系学校");
        }
        if (!users.isEmpty()) {
            if (intValue(users.get(0).get("status")) != 1) {
                throw new H5ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账户已停用");
            }
            return ((Number) users.get(0).get("id")).longValue();
        }
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        String loginName = "wx_" + securitySupport.randomToken(15);
        String password = "{bcrypt}" + securitySupport.randomToken(32);
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sys_user(login_name,password_hash,real_name,mobile,status,is_deleted) VALUES(?,?,?,?,1,0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, loginName);
            statement.setString(2, password);
            statement.setString(3, "微信家长");
            statement.setString(4, mobile);
            return statement;
        }, holder);
        requireSingleUpdate(inserted, "create parent user");
        if (holder.getKey() == null) {
            throw new IllegalStateException("Created parent user has no generated id");
        }
        return holder.getKey().longValue();
    }

    private void ensureParentRole(long parentId) {
        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE role_code='PARENT' AND status=1 FOR UPDATE");
        if (roles.size() != 1) {
            throw new IllegalStateException("Active PARENT role is missing or duplicated");
        }
        long roleId = ((Number) roles.get(0).get("id")).longValue();
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES(?,?)", parentId, roleId);
        if (inserted == 0 && count(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND role_id=?", parentId, roleId) != 1) {
            throw new IllegalStateException("Parent role mapping was not persisted");
        }
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException("Unexpected parent role update count: " + inserted);
        }
    }

    private void bindWechatAccount(long wechatAccountId, long parentId) {
        int updated = jdbcTemplate.update(
                "UPDATE school_wechat_account SET parent_user_id=? "
                        + "WHERE id=? AND status=1 AND parent_user_id IS NULL",
                parentId, wechatAccountId);
        requireSingleUpdate(updated, "bind WeChat account");
    }

    private void bindStudent(long studentId, long parentId) {
        int updated = jdbcTemplate.update(
                "INSERT INTO school_student_parent(student_id,parent_user_id,relation_type,is_primary,binding_source,verified_at) "
                        + "VALUES(?,?,'guardian',0,'WECHAT_H5',CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE relation_type=VALUES(relation_type),binding_source='WECHAT_H5',"
                        + "verified_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP",
                studentId, parentId);
        if (updated < 0 || updated > 2 || count(
                "SELECT COUNT(*) FROM school_student_parent WHERE student_id=? AND parent_user_id=?",
                studentId, parentId) != 1) {
            throw new IllegalStateException("Student-parent binding was not persisted");
        }
    }

    private void saveAgreement(long parentId, long wechatAccountId, String agreementVersion) {
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO school_parent_agreement(parent_user_id,wechat_account_id,agreement_version,agreed_at) "
                        + "VALUES(?,?,?,CURRENT_TIMESTAMP)",
                parentId, wechatAccountId, agreementVersion);
        if (inserted == 0 && count(
                "SELECT COUNT(*) FROM school_parent_agreement WHERE parent_user_id=? AND agreement_version=?",
                parentId, agreementVersion) != 1) {
            throw new IllegalStateException("Parent agreement was not persisted");
        }
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException("Unexpected parent agreement update count: " + inserted);
        }
    }

    private void consumeVerification(VerificationState verification) {
        int updated = jdbcTemplate.update(
                "UPDATE school_mobile_verification SET status=?,verified_at=CURRENT_TIMESTAMP "
                        + "WHERE id=? AND status=? AND verify_attempts<? AND expires_at>CURRENT_TIMESTAMP",
                VERIFICATION_USED, verification.getId(), VERIFICATION_SENT, MAX_VERIFICATION_ATTEMPTS);
        requireSingleUpdate(updated, "consume mobile verification");
    }

    private RegistrationResult rotateAndBindSession(
            long sessionId, String mobile, long studentId, long parentId) {
        String token = securitySupport.randomToken(32);
        String csrf = securitySupport.randomToken(32);
        int updated = jdbcTemplate.update(
                "UPDATE school_h5_session SET token_hash=?,csrf_token_hash=?,parent_user_id=?,"
                        + "identity_fail_count=0,identity_locked_until=NULL,last_access_at=CURRENT_TIMESTAMP "
                        + "WHERE id=? AND status=1 AND parent_user_id IS NULL",
                securitySupport.sha256(token), securitySupport.sha256(csrf), parentId, sessionId);
        requireSingleUpdate(updated, "bind and rotate H5 session");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowState", "READY");
        data.put("mobileMasked", securitySupport.maskMobile(mobile));
        data.put("csrfToken", csrf);
        data.put("defaultStudentId", studentId);
        return new RegistrationResult(data, token, parentId);
    }

    private void applyPendingQueryContext(
            H5RequestContext.SessionIdentity identity,
            RegistrationResult result) {
        try {
            H5QueryContextResolution resolution = contextService.resolveForParent(
                    identity.getWechatAccountId(), result.getParentUserId());
            result.getData().put("flowState", resolution.getFlowState());
            if (!resolution.getQueryPreset().isEmpty()) {
                result.getData().put("queryPreset", resolution.getQueryPreset());
            }
            if (!resolution.getCandidates().isEmpty()) {
                result.getData().put("candidates", resolution.getCandidates());
            }
        } catch (H5ApiException exception) {
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("code", exception.getCode());
            warning.put("message", exception.getMessage());
            result.getData().put("queryContextWarning", warning);
        }
    }

    private H5RequestContext.SessionIdentity requireUnregistered() {
        H5RequestContext.SessionIdentity identity = H5RequestContext.require();
        if (identity.getParentUserId() != null) {
            throw new H5ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "当前微信已完成注册");
        }
        return identity;
    }

    private String codeHmac(long sessionId, String mobile, String code) {
        return securitySupport.hmacSha256(codeSecret, sessionId + ":" + mobile + ":" + code);
    }

    private int count(String sql, Object... values) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, values);
        return result == null ? 0 : result;
    }

    private Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? new LinkedHashMap<String, Object>() : rows.get(0);
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private LocalDateTime localDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    private void requireSingleUpdate(int updated, String operation) {
        if (updated != 1) {
            throw new IllegalStateException(operation + " expected one affected row but was " + updated);
        }
    }

    private static final class RegistrationInput {
        private final String mobile;
        private final String studentName;
        private final String verificationCode;
        private final String studentNo;
        private final String idCardLast4;
        private final String agreementVersion;

        private RegistrationInput(
                String mobile,
                String studentName,
                String verificationCode,
                String studentNo,
                String idCardLast4,
                String agreementVersion) {
            this.mobile = mobile;
            this.studentName = studentName;
            this.verificationCode = verificationCode;
            this.studentNo = studentNo;
            this.idCardLast4 = idCardLast4;
            this.agreementVersion = agreementVersion;
        }

        private String getMobile() { return mobile; }
        private String getStudentName() { return studentName; }
        private String getVerificationCode() { return verificationCode; }
        private String getStudentNo() { return studentNo; }
        private String getIdCardLast4() { return idCardLast4; }
        private String getAgreementVersion() { return agreementVersion; }
    }

    private static final class SessionState {
        private final long sessionId;
        private final int failCount;

        private SessionState(long sessionId, int failCount) {
            this.sessionId = sessionId;
            this.failCount = failCount;
        }

        private long getSessionId() { return sessionId; }
        private int getFailCount() { return failCount; }
    }

    private static final class VerificationState {
        private final long id;
        private final long sessionId;
        private final String codeHmac;
        private final String status;
        private final int attempts;
        private final LocalDateTime expiresAt;

        private VerificationState(
                long id,
                long sessionId,
                String codeHmac,
                String status,
                int attempts,
                LocalDateTime expiresAt) {
            this.id = id;
            this.sessionId = sessionId;
            this.codeHmac = codeHmac;
            this.status = status;
            this.attempts = attempts;
            this.expiresAt = expiresAt;
        }

        private VerificationState() {
            this.id = 0L;
            this.sessionId = 0L;
            this.codeHmac = null;
            this.status = null;
            this.attempts = 0;
            this.expiresAt = null;
        }

        private static VerificationState missing() { return new VerificationState(); }
        private boolean isPresent() { return id > 0L; }
        private long getId() { return id; }
        private long getSessionId() { return sessionId; }
        private String getCodeHmac() { return codeHmac; }
        private String getStatus() { return status; }
        private int getAttempts() { return attempts; }
        private LocalDateTime getExpiresAt() { return expiresAt; }
    }

    private static final class StudentVerification {
        private final long studentId;
        private final H5ApiException failure;
        private final boolean countIdentityFailure;

        private StudentVerification(
                long studentId,
                H5ApiException failure,
                boolean countIdentityFailure) {
            this.studentId = studentId;
            this.failure = failure;
            this.countIdentityFailure = countIdentityFailure;
        }

        private static StudentVerification success(long studentId) {
            return new StudentVerification(studentId, null, false);
        }

        private static StudentVerification identityFailure(H5ApiException failure) {
            return new StudentVerification(0L, failure, true);
        }

        private static StudentVerification nonIdentityFailure(H5ApiException failure) {
            return new StudentVerification(0L, failure, false);
        }

        private long getStudentId() { return studentId; }
        private H5ApiException getFailure() { return failure; }
        private boolean shouldCountIdentityFailure() { return countIdentityFailure; }
    }

    private static final class RegistrationAttempt {
        private final RegistrationResult result;
        private final H5ApiException failure;

        private RegistrationAttempt(RegistrationResult result, H5ApiException failure) {
            this.result = result;
            this.failure = failure;
        }

        private static RegistrationAttempt success(RegistrationResult result) {
            return new RegistrationAttempt(result, null);
        }

        private static RegistrationAttempt failure(H5ApiException failure) {
            return new RegistrationAttempt(null, failure);
        }

        private RegistrationResult getResult() { return result; }
        private H5ApiException getFailure() { return failure; }
    }

    public static final class RegistrationResult {
        private final Map<String, Object> data;
        private final String sessionToken;
        private final long parentUserId;

        public RegistrationResult(Map<String, Object> data, String sessionToken, long parentUserId) {
            this.data = data;
            this.sessionToken = sessionToken;
            this.parentUserId = parentUserId;
        }

        public Map<String, Object> getData() { return data; }
        public String getSessionToken() { return sessionToken; }
        public long getParentUserId() { return parentUserId; }
    }
}
