/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.subject;

import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
import com.xinshi.admin.domain.subject.Subject;
import com.xinshi.admin.domain.subject.SubjectRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SubjectApplicationService {
    private final SubjectRepository subjectRepository;
    private final AuthorizationService authorizationService;
    private final AuthSession authSession;

    public SubjectApplicationService(SubjectRepository subjectRepository, AuthorizationService authorizationService, AuthSession authSession) {
        this.subjectRepository = subjectRepository;
        this.authorizationService = authorizationService;
        this.authSession = authSession;
    }

    public List<Subject> listSubjects(String mode) {
        boolean teacherOnly;
        boolean bl = teacherOnly = this.authSession.hasRole("TEACHER") && !this.authSession.hasRole("SUPER_ADMIN") && !this.authSession.hasRole("HEAD_TEACHER");
        if (this.authSession.hasRole("HEAD_TEACHER") && this.authSession.hasRole("TEACHER") && !this.authSession.hasRole("SUPER_ADMIN") && "teacher".equals(mode)) {
            teacherOnly = true;
        }
        if (teacherOnly) {
            return this.subjectRepository.findAllAccessibleByTeacher(this.authSession.userId(), true);
        }
        return this.subjectRepository.findAll();
    }

    public Subject getSubject(long id) {
        return this.subjectRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("课程不存在"));
    }

    public Subject createSubject(String subjectCode, String subjectName, double minScore, double maxScore, int status) {
        this.authorizationService.ensureSuperAdmin();
        if (this.subjectRepository.findByCode(subjectCode).isPresent()) {
            throw new IllegalArgumentException("课程编码已存在");
        }
        Subject subject = Subject.create(subjectCode, subjectName, minScore, maxScore, status);
        return this.subjectRepository.save(subject);
    }

    public Subject updateSubject(long id, String subjectCode, String subjectName, Double minScore, Double maxScore, Integer status) {
        this.authorizationService.ensureSuperAdmin();
        Subject subject = this.getSubject(id);
        subject.update(subjectCode, subjectName, minScore, maxScore, status);
        this.subjectRepository.update(subject);
        return this.getSubject(id);
    }

    public void deleteSubject(long id) {
        this.authorizationService.ensureSuperAdmin();
        this.subjectRepository.deactivate(id);
    }
}

