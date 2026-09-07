package com.xinshi.admin.interfaces.web.h5;

import java.util.LinkedHashMap;
import java.util.Map;

/** H5 public response envelope. */
public final class H5Responses {
    private H5Responses() {
    }

    public static Map<String, Object> success(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return response;
    }
}
