package com.xinshi.admin.interfaces.web.h5;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts H5 domain failures to the stable public response contract.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.xinshi.admin.interfaces.web.h5")
public class H5ExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(H5ExceptionHandler.class);

    @ExceptionHandler(H5ApiException.class)
    public ResponseEntity<Map<String, Object>> handle(H5ApiException exception) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        log.warn("H5 request rejected, code={}, traceId={}", exception.getCode(), traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", exception.getCode());
        body.put("message", exception.getMessage());
        body.put("traceId", traceId);
        if (!exception.getData().isEmpty()) {
            body.put("data", exception.getData());
        }
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数错误");
        return error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        log.error("Unexpected H5 request failure, traceId={}", traceId, exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂不可用，请稍后重试", traceId);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message, String knownTraceId) {
        String traceId = knownTraceId == null ? UUID.randomUUID().toString().replace("-", "") : knownTraceId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        body.put("traceId", traceId);
        return ResponseEntity.status(status).body(body);
    }
}
