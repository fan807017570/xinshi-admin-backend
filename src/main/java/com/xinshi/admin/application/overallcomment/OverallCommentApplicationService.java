/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.overallcomment;

import com.xinshi.admin.application.h5.ParentContentLifecycleService;
import com.xinshi.admin.domain.overallcomment.OverallComment;
import com.xinshi.admin.domain.overallcomment.OverallCommentRepository;
import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OverallCommentApplicationService {
    private final OverallCommentRepository overallCommentRepository;
    private final AuthorizationService authorizationService;
    private final AuthSession authSession;
    private final ParentContentLifecycleService lifecycleService;

    public OverallCommentApplicationService(
            OverallCommentRepository overallCommentRepository,
            AuthorizationService authorizationService,
            AuthSession authSession,
            ParentContentLifecycleService lifecycleService) {
        this.overallCommentRepository = overallCommentRepository;
        this.authorizationService = authorizationService;
        this.authSession = authSession;
        this.lifecycleService = lifecycleService;
    }

    public List<OverallComment> listOverallComments(Long academicTermId, Long classId, Long studentId) {
        this.authorizationService.ensureReadableCommentScope(academicTermId, classId, studentId);
        return this.overallCommentRepository.findByParams(academicTermId, classId, studentId, this.authSession.userId(), this.authSession.roles());
    }

    public OverallComment saveOverallComment(long academicTermId, long classId, long studentId, String overallComment, String strengths, String improvementPoints, long evaluatorUserId, int status) {
        this.authorizationService.ensureHeadTeacherOrAdmin();
        this.authorizationService.ensureCanAccessClass(classId);
        this.authorizationService.ensureStudentBelongsToClass(studentId, classId);
        java.util.Map<String, Object> saved = this.lifecycleService.saveCommentDraft(
                academicTermId,
                classId,
                studentId,
                overallComment,
                strengths,
                improvementPoints);
        Object idValue = saved.get("id");
        if (!(idValue instanceof Number)) {
            throw new IllegalStateException("保存评语后未返回记录编号");
        }
        return this.overallCommentRepository.findById(((Number)idValue).longValue())
                .orElseThrow(() -> new IllegalStateException("保存评语后未找到记录"));
    }

    public OverallComment getOverallComment(long id) {
        OverallComment comment = this.overallCommentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("评语不存在"));
        this.authorizationService.ensureCanAccessComment(id);
        return comment;
    }
}
