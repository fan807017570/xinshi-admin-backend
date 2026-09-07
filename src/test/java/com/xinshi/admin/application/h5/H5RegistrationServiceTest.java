package com.xinshi.admin.application.h5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xinshi.admin.infrastructure.security.H5RequestContext;
import com.xinshi.admin.infrastructure.security.H5SecuritySupport;
import com.xinshi.admin.interfaces.dto.h5.H5RegistrationRequest;
import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class H5RegistrationServiceTest {
    private static final long SESSION_ID = 1L;
    private static final long WECHAT_ACCOUNT_ID = 2L;
    private static final String MOBILE = "13800138000";
    private static final String CODE = "123456";
    private static final String CODE_SECRET = "code-secret-for-tests";
    private static final String STUDENT_SECRET = "student-secret-for-tests";
    private static final String GUARDIAN_SECRET = "guardian-secret-tests";

    private H5SecuritySupport securitySupport;
    private RegistrationJdbcTemplate database;
    private SerialTransactionManager transactionManager;
    private H5RegistrationService service;

    @BeforeEach
    void setUp() {
        securitySupport = new H5SecuritySupport();
        database = new RegistrationJdbcTemplate(securitySupport);
        transactionManager = new SerialTransactionManager(database);
        H5QueryContextService contextService = mock(H5QueryContextService.class);
        when(contextService.resolveForParent(anyLong(), anyLong())).thenReturn(
                new H5QueryContextResolution("READY", Collections.emptyMap(), Collections.emptyList()));
        service = new H5RegistrationService(
                database,
                new TransactionTemplate(transactionManager),
                securitySupport,
                contextService,
                CODE_SECRET,
                STUDENT_SECRET,
                GUARDIAN_SECRET,
                "test",
                CODE);
        setRequestContext();
    }

    @AfterEach
    void tearDown() {
        H5RequestContext.clear();
    }

    @Test
    void concurrentReuseConsumesCodeOnlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<String> failures = Collections.synchronizedList(new ArrayList<String>());
        Thread first = registrationThread(start, successes, failures);
        Thread second = registrationThread(start, successes, failures);

        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();

        assertEquals(1, successes.get());
        assertEquals(1, failures.size());
        assertTrue(failures.contains("ALREADY_REGISTERED") || failures.contains("VERIFY_CODE_INVALID"));
        assertEquals("USED", database.verificationStatus);
        assertEquals(1, database.verificationConsumeCount);
    }

    @Test
    void wrongCodeIncrementsAttemptWithoutCreatingBusinessData() {
        H5RegistrationRequest request = validRequest();
        request.setVerificationCode("999999");

        H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(request));

        assertEquals("VERIFY_CODE_INVALID", exception.getCode());
        assertEquals(1, database.verificationAttempts);
        assertEquals("SENT", database.verificationStatus);
        assertEquals(0, database.identityFailCount);
        assertNoBusinessData();
    }

    @Test
    void fifthWrongCodeLocksOnlyVerificationRecord() {
        H5RegistrationRequest request = validRequest();
        request.setVerificationCode("999999");

        for (int attempt = 1; attempt <= 5; attempt++) {
            H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(request));
            assertEquals("VERIFY_CODE_INVALID", exception.getCode());
        }

        assertEquals(5, database.verificationAttempts);
        assertEquals("LOCKED", database.verificationStatus);
        assertEquals(0, database.identityFailCount);
        assertNoBusinessData();
    }

    @Test
    void locksSessionThenVerificationUsingForUpdate() {
        service.register(validRequest());

        int sessionLock = database.indexOfQuery("FROM school_h5_session");
        int verificationLock = database.indexOfQuery("FROM school_mobile_verification");
        int accountLock = database.indexOfQuery("FROM school_wechat_account");
        assertTrue(sessionLock >= 0);
        assertTrue(sessionLock < verificationLock);
        assertTrue(verificationLock < accountLock);
        assertTrue(database.querySql.get(sessionLock).contains("id=? AND wechat_account_id=?"));
        assertTrue(database.querySql.get(sessionLock).contains("FOR UPDATE"));
        assertTrue(database.querySql.get(verificationLock).contains("FOR UPDATE"));
        assertTrue(database.querySql.get(accountLock).contains("FOR UPDATE"));
    }

    @Test
    void mismatchedSessionAndWechatAccountFailsBeforeVerificationAccess() {
        database.identityFailCount = 2;
        H5RequestContext.set(new H5RequestContext.SessionIdentity(
                SESSION_ID, WECHAT_ACCOUNT_ID + 1, null, "wx-app", "csrf-hash"));

        H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(validRequest()));

        assertEquals("H5_SESSION_EXPIRED", exception.getCode());
        assertEquals(-1, database.indexOfQuery("FROM school_mobile_verification"));
        assertEquals(0, database.verificationAttempts);
        assertEquals("SENT", database.verificationStatus);
        assertEquals(0, database.verificationConsumeCount);
        assertEquals(2, database.identityFailCount);
        assertTrue(database.updateSql.isEmpty());
        assertNoBusinessData();
    }

    @Test
    void studentFailureIsCommittedWithoutConsumingCode() {
        database.studentValid = false;

        H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(validRequest()));

        assertEquals("STUDENT_VERIFY_FAILED", exception.getCode());
        assertEquals(1, database.identityFailCount);
        assertEquals("SENT", database.verificationStatus);
        assertEquals(1, transactionManager.getTransactionCount());
        assertNoBusinessData();
    }

    @Test
    void guardianFailurePrecedesIdCheckAndDoesNotCountAsIdentityFailure() {
        database.guardianValid = false;
        database.studentIdHmacValid = false;

        H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(validRequest()));

        assertEquals("GUARDIAN_AUTH_REQUIRED", exception.getCode());
        assertEquals(0, database.identityFailCount);
        assertEquals(0, database.verificationAttempts);
        assertEquals("SENT", database.verificationStatus);
        assertNoBusinessData();
    }

    @Test
    void missingStudentIdDigestDoesNotCountAsIdentityFailure() {
        database.studentIdHmacPresent = false;

        H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(validRequest()));

        assertEquals("STUDENT_ID_NOT_READY", exception.getCode());
        assertEquals(0, database.identityFailCount);
        assertEquals("SENT", database.verificationStatus);
        assertNoBusinessData();
    }

    @Test
    void idMismatchUsesUnifiedStudentFailureAndCountsIdentityFailure() {
        database.studentIdHmacValid = false;

        H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(validRequest()));

        assertEquals("STUDENT_VERIFY_FAILED", exception.getCode());
        assertEquals(1, database.identityFailCount);
        assertEquals("SENT", database.verificationStatus);
        assertNoBusinessData();
    }

    @Test
    void fifthIdentityFailureFreezesSessionForThirtyMinutes() {
        database.studentValid = false;
        for (int attempt = 1; attempt <= 5; attempt++) {
            setRequestContext();
            H5ApiException exception = assertThrows(H5ApiException.class, () -> service.register(validRequest()));
            assertEquals("STUDENT_VERIFY_FAILED", exception.getCode());
        }

        assertEquals(5, database.identityFailCount);
        assertNotNull(database.identityLockedUntil);
        assertTrue(database.identityLockedUntil.isAfter(LocalDateTime.now().plusMinutes(29)));

        setRequestContext();
        H5ApiException locked = assertThrows(H5ApiException.class, () -> service.register(validRequest()));
        assertEquals("TOO_MANY_REQUESTS", locked.getCode());
        assertEquals(5, database.identityFailCount);
    }

    @Test
    void expiredFreezeAllowsRegistration() {
        database.identityFailCount = 5;
        database.identityLockedUntil = LocalDateTime.now().minusSeconds(1);

        H5RegistrationService.RegistrationResult result = service.register(validRequest());

        assertEquals("READY", result.getData().get("flowState"));
        assertEquals(0, database.identityFailCount);
        assertEquals(null, database.identityLockedUntil);
        assertEquals("USED", database.verificationStatus);
    }

    @Test
    void successfulRegistrationClearsPreviousFailures() {
        database.identityFailCount = 3;

        service.register(validRequest());

        assertEquals(0, database.identityFailCount);
        assertEquals(null, database.identityLockedUntil);
        assertEquals(Long.valueOf(database.parentUserId), database.sessionParentUserId);
    }

    @Test
    void consumeUsesConditionalSentToUsedUpdate() {
        service.register(validRequest());

        String consumeSql = database.firstUpdateContaining("UPDATE school_mobile_verification SET status=");
        assertTrue(consumeSql.contains("WHERE id=?"));
        assertTrue(consumeSql.contains("status=?"));
        assertTrue(consumeSql.contains("verify_attempts<?"));
        assertTrue(consumeSql.contains("expires_at>CURRENT_TIMESTAMP"));
        assertEquals(1, database.verificationConsumeCount);
    }

    @Test
    void failedConditionalConsumeRollsBackAllBusinessWrites() {
        database.failVerificationConsume = true;

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> service.register(validRequest()));

        assertTrue(exception.getMessage().contains("consume mobile verification expected one affected row"));
        assertEquals("SENT", database.verificationStatus);
        assertEquals(0, database.verificationConsumeCount);
        assertNoBusinessData();
        assertEquals(false, database.parentRoleSaved);
    }

    private Thread registrationThread(
            CountDownLatch start,
            AtomicInteger successes,
            List<String> failures) {
        return new Thread(() -> {
            try {
                start.await();
                setRequestContext();
                service.register(validRequest());
                successes.incrementAndGet();
            } catch (H5ApiException exception) {
                failures.add(exception.getCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures.add("INTERRUPTED");
            } finally {
                H5RequestContext.clear();
            }
        });
    }

    private void setRequestContext() {
        H5RequestContext.set(new H5RequestContext.SessionIdentity(
                SESSION_ID, WECHAT_ACCOUNT_ID, null, "wx-app", "csrf-hash"));
    }

    private H5RegistrationRequest validRequest() {
        H5RegistrationRequest request = new H5RegistrationRequest();
        request.setStudentName("张三");
        request.setMobile(MOBILE);
        request.setVerificationCode(CODE);
        request.setStudentNo("S001");
        request.setIdCardLast4("123x");
        request.setAgreementVersion("v1");
        return request;
    }

    private void assertNoBusinessData() {
        assertEquals(null, database.sessionParentUserId);
        assertEquals(null, database.accountParentUserId);
        assertEquals(false, database.studentBound);
        assertEquals(false, database.agreementSaved);
    }

    private static final class SerialTransactionManager implements PlatformTransactionManager {
        private final ReentrantLock transactionLock = new ReentrantLock();
        private final RegistrationJdbcTemplate database;
        private final ThreadLocal<RegistrationJdbcTemplate.Snapshot> snapshot =
                new ThreadLocal<RegistrationJdbcTemplate.Snapshot>();
        private final AtomicInteger transactionCount = new AtomicInteger();

        private SerialTransactionManager(RegistrationJdbcTemplate database) {
            this.database = database;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            transactionLock.lock();
            transactionCount.incrementAndGet();
            snapshot.set(database.snapshot());
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            snapshot.remove();
            transactionLock.unlock();
        }

        private int getTransactionCount() {
            return transactionCount.get();
        }

        @Override
        public void rollback(TransactionStatus status) {
            database.restore(snapshot.get());
            snapshot.remove();
            transactionLock.unlock();
        }
    }

    private static final class RegistrationJdbcTemplate extends JdbcTemplate {
        private final H5SecuritySupport securitySupport;
        private final long parentUserId = 100L;
        private int identityFailCount;
        private LocalDateTime identityLockedUntil;
        private Long sessionParentUserId;
        private Long accountParentUserId;
        private int verificationAttempts;
        private String verificationStatus = "SENT";
        private int verificationConsumeCount;
        private boolean studentValid = true;
        private boolean guardianValid = true;
        private boolean studentIdHmacPresent = true;
        private boolean studentIdHmacValid = true;
        private boolean studentBound;
        private boolean agreementSaved;
        private boolean parentRoleSaved;
        private boolean failVerificationConsume;
        private final List<String> querySql = Collections.synchronizedList(new ArrayList<String>());
        private final List<String> updateSql = Collections.synchronizedList(new ArrayList<String>());

        private RegistrationJdbcTemplate(H5SecuritySupport securitySupport) {
            this.securitySupport = securitySupport;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return queryForList(sql, new Object[0]);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            querySql.add(sql);
            if (sql.contains("FROM school_h5_session")) {
                if (((Number) args[0]).longValue() != SESSION_ID
                        || ((Number) args[1]).longValue() != WECHAT_ACCOUNT_ID) {
                    return Collections.emptyList();
                }
                Map<String, Object> row = row("id", SESSION_ID);
                row.put("parentUserId", sessionParentUserId);
                row.put("failCount", identityFailCount);
                row.put("lockedUntil", identityLockedUntil == null ? null : Timestamp.valueOf(identityLockedUntil));
                return rows(row);
            }
            if (sql.contains("FROM school_mobile_verification")) {
                Map<String, Object> row = row("id", 11L);
                row.put("codeHmac", securitySupport.hmacSha256(
                        CODE_SECRET, SESSION_ID + ":" + MOBILE + ":" + CODE));
                row.put("status", verificationStatus);
                row.put("attempts", verificationAttempts);
                row.put("expiresAt", Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));
                return rows(row);
            }
            if (sql.contains("FROM school_wechat_account")) {
                Map<String, Object> row = row("id", WECHAT_ACCOUNT_ID);
                row.put("parentUserId", accountParentUserId);
                return rows(row);
            }
            if (sql.contains("FROM school_student s")) {
                if (!studentValid) {
                    return Collections.emptyList();
                }
                Map<String, Object> row = row("id", 10L);
                row.put("studentName", "张三");
                row.put("idHmac", studentIdHmacPresent
                        ? securitySupport.hmacSha256(
                                STUDENT_SECRET,
                                studentIdHmacValid ? "S001:123X" : "S001:9999")
                        : null);
                return rows(row);
            }
            if (sql.contains("FROM school_student_guardian_contact")) {
                return guardianValid ? rows(row("id", 20L)) : Collections.emptyList();
            }
            if (sql.contains("FROM sys_user")) {
                Map<String, Object> row = row("id", parentUserId);
                row.put("status", 1);
                return rows(row);
            }
            if (sql.contains("FROM sys_role")) {
                return rows(row("id", 7L));
            }
            return Collections.emptyList();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            Integer value = 0;
            if (sql.contains("FROM sys_user_role")) {
                value = parentRoleSaved ? 1 : 0;
            } else if (sql.contains("FROM school_student_parent")) {
                value = studentBound ? 1 : 0;
            } else if (sql.contains("FROM school_parent_agreement")) {
                value = agreementSaved ? 1 : 0;
            }
            return requiredType.cast(value);
        }

        @Override
        public int update(String sql, Object... args) {
            updateSql.add(sql);
            if (sql.contains("SET verify_attempts=?")) {
                if (!"SENT".equals(verificationStatus)
                        || verificationAttempts != ((Number) args[3]).intValue()) {
                    return 0;
                }
                verificationAttempts = ((Number) args[0]).intValue();
                verificationStatus = String.valueOf(args[1]);
                return 1;
            }
            if (sql.contains("SET identity_fail_count=0")) {
                if (identityFailCount != ((Number) args[1]).intValue()) {
                    return 0;
                }
                identityFailCount = 0;
                identityLockedUntil = null;
                return 1;
            }
            if (sql.contains("SET identity_fail_count=?")) {
                if (identityFailCount != ((Number) args[3]).intValue()) {
                    return 0;
                }
                identityFailCount = ((Number) args[0]).intValue();
                identityLockedUntil = args[1] == null ? null : ((Timestamp) args[1]).toLocalDateTime();
                return 1;
            }
            if (sql.startsWith("INSERT IGNORE INTO sys_user_role")) {
                boolean existed = parentRoleSaved;
                parentRoleSaved = true;
                return existed ? 0 : 1;
            }
            if (sql.startsWith("UPDATE school_wechat_account")) {
                if (accountParentUserId != null) {
                    return 0;
                }
                accountParentUserId = ((Number) args[0]).longValue();
                return 1;
            }
            if (sql.startsWith("INSERT INTO school_student_parent")) {
                studentBound = true;
                return 1;
            }
            if (sql.startsWith("INSERT IGNORE INTO school_parent_agreement")) {
                boolean existed = agreementSaved;
                agreementSaved = true;
                return existed ? 0 : 1;
            }
            if (sql.contains("SET status=?,verified_at=CURRENT_TIMESTAMP")) {
                if (failVerificationConsume || !"SENT".equals(verificationStatus)) {
                    return 0;
                }
                verificationStatus = String.valueOf(args[0]);
                verificationConsumeCount++;
                return 1;
            }
            if (sql.contains("SET token_hash=?")) {
                if (sessionParentUserId != null) {
                    return 0;
                }
                sessionParentUserId = ((Number) args[2]).longValue();
                identityFailCount = 0;
                identityLockedUntil = null;
                return 1;
            }
            throw new AssertionError("Unexpected update SQL: " + sql);
        }

        private int indexOfQuery(String fragment) {
            for (int i = 0; i < querySql.size(); i++) {
                if (querySql.get(i).contains(fragment)) {
                    return i;
                }
            }
            return -1;
        }

        private String firstUpdateContaining(String fragment) {
            for (String sql : updateSql) {
                if (sql.contains(fragment)) {
                    return sql;
                }
            }
            throw new AssertionError("Update SQL not found: " + fragment);
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    identityFailCount,
                    identityLockedUntil,
                    sessionParentUserId,
                    accountParentUserId,
                    verificationAttempts,
                    verificationStatus,
                    verificationConsumeCount,
                    studentBound,
                    agreementSaved,
                    parentRoleSaved);
        }

        private void restore(Snapshot state) {
            if (state == null) {
                return;
            }
            identityFailCount = state.identityFailCount;
            identityLockedUntil = state.identityLockedUntil;
            sessionParentUserId = state.sessionParentUserId;
            accountParentUserId = state.accountParentUserId;
            verificationAttempts = state.verificationAttempts;
            verificationStatus = state.verificationStatus;
            verificationConsumeCount = state.verificationConsumeCount;
            studentBound = state.studentBound;
            agreementSaved = state.agreementSaved;
            parentRoleSaved = state.parentRoleSaved;
        }

        private static final class Snapshot {
            private final int identityFailCount;
            private final LocalDateTime identityLockedUntil;
            private final Long sessionParentUserId;
            private final Long accountParentUserId;
            private final int verificationAttempts;
            private final String verificationStatus;
            private final int verificationConsumeCount;
            private final boolean studentBound;
            private final boolean agreementSaved;
            private final boolean parentRoleSaved;

            private Snapshot(
                    int identityFailCount,
                    LocalDateTime identityLockedUntil,
                    Long sessionParentUserId,
                    Long accountParentUserId,
                    int verificationAttempts,
                    String verificationStatus,
                    int verificationConsumeCount,
                    boolean studentBound,
                    boolean agreementSaved,
                    boolean parentRoleSaved) {
                this.identityFailCount = identityFailCount;
                this.identityLockedUntil = identityLockedUntil;
                this.sessionParentUserId = sessionParentUserId;
                this.accountParentUserId = accountParentUserId;
                this.verificationAttempts = verificationAttempts;
                this.verificationStatus = verificationStatus;
                this.verificationConsumeCount = verificationConsumeCount;
                this.studentBound = studentBound;
                this.agreementSaved = agreementSaved;
                this.parentRoleSaved = parentRoleSaved;
            }
        }

        private static Map<String, Object> row(String key, Object value) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(key, value);
            return row;
        }

        private static List<Map<String, Object>> rows(Map<String, Object> row) {
            return Collections.singletonList(row);
        }
    }
}
