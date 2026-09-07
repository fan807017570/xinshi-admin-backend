package com.xinshi.admin.application.h5;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinshi.admin.infrastructure.security.H5SecuritySupport;
import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Persists, merges and resolves short-lived WeChat score query contexts.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Service
public class H5QueryContextService {
    private static final String LOOKUP_CLARIFYING_OPERATION = "LOOKUP_CLARIFYING";
    private static final String PENDING_CONTEXT_COLUMNS =
            "SELECT c.id, c.student_name_ciphertext AS studentNameCiphertext, c.year_value AS yearValue, "
                    + "c.year_mode AS yearMode, c.academic_year AS academicYear, c.term_no AS termNo, "
                    + "c.exam_type_code AS examTypeCode, c.subject_name AS subjectName, "
                    + "c.slots_ciphertext AS slotsCiphertext, c.resolved_student_id AS resolvedStudentId, "
                    + "c.resolved_academic_term_id AS resolvedAcademicTermId, "
                    + "c.resolved_exam_type_id AS resolvedExamTypeId ";
    private static final String PENDING_CONTEXT_SQL = PENDING_CONTEXT_COLUMNS
            + "FROM school_score_query_context c "
            + "JOIN school_wechat_account wa ON wa.id = c.wechat_account_id AND wa.appid = c.appid "
            + "AND wa.openid_hmac = c.openid_hmac AND wa.status = 1 "
            + "WHERE wa.id = ? AND c.status = 'PENDING' AND c.expires_at > CURRENT_TIMESTAMP "
            + "ORDER BY c.created_at DESC, c.id DESC LIMIT 1";
    private static final String PENDING_CONTEXT_FOR_UPDATE_SQL = PENDING_CONTEXT_COLUMNS
            + "FROM school_score_query_context c "
            + "JOIN school_wechat_account wa ON wa.id = c.wechat_account_id AND wa.appid = c.appid "
            + "AND wa.openid_hmac = c.openid_hmac AND wa.status = 1 "
            + "WHERE wa.id = ? AND wa.parent_user_id = ? AND c.status = 'PENDING' "
            + "AND c.expires_at > CURRENT_TIMESTAMP "
            + "ORDER BY c.created_at DESC, c.id DESC LIMIT 1 FOR UPDATE";
    private static final String LOOKUP_CLARIFYING_SQL =
            "SELECT COUNT(*) FROM school_score_query_context c "
                    + "JOIN school_wechat_app app ON app.appid = c.appid "
                    + "AND app.status = 1 AND app.score_query_enabled = 1 "
                    + "WHERE c.appid = ? AND c.openid_hmac = ? AND c.status = 'CLARIFYING' "
                    + "AND c.expires_at > CURRENT_TIMESTAMP";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final H5SecuritySupport securitySupport;
    private final ObjectMapper objectMapper;
    private final String identitySecret;
    private final String encryptionSecret;
    private final String internalSigningSecret;
    private final long maxClockSkewSeconds;
    private final String expectedServiceId;

    public H5QueryContextService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            H5SecuritySupport securitySupport,
            ObjectMapper objectMapper,
            @Value("${h5.query-context.identity-hmac-secret:}") String identitySecret,
            @Value("${h5.query-context.encryption-secret:}") String encryptionSecret,
            @Value("${internal.xinshi-rag.signing-secret:}") String internalSigningSecret,
            @Value("${internal.request.max-clock-skew-seconds:300}") long maxClockSkewSeconds,
            @Value("${internal.xinshi-rag.service-id:xinshi-rag}") String expectedServiceId) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.securitySupport = securitySupport;
        this.objectMapper = objectMapper;
        this.identitySecret = identitySecret;
        this.encryptionSecret = encryptionSecret;
        this.internalSigningSecret = internalSigningSecret;
        this.maxClockSkewSeconds = maxClockSkewSeconds;
        this.expectedServiceId = expectedServiceId;
    }

    public Map<String, Object> storeInternal(
            byte[] body,
            String serviceId,
            String timestamp,
            String nonce,
            String signature) {
        verifyInternalSignature(body, serviceId, timestamp, nonce, signature);
        Map<String, Object> request = readJson(body);
        String operation = optionalString(request.get("operation"));
        if (LOOKUP_CLARIFYING_OPERATION.equals(operation)) {
            return lookupClarifyingContext(request);
        }
        if (StringUtils.hasText(operation)) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "operation 非法");
        }
        return transactionTemplate.execute(status -> storeInternalInTransaction(request, serviceId, nonce));
    }

    public void attachLatestContext(String appid, String openid, long wechatAccountId) {
        String openidHmac = securitySupport.hmacSha256(identitySecret, "openid:" + appid + ":" + openid);
        jdbcTemplate.update(
                "UPDATE school_score_query_context SET wechat_account_id = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE appid = ? AND openid_hmac = ? AND status IN ('PENDING','CLARIFYING') "
                        + "AND expires_at > CURRENT_TIMESTAMP",
                wechatAccountId,
                appid,
                openidHmac);
    }

    public Map<String, Object> pendingPreset(long wechatAccountId) {
        Map<String, Object> context = first(jdbcTemplate.queryForList(PENDING_CONTEXT_SQL, wechatAccountId));
        if (context.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> preset = basePreset(context);
        preset.put("resolutionState", "PENDING");
        return preset;
    }

    public H5QueryContextResolution resolveForParent(long wechatAccountId, long parentUserId) {
        H5ApiException[] committedRejection = new H5ApiException[1];
        H5QueryContextResolution resolution = transactionTemplate.execute(status -> {
            Map<String, Object> context = lockLatestPendingContext(wechatAccountId, parentUserId);
            if (context.isEmpty()) {
                return new H5QueryContextResolution("READY", Collections.emptyMap(), Collections.emptyList());
            }
            try {
                return resolveContext(context, parentUserId, null, null, null, false);
            } catch (H5ApiException exception) {
                if (isCommittedRejection(exception)) {
                    committedRejection[0] = exception;
                    return null;
                }
                throw exception;
            }
        });
        if (committedRejection[0] != null) {
            throw committedRejection[0];
        }
        return resolution;
    }

    public H5QueryContextResolution resolveSelection(
            long wechatAccountId,
            long parentUserId,
            Long studentId,
            Long academicTermId,
            Long examTypeId) {
        return transactionTemplate.execute(status -> {
            Map<String, Object> context = lockLatestPendingContext(wechatAccountId, parentUserId);
            if (context.isEmpty()) {
                throw contextExpired();
            }
            if (!hasSubmittedSelection(studentId, academicTermId, examTypeId)) {
                throw selectionInvalid();
            }
            return resolveContext(
                    context,
                    parentUserId,
                    studentId,
                    academicTermId,
                    examTypeId,
                    true);
        });
    }

    private Map<String, Object> lookupClarifyingContext(Map<String, Object> request) {
        if (request.size() != 3
                || !request.containsKey("appid")
                || !request.containsKey("openid")
                || !request.containsKey("operation")) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "澄清上下文查询参数非法");
        }
        String appid = requiredString(request, "appid");
        String openid = requiredString(request, "openid");
        String openidHmac = securitySupport.hmacSha256(identitySecret, "openid:" + appid + ":" + openid);
        Integer count = jdbcTemplate.queryForObject(
                LOOKUP_CLARIFYING_SQL,
                Integer.class,
                appid,
                openidHmac);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hasClarifyingContext", count != null && count > 0);
        return response;
    }

    private Map<String, Object> lockLatestPendingContext(long wechatAccountId, long parentUserId) {
        return first(jdbcTemplate.queryForList(
                PENDING_CONTEXT_FOR_UPDATE_SQL,
                wechatAccountId,
                parentUserId));
    }

    private H5QueryContextResolution resolveContext(
            Map<String, Object> context,
            long parentUserId,
            Long submittedStudentId,
            Long submittedTermId,
            Long submittedExamTypeId,
            boolean acceptSelection) {
        long contextId = longValue(context, "id");
        List<Map<String, Object>> matchedStudents = matchStudents(context, listBoundStudents(parentUserId));
        if (matchedStudents.isEmpty()) {
            handleStudentNotMatched(contextId, acceptSelection);
        }

        boolean selectionApplied = false;
        Long studentId = nullableLong(context, "resolvedStudentId");
        if (studentId != null) {
            requireCandidate(matchedStudents, "studentId", studentId);
        } else if (matchedStudents.size() > 1) {
            if (!acceptSelection) {
                return candidateResolution(context, "STUDENT_SELECTION_REQUIRED", null, null, matchedStudents);
            }
            requireOnlyStudentSelection(submittedStudentId, submittedTermId, submittedExamTypeId);
            requireCandidate(matchedStudents, "studentId", submittedStudentId);
            studentId = submittedStudentId;
            selectionApplied = true;
        } else {
            studentId = longValue(matchedStudents.get(0), "studentId");
        }

        List<Map<String, Object>> scopes = listPublishedScopes(studentId, context);
        if (scopes.isEmpty()) {
            handleScopeNotMatched(contextId, studentId, acceptSelection);
        }
        List<Map<String, Object>> terms = termCandidates(scopes);
        Long termId = nullableLong(context, "resolvedAcademicTermId");
        if (termId != null) {
            requireCandidate(terms, "academicTermId", termId);
        } else if (terms.size() > 1) {
            if (!acceptSelection || selectionApplied) {
                persistPendingSelection(context, contextId, studentId, null);
                return candidateResolution(context, "TERM_SELECTION_REQUIRED", studentId, null, terms);
            }
            requireOnlyTermSelection(submittedStudentId, submittedTermId, submittedExamTypeId);
            requireCandidate(terms, "academicTermId", submittedTermId);
            termId = submittedTermId;
            selectionApplied = true;
        } else {
            termId = longValue(terms.get(0), "academicTermId");
        }

        List<Map<String, Object>> exams = examCandidates(filterBy(scopes, "academicTermId", termId));
        Long examTypeId = nullableLong(context, "resolvedExamTypeId");
        if (examTypeId != null) {
            requireCandidate(exams, "examTypeId", examTypeId);
        } else if (exams.size() > 1) {
            if (!acceptSelection || selectionApplied) {
                persistPendingSelection(context, contextId, studentId, termId);
                return candidateResolution(context, "EXAM_SELECTION_REQUIRED", studentId, termId, exams);
            }
            requireOnlyExamSelection(submittedStudentId, submittedTermId, submittedExamTypeId);
            requireCandidate(exams, "examTypeId", submittedExamTypeId);
            examTypeId = submittedExamTypeId;
            selectionApplied = true;
        } else {
            examTypeId = longValue(exams.get(0), "examTypeId");
        }

        if (acceptSelection && !selectionApplied && hasSubmittedSelection(
                submittedStudentId, submittedTermId, submittedExamTypeId)) {
            throw selectionInvalid();
        }
        consumeContext(contextId, studentId, termId, examTypeId);
        return new H5QueryContextResolution(
                "READY",
                resolvedPreset(context, studentId, termId, examTypeId),
                Collections.emptyList());
    }

    private H5QueryContextResolution candidateResolution(
            Map<String, Object> context,
            String flowState,
            Long studentId,
            Long termId,
            List<Map<String, Object>> candidates) {
        Map<String, Object> preset = basePreset(context);
        if (studentId != null) {
            preset.put("resolvedStudentId", studentId);
        }
        if (termId != null) {
            preset.put("resolvedAcademicTermId", termId);
        }
        preset.put("resolutionState", flowState);
        return new H5QueryContextResolution(flowState, preset, candidates);
    }

    private void handleStudentNotMatched(long contextId, boolean acceptSelection) {
        if (acceptSelection) {
            throw selectionInvalid();
        }
        int updated = jdbcTemplate.update(
                "UPDATE school_score_query_context SET status = 'REJECTED', consumed_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP",
                contextId);
        if (updated != 1) {
            throw contextExpired();
        }
        throw new H5ApiException(
                HttpStatus.FORBIDDEN,
                "STUDENT_NAME_NOT_BOUND",
                "查询姓名与当前账号绑定学生不一致");
    }

    private void handleScopeNotMatched(long contextId, long studentId, boolean acceptSelection) {
        if (acceptSelection) {
            throw selectionInvalid();
        }
        int updated = jdbcTemplate.update(
                "UPDATE school_score_query_context SET resolved_student_id = ?, status = 'REJECTED', "
                        + "consumed_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING' "
                        + "AND expires_at > CURRENT_TIMESTAMP",
                studentId,
                contextId);
        if (updated != 1) {
            throw contextExpired();
        }
        throw new H5ApiException(HttpStatus.NOT_FOUND, "RESULT_NOT_FOUND", "未找到符合条件的已发布成绩");
    }

    private void persistPendingSelection(
            Map<String, Object> context,
            long contextId,
            long studentId,
            Long termId) {
        if (Objects.equals(nullableLong(context, "resolvedStudentId"), studentId)
                && Objects.equals(nullableLong(context, "resolvedAcademicTermId"), termId)
                && nullableLong(context, "resolvedExamTypeId") == null) {
            return;
        }
        int updated = jdbcTemplate.update(
                "UPDATE school_score_query_context SET resolved_student_id = ?, resolved_academic_term_id = ?, "
                        + "resolved_exam_type_id = NULL WHERE id = ? AND status = 'PENDING' "
                        + "AND expires_at > CURRENT_TIMESTAMP",
                studentId,
                termId,
                contextId);
        if (updated != 1) {
            throw contextExpired();
        }
        context.put("resolvedStudentId", studentId);
        context.put("resolvedAcademicTermId", termId);
        context.put("resolvedExamTypeId", null);
    }

    private void consumeContext(long contextId, long studentId, long termId, long examTypeId) {
        int updated = jdbcTemplate.update(
                "UPDATE school_score_query_context SET resolved_student_id = ?, resolved_academic_term_id = ?, "
                        + "resolved_exam_type_id = ?, status = 'CONSUMED', consumed_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP",
                studentId,
                termId,
                examTypeId,
                contextId);
        if (updated != 1) {
            throw contextExpired();
        }
    }

    private void requireOnlyStudentSelection(Long studentId, Long termId, Long examTypeId) {
        if (studentId == null || termId != null || examTypeId != null) {
            throw selectionInvalid();
        }
    }

    private void requireOnlyTermSelection(Long studentId, Long termId, Long examTypeId) {
        if (studentId != null || termId == null || examTypeId != null) {
            throw selectionInvalid();
        }
    }

    private void requireOnlyExamSelection(Long studentId, Long termId, Long examTypeId) {
        if (studentId != null || termId != null || examTypeId == null) {
            throw selectionInvalid();
        }
    }

    private boolean hasSubmittedSelection(Long studentId, Long termId, Long examTypeId) {
        return studentId != null || termId != null || examTypeId != null;
    }

    private void requireCandidate(List<Map<String, Object>> candidates, String key, Long selectedId) {
        if (selectedId == null) {
            throw selectionInvalid();
        }
        for (Map<String, Object> candidate : candidates) {
            if (selectedId.longValue() == longValue(candidate, key)) {
                return;
            }
        }
        throw selectionInvalid();
    }

    private H5ApiException selectionInvalid() {
        return new H5ApiException(
                HttpStatus.NOT_FOUND,
                "QUERY_CONTEXT_SELECTION_INVALID",
                "所选查询条件不可用，请重新进入查询");
    }

    private H5ApiException contextExpired() {
        return new H5ApiException(HttpStatus.CONFLICT, "QUERY_CONTEXT_EXPIRED", "查询条件已过期或已处理");
    }

    private boolean isCommittedRejection(H5ApiException exception) {
        return "STUDENT_NAME_NOT_BOUND".equals(exception.getCode())
                || "RESULT_NOT_FOUND".equals(exception.getCode());
    }

    private Map<String, Object> storeInternalInTransaction(
            Map<String, Object> request,
            String serviceId,
            String nonce) {
        String appid = requiredString(request, "appid");
        String openid = requiredString(request, "openid");
        String msgId = optionalString(request.get("msgId"));
        String incomingStatus = requiredString(request, "contextStatus");
        Map<String, Object> incomingSlots = mapValue(request.get("slots"));
        String openidHmac = securitySupport.hmacSha256(identitySecret, "openid:" + appid + ":" + openid);
        Map<String, Object> mergedSlots = mergeClarifyingSlots(appid, openidHmac, incomingSlots);
        List<String> missingCodes = missingCodes(mergedSlots);
        String status = missingCodes.isEmpty() ? "PENDING" : "CLARIFYING";
        if (!"PENDING".equals(incomingStatus) && !"CLARIFYING".equals(incomingStatus)) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "contextStatus 非法");
        }
        String msgIdHash = securitySupport.sha256(StringUtils.hasText(msgId) ? msgId : appid + ":" + nonce);
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM school_score_query_context WHERE msg_id_hash = ?",
                msgIdHash);
        if (!existing.isEmpty()) {
            return storedContextResponse(status, missingCodes, mergedSlots);
        }
        jdbcTemplate.update(
                "UPDATE school_score_query_context SET status = 'SUPERSEDED', updated_at = CURRENT_TIMESTAMP "
                        + "WHERE appid = ? AND openid_hmac = ? AND status IN ('PENDING','CLARIFYING')",
                appid,
                openidHmac);
        String studentName = optionalString(mergedSlots.get("studentName"));
        Timestamp expiresAt = Timestamp.valueOf(LocalDateTime.now().plusSeconds("PENDING".equals(status) ? 600 : 120));
        jdbcTemplate.update(
                "INSERT INTO school_score_query_context "
                        + "(appid, openid_hmac, msg_id_hash, intent, missing_slot_codes, student_name_ciphertext, "
                        + "student_name_hmac, year_value, year_mode, academic_year, term_no, exam_type_code, "
                        + "subject_name, slots_ciphertext, status, expires_at) "
                        + "VALUES (?, ?, ?, 'SCORE_QUERY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                appid,
                openidHmac,
                msgIdHash,
                String.join(",", missingCodes),
                studentName == null ? null : securitySupport.encrypt(encryptionSecret, studentName),
                studentName == null ? null : securitySupport.hmacSha256(identitySecret, "student-name:" + studentName),
                integerValue(mergedSlots.get("yearValue")),
                optionalString(mergedSlots.get("yearMode")),
                optionalString(mergedSlots.get("academicYear")),
                integerValue(mergedSlots.get("termNo")),
                optionalString(mergedSlots.get("examTypeCode")),
                optionalString(mergedSlots.get("subjectName")),
                securitySupport.encrypt(encryptionSecret, writeJson(mergedSlots)),
                status,
                expiresAt);
        return storedContextResponse(status, missingCodes, mergedSlots);
    }

    private void verifyInternalSignature(
            byte[] body,
            String serviceId,
            String timestamp,
            String nonce,
            String signature) {
        if (!StringUtils.hasText(serviceId)
                || !securitySupport.constantTimeEquals(expectedServiceId, serviceId)
                || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)) {
            throw new H5ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_SIGNATURE_INVALID", "内部请求签名无效");
        }
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new H5ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_SIGNATURE_INVALID", "内部请求签名无效");
        }
        long currentEpochSecond = System.currentTimeMillis() / 1000L;
        if (requestTime < currentEpochSecond - maxClockSkewSeconds
                || requestTime > currentEpochSecond + maxClockSkewSeconds) {
            throw new H5ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_SIGNATURE_EXPIRED", "内部请求签名已过期");
        }
        String bodyDigest = sha256Hex(body);
        String canonical = serviceId + "\n" + timestamp + "\n" + nonce + "\n" + bodyDigest;
        String expected = securitySupport.hmacSha256(internalSigningSecret, canonical);
        if (!securitySupport.constantTimeEquals(expected, signature)) {
            throw new H5ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_SIGNATURE_INVALID", "内部请求签名无效");
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO school_internal_request_nonce (service_id, nonce_hash, expires_at) "
                            + "VALUES (?, ?, ?)",
                    serviceId,
                    securitySupport.sha256(nonce),
                    Timestamp.from(Instant.ofEpochSecond(requestTime).plusSeconds(maxClockSkewSeconds)));
        } catch (DuplicateKeyException exception) {
            throw new H5ApiException(HttpStatus.CONFLICT, "INTERNAL_REQUEST_REPLAYED", "内部请求已处理");
        }
    }

    private Map<String, Object> mergeClarifyingSlots(
            String appid,
            String openidHmac,
            Map<String, Object> incoming) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT slots_ciphertext AS slotsCiphertext FROM school_score_query_context "
                        + "WHERE appid = ? AND openid_hmac = ? AND status = 'CLARIFYING' "
                        + "AND expires_at > CURRENT_TIMESTAMP ORDER BY created_at DESC, id DESC LIMIT 1",
                appid,
                openidHmac);
        Map<String, Object> merged = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            byte[] ciphertext = (byte[]) rows.get(0).get("slotsCiphertext");
            String previous = securitySupport.decrypt(encryptionSecret, ciphertext);
            if (StringUtils.hasText(previous)) {
                merged.putAll(readJson(previous.getBytes(StandardCharsets.UTF_8)));
            }
        }
        incoming.forEach((key, value) -> {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    private List<Map<String, Object>> listBoundStudents(long parentUserId) {
        return jdbcTemplate.queryForList(
                "SELECT s.id AS studentId, s.student_name AS studentName, s.student_no AS studentNo, "
                        + "c.class_name AS className FROM school_student_parent sp "
                        + "JOIN school_student s ON s.id = sp.student_id AND s.status = 1 AND s.is_deleted = 0 "
                        + "JOIN school_class c ON c.id = s.class_id AND c.status = 1 AND c.is_deleted = 0 "
                        + "WHERE sp.parent_user_id = ? ORDER BY s.student_no, s.id",
                parentUserId);
    }

    private List<Map<String, Object>> matchStudents(
            Map<String, Object> context,
            List<Map<String, Object>> students) {
        String requestedName = decryptName(context);
        if (!StringUtils.hasText(requestedName)) {
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (Map<String, Object> student : students) {
                candidates.add(toStudentCandidate(student));
            }
            return candidates;
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        String normalizedRequested = securitySupport.normalizeName(requestedName);
        for (Map<String, Object> student : students) {
            String actual = securitySupport.normalizeName(String.valueOf(student.get("studentName")));
            if (securitySupport.constantTimeEquals(normalizedRequested, actual)) {
                matched.add(toStudentCandidate(student));
            }
        }
        return matched;
    }

    private List<Map<String, Object>> listPublishedScopes(long studentId, Map<String, Object> context) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT t.id AS academicTermId, t.term_name AS termName, "
                        + "et.id AS examTypeId, et.exam_type_name AS examTypeName "
                        + "FROM school_student_course_result r "
                        + "JOIN school_student s ON s.id = r.student_id AND s.status = 1 AND s.is_deleted = 0 "
                        + "JOIN school_academic_term t ON t.id = r.academic_term_id AND t.status = 1 "
                        + "JOIN school_class_subject cs ON cs.id = r.class_subject_id AND cs.status = 1 "
                        + "AND cs.academic_term_id = r.academic_term_id "
                        + "JOIN school_class c ON c.id = cs.class_id AND c.id = s.class_id "
                        + "AND c.status = 1 AND c.is_deleted = 0 "
                        + "JOIN school_subject su ON su.id = cs.subject_id AND su.status = 1 "
                        + "JOIN school_exam_type et ON et.id = r.exam_type_id AND et.status = 1 "
                        + "WHERE r.student_id = ? AND r.status = 2");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        String examTypeCode = optionalString(context.get("examTypeCode"));
        if (StringUtils.hasText(examTypeCode)) {
            sql.append(" AND et.exam_type_code = ?");
            args.add(examTypeCode);
        }
        Integer year = integerValue(context.get("yearValue"));
        String academicYear = optionalString(context.get("academicYear"));
        if (StringUtils.hasText(academicYear)) {
            sql.append(" AND t.academic_year = ?");
            args.add(academicYear);
        } else if (year != null) {
            sql.append(" AND (t.academic_year LIKE ? OR YEAR(t.start_date) = ? OR YEAR(t.end_date) = ?)");
            args.add(year + "%");
            args.add(year);
            args.add(year);
        }
        Integer termNo = integerValue(context.get("termNo"));
        if (termNo != null) {
            sql.append(" AND t.term_code LIKE ?");
            args.add("%-" + termNo);
        }
        sql.append(" ORDER BY t.start_date DESC, t.id DESC, et.sort_order, et.id");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private Map<String, Object> toStudentCandidate(Map<String, Object> student) {
        String normalizedName = securitySupport.normalizeName(String.valueOf(student.get("studentName")));
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("studentId", student.get("studentId"));
        candidate.put("studentNameMasked", securitySupport.maskName(normalizedName));
        candidate.put("studentNoMasked", maskStudentNo(String.valueOf(student.get("studentNo"))));
        candidate.put("className", student.get("className"));
        return candidate;
    }

    private List<Map<String, Object>> termCandidates(List<Map<String, Object>> scopes) {
        Map<Long, Map<String, Object>> distinct = new LinkedHashMap<>();
        for (Map<String, Object> scope : scopes) {
            long id = longValue(scope, "academicTermId");
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("academicTermId", id);
            candidate.put("termName", scope.get("termName"));
            distinct.putIfAbsent(id, candidate);
        }
        return new ArrayList<>(distinct.values());
    }

    private List<Map<String, Object>> examCandidates(List<Map<String, Object>> scopes) {
        Map<Long, Map<String, Object>> distinct = new LinkedHashMap<>();
        for (Map<String, Object> scope : scopes) {
            long id = longValue(scope, "examTypeId");
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("examTypeId", id);
            candidate.put("examTypeName", scope.get("examTypeName"));
            distinct.putIfAbsent(id, candidate);
        }
        return new ArrayList<>(distinct.values());
    }

    private Map<String, Object> basePreset(Map<String, Object> context) {
        Map<String, Object> preset = new LinkedHashMap<>();
        preset.put("source", "WECHAT_TEXT");
        String studentName = decryptName(context);
        if (StringUtils.hasText(studentName)) {
            preset.put("studentNameMasked", securitySupport.maskName(studentName));
        }
        preset.put("requestedYear", context.get("yearValue"));
        preset.put("requestedExamTypeCode", context.get("examTypeCode"));
        return preset;
    }

    private Map<String, Object> resolvedPreset(
            Map<String, Object> context,
            long studentId,
            long termId,
            long examTypeId) {
        Map<String, Object> preset = basePreset(context);
        preset.put("studentMatched", true);
        preset.put("resolvedStudentId", studentId);
        preset.put("resolvedAcademicTermId", termId);
        preset.put("resolvedExamTypeId", examTypeId);
        preset.put("resolutionState", "RESOLVED");
        return preset;
    }

    private Map<String, Object> storedContextResponse(
            String status,
            List<String> missingCodes,
            Map<String, Object> slots) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("contextStatus", status);
        response.put("missingSlotCodes", missingCodes);
        response.put("slots", slots);
        return response;
    }

    private List<String> missingCodes(Map<String, Object> slots) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(optionalString(slots.get("studentName")))) {
            missing.add("STUDENT_NAME");
        }
        if (integerValue(slots.get("yearValue")) == null
                && !StringUtils.hasText(optionalString(slots.get("academicYear")))
                && integerValue(slots.get("termNo")) == null) {
            missing.add("TIME_RANGE");
        }
        if (!StringUtils.hasText(optionalString(slots.get("examTypeCode")))) {
            missing.add("EXAM_TYPE");
        }
        return missing;
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

    private String decryptName(Map<String, Object> context) {
        Object ciphertext = context.get("studentNameCiphertext");
        return ciphertext instanceof byte[] ? securitySupport.decrypt(encryptionSecret, (byte[]) ciphertext) : null;
    }

    private List<Map<String, Object>> filterBy(List<Map<String, Object>> rows, String key, long value) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (longValue(row, key) == value) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private Map<String, Object> readJson(byte[] body) {
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请求 JSON 格式错误");
        } catch (java.io.IOException exception) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请求 JSON 格式错误");
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Query context serialization failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
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

    private Integer integerValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value));
    }

    private String requiredString(Map<String, Object> request, String key) {
        String value = optionalString(request.get(key));
        if (!StringUtils.hasText(value)) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", key + " 不能为空");
        }
        return value;
    }

    private String optionalString(Object value) {
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? null : text;
    }

    private String maskStudentNo(String studentNo) {
        if (studentNo == null || studentNo.length() < 4) {
            return "***";
        }
        return studentNo.substring(0, 2) + "******" + studentNo.substring(studentNo.length() - 2);
    }
}
