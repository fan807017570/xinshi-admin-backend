package com.xinshi.admin.application.h5;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Admin API facade for the parent-content publication lifecycle.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Service
public class ParentContentPublishService {
    private final ParentContentLifecycleService lifecycleService;

    public ParentContentPublishService(ParentContentLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    public Map<String, Object> publishComment(long id) {
        return lifecycleService.publishComment(id);
    }

    public Map<String, Object> unpublishComment(long id) {
        return lifecycleService.unpublishComment(id);
    }

    public Map<String, Object> publishAchievement(long id) {
        return lifecycleService.publishAchievement(id);
    }

    public Map<String, Object> unpublishAchievement(long id) {
        return lifecycleService.unpublishAchievement(id);
    }
}
