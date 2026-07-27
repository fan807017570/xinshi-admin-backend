/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.gradesubject;

import com.xinshi.admin.domain.gradesubject.GradeSubject;
import com.xinshi.admin.domain.gradesubject.GradeSubjectRepository;
import com.xinshi.admin.domain.shared.AuthorizationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GradeSubjectApplicationService {
    private final GradeSubjectRepository gradeSubjectRepository;
    private final AuthorizationService authorizationService;

    public GradeSubjectApplicationService(GradeSubjectRepository gradeSubjectRepository, AuthorizationService authorizationService) {
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.authorizationService = authorizationService;
    }

    public List<GradeSubject> listGradeSubjects(long academicTermId, int gradeLevel) {
        this.authorizationService.ensureReadableAcademicConfig();
        return this.gradeSubjectRepository.findByTermAndGrade(academicTermId, gradeLevel);
    }

    public void saveGradeSubjects(long academicTermId, int gradeLevel, List<Map<String, Object>> subjectMaps) {
        this.authorizationService.ensureSuperAdmin();
        ArrayList<GradeSubject> subjects = new ArrayList<GradeSubject>();
        for (Map<String, Object> subjectMap : subjectMaps) {
            long subjectId = this.toLong(subjectMap.get("subjectId"));
            int isRequired = this.toInt(subjectMap.get("isRequired"), 1);
            int sortOrder = this.toInt(subjectMap.get("sortOrder"), 0);
            int status = this.toInt(subjectMap.get("status"), 1);
            subjects.add(GradeSubject.create(academicTermId, gradeLevel, subjectId, isRequired, sortOrder, status));
        }
        this.gradeSubjectRepository.saveBatch(academicTermId, gradeLevel, subjects);
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number)value).intValue();
        }
        return defaultValue;
    }
}

