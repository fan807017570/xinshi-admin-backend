package com.xinshi.admin.application.overallcomment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xinshi.admin.application.h5.ParentContentLifecycleService;
import com.xinshi.admin.domain.overallcomment.OverallComment;
import com.xinshi.admin.domain.overallcomment.OverallCommentRepository;
import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OverallCommentApplicationServiceTest {

    @Test
    void savePublishedCommentUsesLifecycleAndReturnsDraftResponse() {
        OverallCommentRepository repository = mock(OverallCommentRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        AuthSession authSession = mock(AuthSession.class);
        ParentContentLifecycleService lifecycleService = mock(ParentContentLifecycleService.class);
        OverallCommentApplicationService service = new OverallCommentApplicationService(
                repository, authorizationService, authSession, lifecycleService);
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("id", 71L);
        when(lifecycleService.saveCommentDraft(
                31L, 41L, 51L, "更新后评语", "优点", "改进点"))
                .thenReturn(saved);
        OverallComment draft = OverallComment.rehydrate(
                71L,
                31L,
                "2026学年",
                41L,
                "一班",
                51L,
                "学生一",
                "更新后评语",
                "优点",
                "改进点",
                900L,
                "认证教师",
                LocalDateTime.now(),
                1,
                LocalDateTime.now());
        when(repository.findById(71L)).thenReturn(Optional.of(draft));

        OverallComment result = service.saveOverallComment(
                31L, 41L, 51L, "更新后评语", "优点", "改进点", 12345L, 2);

        assertEquals(1, result.getStatusCode());
        assertEquals(900L, result.getEvaluatorUserId());
        verify(lifecycleService).saveCommentDraft(
                31L, 41L, 51L, "更新后评语", "优点", "改进点");
        verify(repository, never()).save(any(OverallComment.class));
        verify(repository, never()).update(any(OverallComment.class));
        verify(repository, never()).findByTermAndStudent(anyLong(), anyLong());
    }
}
