package com.xinshi.admin.application.h5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinshi.admin.infrastructure.security.H5SecuritySupport;
import com.xinshi.admin.interfaces.dto.h5.H5QueryContextResolveRequest;
import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests server-owned query-context candidate resolution and consumption.
 *
 * @author Codex
 * @date 2026-08-31
 */
class H5QueryContextServiceTest {
    private static final String IDENTITY_SECRET = "identity-secret-at-least-sixteen";
    private static final String ENCRYPTION_SECRET = "encryption-secret-at-least-sixteen";
    private static final String SIGNING_SECRET = "signing-secret-at-least-sixteen";
    private static final long WECHAT_ACCOUNT_ID = 21L;
    private static final long PARENT_USER_ID = 31L;

    private final H5SecuritySupport securitySupport = new H5SecuritySupport();
    private TestJdbcTemplate jdbcTemplate;
    private H5QueryContextService service;
    private boolean transactionActive;
    private boolean lastTransactionCompletedNormally;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new TestJdbcTemplate();
        service = new H5QueryContextService(
                jdbcTemplate,
                immediateTransactionTemplate(),
                securitySupport,
                new ObjectMapper(),
                IDENTITY_SECRET,
                ENCRYPTION_SECRET,
                SIGNING_SECRET,
                300L,
                "xinshi-rag");
    }

    @Test
    void resolveSelectionAcceptsOnlyCurrentTermCandidate() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Collections.singletonList(student(1001L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Arrays.asList(
                scope(12L, "第一学期", 3L, "期末"),
                scope(13L, "第二学期", 3L, "期末")));

        H5QueryContextResolution resolution = service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, 12L, null);

        assertEquals("READY", resolution.getFlowState());
        assertEquals(1001L, resolution.getQueryPreset().get("resolvedStudentId"));
        assertEquals(12L, resolution.getQueryPreset().get("resolvedAcademicTermId"));
        assertFalse(jdbcTemplate.pending);
        assertEquals(WECHAT_ACCOUNT_ID, jdbcTemplate.lastLockWechatAccountId);
        assertEquals(PARENT_USER_ID, jdbcTemplate.lastLockParentUserId);
        assertTrue(jdbcTemplate.lastScopeSql.contains("s.status = 1"));
        assertTrue(jdbcTemplate.lastScopeSql.contains("t.status = 1"));
        assertTrue(jdbcTemplate.lastScopeSql.contains("cs.status = 1"));
        assertTrue(jdbcTemplate.lastScopeSql.contains("cs.academic_term_id = r.academic_term_id"));
        assertTrue(jdbcTemplate.lastScopeSql.contains("c.id = s.class_id"));
        assertTrue(jdbcTemplate.lastScopeSql.contains("su.status = 1"));
        assertTrue(jdbcTemplate.lastScopeSql.contains("et.status = 1"));
        assertTrue(jdbcTemplate.lastScopeSql.contains("r.status = 2"));
    }

    @Test
    void resolveSelectionRejectsTermOutsideRecomputedCandidates() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Collections.singletonList(student(1001L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Arrays.asList(
                scope(12L, "第一学期", 3L, "期末"),
                scope(13L, "第二学期", 3L, "期末")));

        H5ApiException exception = assertThrows(
                H5ApiException.class,
                () -> service.resolveSelection(WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, 99L, null));

        assertEquals("QUERY_CONTEXT_SELECTION_INVALID", exception.getCode());
        assertTrue(jdbcTemplate.pending);
    }

    @Test
    void resolveSelectionRejectsStudentOutsideNameMatchedCandidates() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Arrays.asList(
                student(1001L, "张三"),
                student(1002L, "张三"),
                student(1003L, "李四"));

        H5ApiException exception = assertThrows(
                H5ApiException.class,
                () -> service.resolveSelection(WECHAT_ACCOUNT_ID, PARENT_USER_ID, 1003L, null, null));

        assertEquals("QUERY_CONTEXT_SELECTION_INVALID", exception.getCode());
        assertTrue(jdbcTemplate.pending);
    }

    @Test
    void resolveSelectionAdvancesStudentTermAndExamOneDimensionAtATime() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Arrays.asList(student(1001L, "张三"), student(1002L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Arrays.asList(
                scope(12L, "第一学期", 3L, "期中"),
                scope(12L, "第一学期", 4L, "期末"),
                scope(13L, "第二学期", 4L, "期末")));

        H5QueryContextResolution termResolution = service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, 1001L, null, null);
        assertEquals("TERM_SELECTION_REQUIRED", termResolution.getFlowState());
        assertEquals(2, termResolution.getCandidates().size());
        assertEquals(1001L, jdbcTemplate.context.get("resolvedStudentId"));
        assertNull(jdbcTemplate.context.get("resolvedAcademicTermId"));
        assertTrue(jdbcTemplate.pending);

        H5QueryContextResolution examResolution = service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, 12L, null);
        assertEquals("EXAM_SELECTION_REQUIRED", examResolution.getFlowState());
        assertEquals(2, examResolution.getCandidates().size());
        assertEquals(12L, jdbcTemplate.context.get("resolvedAcademicTermId"));
        assertTrue(jdbcTemplate.pending);

        H5QueryContextResolution readyResolution = service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, null, 4L);
        assertEquals("READY", readyResolution.getFlowState());
        assertEquals(4L, readyResolution.getQueryPreset().get("resolvedExamTypeId"));
        assertFalse(jdbcTemplate.pending);
    }

    @Test
    void resolveSelectionRejectsSkippingTheCurrentDimensionAtEveryStage() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Arrays.asList(student(1001L, "张三"), student(1002L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Arrays.asList(
                scope(12L, "第一学期", 3L, "期中"),
                scope(12L, "第一学期", 4L, "期末"),
                scope(13L, "第二学期", 4L, "期末")));

        assertSelectionInvalid(() -> service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, 12L, 3L));

        H5QueryContextResolution termResolution = service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, 1001L, null, null);
        assertEquals("TERM_SELECTION_REQUIRED", termResolution.getFlowState());
        assertSelectionInvalid(() -> service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, 12L, 3L));

        H5QueryContextResolution examResolution = service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, 12L, null);
        assertEquals("EXAM_SELECTION_REQUIRED", examResolution.getFlowState());
        assertSelectionInvalid(() -> service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, 1001L, null, 4L));

        H5QueryContextResolution readyResolution = service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, null, 4L);
        assertEquals("READY", readyResolution.getFlowState());
        assertFalse(jdbcTemplate.pending);
    }

    @Test
    void resolveForParentPersistsInferredStateAcrossRepeatedCallsWithoutRewritingIt() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Collections.singletonList(student(1001L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Arrays.asList(
                scope(12L, "第一学期", 3L, "期末"),
                scope(13L, "第二学期", 3L, "期末")));

        H5QueryContextResolution first = service.resolveForParent(WECHAT_ACCOUNT_ID, PARENT_USER_ID);
        int updatesAfterFirstResolution = jdbcTemplate.contextUpdateCount;
        H5QueryContextResolution second = service.resolveForParent(WECHAT_ACCOUNT_ID, PARENT_USER_ID);

        assertEquals("TERM_SELECTION_REQUIRED", first.getFlowState());
        assertEquals("TERM_SELECTION_REQUIRED", second.getFlowState());
        assertEquals(1001L, jdbcTemplate.context.get("resolvedStudentId"));
        assertEquals(updatesAfterFirstResolution, jdbcTemplate.contextUpdateCount);
        assertTrue(jdbcTemplate.pending);
    }

    @Test
    void resolveSelectionRejectsEmptyPayloadAndDtoRejectsMissingOrNonPositiveSelection() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Collections.singletonList(student(1001L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Collections.singletonList(
                scope(12L, "第一学期", 3L, "期末")));

        assertSelectionInvalid(() -> service.resolveSelection(
                WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, null, null));

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        H5QueryContextResolveRequest request = new H5QueryContextResolveRequest();
        assertFalse(validator.validate(request).isEmpty());
        request.setStudentId(0L);
        assertFalse(validator.validate(request).isEmpty());
        request.setStudentId(1001L);
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void resolveSelectionRejectsRepeatedSubmissionAndUsesIdentityBoundRowLock() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Collections.singletonList(student(1001L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Collections.singletonList(
                scope(12L, "第一学期", 3L, "期末")));

        service.resolveForParent(WECHAT_ACCOUNT_ID, PARENT_USER_ID);
        H5ApiException exception = assertThrows(
                H5ApiException.class,
                () -> service.resolveSelection(WECHAT_ACCOUNT_ID, PARENT_USER_ID, 1001L, null, null));

        assertEquals("QUERY_CONTEXT_EXPIRED", exception.getCode());
        assertTrue(jdbcTemplate.lastLockSql.contains("FOR UPDATE"));
        assertTrue(jdbcTemplate.lastLockSql.contains("wa.appid = c.appid"));
        assertTrue(jdbcTemplate.lastLockSql.contains("wa.openid_hmac = c.openid_hmac"));
    }

    @Test
    void resolveSelectionRejectsConcurrentStateChangeWhenAtomicConsumeLosesRace() {
        jdbcTemplate.context = context("张三");
        jdbcTemplate.students = Collections.singletonList(student(1001L, "张三"));
        jdbcTemplate.scopesByStudent.put(1001L, Arrays.asList(
                scope(12L, "第一学期", 3L, "期末"),
                scope(13L, "第二学期", 3L, "期末")));
        jdbcTemplate.failConsumeUpdate = true;

        H5ApiException exception = assertThrows(
                H5ApiException.class,
                () -> service.resolveSelection(WECHAT_ACCOUNT_ID, PARENT_USER_ID, null, 12L, null));

        assertEquals("QUERY_CONTEXT_EXPIRED", exception.getCode());
        assertTrue(jdbcTemplate.lastConsumeSql.contains("status = 'PENDING'"));
        assertTrue(jdbcTemplate.lastConsumeSql.contains("expires_at > CURRENT_TIMESTAMP"));
    }

    @Test
    void lookupClarifyingContextReturnsOnlyBooleanWithoutChangingContext() {
        jdbcTemplate.clarifyingCount = 1;
        Map<String, Object> found = lookupClarifying("nonce-found");
        jdbcTemplate.clarifyingCount = 0;
        Map<String, Object> missing = lookupClarifying("nonce-missing");

        assertEquals(Collections.singletonMap("hasClarifyingContext", true), found);
        assertEquals(Collections.singletonMap("hasClarifyingContext", false), missing);
        assertEquals(2, jdbcTemplate.nonceInsertCount);
        assertEquals(0, jdbcTemplate.contextUpdateCount);
        assertTrue(jdbcTemplate.lastLookupSql.contains("app.status = 1"));
        assertTrue(jdbcTemplate.lastLookupSql.contains("app.score_query_enabled = 1"));
        assertTrue(jdbcTemplate.lastLookupSql.contains("c.status = 'CLARIFYING'"));
        assertTrue(jdbcTemplate.lastLookupSql.contains("c.expires_at > CURRENT_TIMESTAMP"));
        assertEquals("wx-test", jdbcTemplate.lastLookupAppid);
        assertEquals(
                securitySupport.hmacSha256(IDENTITY_SECRET, "openid:wx-test:openid-test"),
                jdbcTemplate.lastLookupOpenidHmac);
    }

    @Test
    void lookupClarifyingRejectsReplayAndKeepsNonceUntilFutureDatedSignatureExpires() {
        long futureRequestTime = System.currentTimeMillis() / 1000L + 240L;
        Map<String, Object> response = lookupClarifying("nonce-replay", futureRequestTime);

        assertEquals(Collections.singletonMap("hasClarifyingContext", false), response);
        assertTrue(jdbcTemplate.lastNonceExpiresAt.getTime()
                >= (futureRequestTime + 300L) * 1000L);
        H5ApiException replay = assertThrows(
                H5ApiException.class,
                () -> lookupClarifying("nonce-replay", futureRequestTime));
        assertEquals("INTERNAL_REQUEST_REPLAYED", replay.getCode());
        assertEquals(1, jdbcTemplate.nonceInsertCount);
    }

    @Test
    void termNoOnlyLookupAndStoreDoesNotReportMissingTimeRange() {
        jdbcTemplate.clarifyingCount = 1;
        jdbcTemplate.clarifyingSlotsCiphertext = securitySupport.encrypt(ENCRYPTION_SECRET, "{}");

        assertEquals(
                Collections.singletonMap("hasClarifyingContext", true),
                lookupClarifying("nonce-term-lookup"));

        String bodyText = "{\"appid\":\"wx-test\",\"openid\":\"openid-test\","
                + "\"msgId\":\"msg-term-only\",\"intent\":\"SCORE_QUERY\","
                + "\"contextStatus\":\"CLARIFYING\",\"missingSlotCodes\":[\"STUDENT_NAME\",\"EXAM_TYPE\"],"
                + "\"slots\":{\"termNo\":1}}";
        Map<String, Object> response = signedStore(
                bodyText.getBytes(StandardCharsets.UTF_8),
                "nonce-term-store",
                System.currentTimeMillis() / 1000L);

        assertEquals("CLARIFYING", response.get("contextStatus"));
        assertEquals(Arrays.asList("STUDENT_NAME", "EXAM_TYPE"), response.get("missingSlotCodes"));
        assertEquals(1, ((Number) map(response.get("slots")).get("termNo")).intValue());
        assertEquals(1, jdbcTemplate.insertedTermNo.intValue());
        assertEquals(1, ((Number) jdbcTemplate.insertedSlots.get("termNo")).intValue());
    }

    @Test
    void storeMergesAndPersistsTermNoFromClarifyingContext() {
        jdbcTemplate.clarifyingSlotsCiphertext = securitySupport.encrypt(
                ENCRYPTION_SECRET,
                "{\"termNo\":2}");
        String bodyText = "{\"appid\":\"wx-test\",\"openid\":\"openid-test\","
                + "\"msgId\":\"msg-merged-term\",\"intent\":\"SCORE_QUERY\","
                + "\"contextStatus\":\"CLARIFYING\",\"missingSlotCodes\":[\"TIME_RANGE\"],"
                + "\"slots\":{\"studentName\":\"张三\",\"examTypeCode\":\"FINAL\"}}";

        Map<String, Object> response = signedStore(
                bodyText.getBytes(StandardCharsets.UTF_8),
                "nonce-merged-term",
                System.currentTimeMillis() / 1000L);

        assertEquals("PENDING", response.get("contextStatus"));
        assertEquals(Collections.emptyList(), response.get("missingSlotCodes"));
        assertEquals(2, ((Number) map(response.get("slots")).get("termNo")).intValue());
        assertEquals(2, jdbcTemplate.insertedTermNo.intValue());
        assertEquals(2, ((Number) jdbcTemplate.insertedSlots.get("termNo")).intValue());
    }

    @Test
    void internalSignatureHashesExactNonAsciiRawBodyBytes() {
        jdbcTemplate.clarifyingCount = 1;
        String bodyText = "{\"appid\":\"微信测试\",\"openid\":\"用户甲\","
                + "\"operation\":\"LOOKUP_CLARIFYING\"}";
        byte[] encodedBody = bodyText.getBytes(StandardCharsets.UTF_16LE);
        byte[] rawBody = new byte[encodedBody.length + 2];
        rawBody[0] = (byte) 0xff;
        rawBody[1] = (byte) 0xfe;
        System.arraycopy(encodedBody, 0, rawBody, 2, encodedBody.length);

        Map<String, Object> response = signedStore(
                rawBody,
                "nonce-non-ascii",
                System.currentTimeMillis() / 1000L);

        assertEquals(Collections.singletonMap("hasClarifyingContext", true), response);
        assertEquals("微信测试", jdbcTemplate.lastLookupAppid);
        assertEquals(
                securitySupport.hmacSha256(IDENTITY_SECRET, "openid:微信测试:用户甲"),
                jdbcTemplate.lastLookupOpenidHmac);
    }

    @Test
    void resolveForParentCommitsRejectedStatusBeforeReturningError() {
        jdbcTemplate.context = context("李四");
        jdbcTemplate.students = Collections.singletonList(student(1001L, "张三"));

        H5ApiException exception = assertThrows(
                H5ApiException.class,
                () -> service.resolveForParent(WECHAT_ACCOUNT_ID, PARENT_USER_ID));

        assertEquals("STUDENT_NAME_NOT_BOUND", exception.getCode());
        assertTrue(lastTransactionCompletedNormally);
        assertFalse(jdbcTemplate.pending);
    }

    private Map<String, Object> lookupClarifying(String nonce) {
        return lookupClarifying(nonce, System.currentTimeMillis() / 1000L);
    }

    private Map<String, Object> lookupClarifying(String nonce, long requestTime) {
        String bodyText = "{\"appid\":\"wx-test\",\"openid\":\"openid-test\","
                + "\"operation\":\"LOOKUP_CLARIFYING\"}";
        byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
        return signedStore(body, nonce, requestTime);
    }

    private Map<String, Object> signedStore(byte[] body, String nonce, long requestTime) {
        String timestamp = String.valueOf(requestTime);
        String canonical = "xinshi-rag\n" + timestamp + "\n" + nonce + "\n" + sha256Hex(body);
        String signature = securitySupport.hmacSha256(SIGNING_SECRET, canonical);
        return service.storeInternal(body, "xinshi-rag", timestamp, nonce, signature);
    }

    private String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(item & 0x0f, 16));
            }
            return result.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 initialization failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private void assertSelectionInvalid(org.junit.jupiter.api.function.Executable executable) {
        H5ApiException exception = assertThrows(H5ApiException.class, executable);
        assertEquals("QUERY_CONTEXT_SELECTION_INVALID", exception.getCode());
        assertTrue(jdbcTemplate.pending);
    }

    private Map<String, Object> context(String studentName) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", 91L);
        context.put("studentNameCiphertext", securitySupport.encrypt(ENCRYPTION_SECRET, studentName));
        context.put("yearValue", null);
        context.put("academicYear", null);
        context.put("termNo", null);
        context.put("examTypeCode", null);
        context.put("resolvedStudentId", null);
        context.put("resolvedAcademicTermId", null);
        context.put("resolvedExamTypeId", null);
        return context;
    }

    private Map<String, Object> student(long id, String name) {
        Map<String, Object> student = new LinkedHashMap<>();
        student.put("studentId", id);
        student.put("studentName", name);
        student.put("studentNo", "202600" + id);
        student.put("className", "初二（3）班");
        return student;
    }

    private Map<String, Object> scope(long termId, String termName, long examId, String examName) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("academicTermId", termId);
        scope.put("termName", termName);
        scope.put("examTypeId", examId);
        scope.put("examTypeName", examName);
        return scope;
    }

    private TransactionTemplate immediateTransactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactionActive = true;
                lastTransactionCompletedNormally = false;
                try {
                    T result = action.doInTransaction(new SimpleTransactionStatus());
                    lastTransactionCompletedNormally = true;
                    return result;
                } finally {
                    transactionActive = false;
                }
            }
        };
    }

    private final class TestJdbcTemplate extends JdbcTemplate {
        private Map<String, Object> context;
        private List<Map<String, Object>> students = new ArrayList<>();
        private final Map<Long, List<Map<String, Object>>> scopesByStudent = new LinkedHashMap<>();
        private boolean pending = true;
        private boolean failConsumeUpdate;
        private int clarifyingCount;
        private int nonceInsertCount;
        private int contextUpdateCount;
        private String lastLockSql = "";
        private String lastConsumeSql = "";
        private String lastScopeSql = "";
        private long lastLockWechatAccountId;
        private long lastLockParentUserId;
        private String lastLookupSql = "";
        private String lastLookupAppid = "";
        private String lastLookupOpenidHmac = "";
        private Timestamp lastNonceExpiresAt;
        private byte[] clarifyingSlotsCiphertext;
        private Integer insertedTermNo;
        private Map<String, Object> insertedSlots = Collections.emptyMap();
        private final Set<String> nonceKeys = new HashSet<>();

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.startsWith("SELECT slots_ciphertext AS slotsCiphertext")) {
                if (clarifyingSlotsCiphertext == null) {
                    return Collections.emptyList();
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("slotsCiphertext", clarifyingSlotsCiphertext);
                return Collections.singletonList(row);
            }
            if (sql.startsWith("SELECT id FROM school_score_query_context WHERE msg_id_hash")) {
                return Collections.emptyList();
            }
            if (sql.contains("FROM school_score_query_context c") && sql.contains("FOR UPDATE")) {
                if (!transactionActive) {
                    throw new AssertionError("Context row lock must execute inside TransactionTemplate");
                }
                lastLockSql = sql;
                lastLockWechatAccountId = ((Number) args[0]).longValue();
                lastLockParentUserId = ((Number) args[1]).longValue();
                if (!pending || context == null) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(new LinkedHashMap<>(context));
            }
            if (sql.contains("FROM school_student_parent sp")) {
                return copyRows(students);
            }
            if (sql.contains("SELECT DISTINCT t.id AS academicTermId")) {
                lastScopeSql = sql;
                Long studentId = ((Number) args[0]).longValue();
                return copyRows(scopesByStudent.getOrDefault(studentId, Collections.emptyList()));
            }
            throw new AssertionError("Unexpected queryForList SQL: " + sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("c.status = 'CLARIFYING'")) {
                lastLookupSql = sql;
                lastLookupAppid = String.valueOf(args[0]);
                lastLookupOpenidHmac = String.valueOf(args[1]);
                return requiredType.cast(Integer.valueOf(clarifyingCount));
            }
            throw new AssertionError("Unexpected queryForObject SQL: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO school_internal_request_nonce")) {
                String nonceKey = args[0] + ":" + args[1];
                if (!nonceKeys.add(nonceKey)) {
                    throw new DuplicateKeyException("duplicate nonce");
                }
                nonceInsertCount++;
                lastNonceExpiresAt = (Timestamp) args[2];
                return 1;
            }
            if (sql.contains("status = 'CONSUMED'")) {
                contextUpdateCount++;
                lastConsumeSql = sql;
                if (!pending || failConsumeUpdate) {
                    return 0;
                }
                context.put("resolvedStudentId", args[0]);
                context.put("resolvedAcademicTermId", args[1]);
                context.put("resolvedExamTypeId", args[2]);
                pending = false;
                return 1;
            }
            if (sql.contains("resolved_exam_type_id = NULL")) {
                contextUpdateCount++;
                if (!pending) {
                    return 0;
                }
                context.put("resolvedStudentId", args[0]);
                context.put("resolvedAcademicTermId", args[1]);
                context.put("resolvedExamTypeId", null);
                return 1;
            }
            if (sql.contains("status = 'REJECTED'")) {
                contextUpdateCount++;
                pending = false;
                return 1;
            }
            if (sql.startsWith("UPDATE school_score_query_context SET status = 'SUPERSEDED'")) {
                return 1;
            }
            if (sql.startsWith("INSERT INTO school_score_query_context")) {
                insertedTermNo = (Integer) args[9];
                byte[] ciphertext = (byte[]) args[12];
                String plaintext = securitySupport.decrypt(ENCRYPTION_SECRET, ciphertext);
                try {
                    insertedSlots = new ObjectMapper().readValue(
                            plaintext,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                } catch (java.io.IOException exception) {
                    throw new AssertionError("Unable to decode inserted slots", exception);
                }
                return 1;
            }
            throw new AssertionError("Unexpected update SQL: " + sql);
        }

        private List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
            List<Map<String, Object>> copies = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                copies.add(new LinkedHashMap<>(row));
            }
            return copies;
        }
    }
}
