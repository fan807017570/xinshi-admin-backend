package com.xinshi.admin.interfaces.web;

import com.xinshi.admin.application.h5.ParentContentPublishService;
import java.util.Map;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publication endpoints protected by the existing admin-token interceptor. */
@RestController
@RequestMapping("/api")
public class ParentContentPublishController {
    private final ParentContentPublishService publishService;

    public ParentContentPublishController(ParentContentPublishService publishService) { this.publishService = publishService; }

    @PatchMapping("/student-overall-comments/{id}/publish")
    public Map<String, Object> publishComment(@PathVariable long id) { return publishService.publishComment(id); }
    @PatchMapping("/student-overall-comments/{id}/unpublish")
    public Map<String, Object> unpublishComment(@PathVariable long id) { return publishService.unpublishComment(id); }
    @PatchMapping("/achievements/{id}/publish")
    public Map<String, Object> publishAchievement(@PathVariable long id) { return publishService.publishAchievement(id); }
    @PatchMapping("/achievements/{id}/unpublish")
    public Map<String, Object> unpublishAchievement(@PathVariable long id) { return publishService.unpublishAchievement(id); }
}
