/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.http.HttpStatus
 *  org.springframework.http.ResponseEntity
 *  org.springframework.web.bind.MethodArgumentNotValidException
 *  org.springframework.web.bind.annotation.ExceptionHandler
 *  org.springframework.web.bind.annotation.RestControllerAdvice
 */
package com.xinshi.admin.interfaces.web;

import com.xinshi.admin.domain.shared.DomainException;
import com.xinshi.admin.interfaces.web.security.ForbiddenException;
import com.xinshi.admin.interfaces.web.security.UnauthorizedException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value={DomainException.class})
    public ResponseEntity<Map<String, String>> handleDomainException(DomainException exception) {
        log.warn("业务异常: {}", (Object)exception.getMessage());
        return ResponseEntity.status((HttpStatus)HttpStatus.BAD_REQUEST).body(this.error(exception.getMessage()));
    }

    @ExceptionHandler(value={MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst().map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage()).orElse("Invalid request");
        log.warn("参数校验失败: {}", (Object)message);
        return ResponseEntity.status((HttpStatus)HttpStatus.BAD_REQUEST).body(this.error(message));
    }

    @ExceptionHandler(value={IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("参数或业务校验异常: {}", (Object)exception.getMessage(), (Object)exception);
        return ResponseEntity.status((HttpStatus)HttpStatus.BAD_REQUEST).body(this.error(exception.getMessage()));
    }

    @ExceptionHandler(value={IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException exception) {
        log.error("状态异常: {}", (Object)exception.getMessage(), (Object)exception);
        return ResponseEntity.status((HttpStatus)HttpStatus.INTERNAL_SERVER_ERROR).body(this.error(exception.getMessage()));
    }

    @ExceptionHandler(value={UnauthorizedException.class})
    public ResponseEntity<Map<String, String>> handleUnauthorizedException(UnauthorizedException exception) {
        log.warn("未授权访问: {}", (Object)exception.getMessage());
        return ResponseEntity.status((HttpStatus)HttpStatus.UNAUTHORIZED).body(this.error(exception.getMessage()));
    }

    @ExceptionHandler(value={ForbiddenException.class})
    public ResponseEntity<Map<String, String>> handleForbiddenException(ForbiddenException exception) {
        log.warn("权限不足: {}", (Object)exception.getMessage());
        return ResponseEntity.status((HttpStatus)HttpStatus.FORBIDDEN).body(this.error(exception.getMessage()));
    }

    @ExceptionHandler(value={Exception.class})
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception exception) {
        log.error("未预期的异常: {}", (Object)exception.getMessage(), (Object)exception);
        return ResponseEntity.status((HttpStatus)HttpStatus.INTERNAL_SERVER_ERROR).body(this.error("服务器内部错误，请稍后重试"));
    }

    private Map<String, String> error(String message) {
        HashMap<String, String> body = new HashMap<String, String>();
        body.put("message", message);
        return body;
    }
}

