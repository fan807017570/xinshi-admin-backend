package com.xinshi.admin.interfaces.web.h5;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * H5 API exception carrying a stable public error code.
 *
 * @author Codex
 * @date 2026-08-31
 */
public class H5ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> data;

    public H5ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Collections.emptyMap());
    }

    public H5ApiException(HttpStatus status, String code, String message, Map<String, Object> data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data == null ? Collections.emptyMap() : data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
