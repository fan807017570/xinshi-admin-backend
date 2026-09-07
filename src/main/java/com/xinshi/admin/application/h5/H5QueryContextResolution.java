package com.xinshi.admin.application.h5;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Query-context routing result returned to bootstrap and registration flows.
 *
 * @author Codex
 * @date 2026-08-31
 */
public class H5QueryContextResolution {
    private final String flowState;
    private final Map<String, Object> queryPreset;
    private final List<Map<String, Object>> candidates;

    public H5QueryContextResolution(
            String flowState,
            Map<String, Object> queryPreset,
            List<Map<String, Object>> candidates) {
        this.flowState = flowState;
        this.queryPreset = queryPreset == null ? Collections.emptyMap() : queryPreset;
        this.candidates = candidates == null ? Collections.emptyList() : candidates;
    }

    public String getFlowState() {
        return flowState;
    }

    public Map<String, Object> getQueryPreset() {
        return queryPreset;
    }

    public List<Map<String, Object>> getCandidates() {
        return candidates;
    }
}
