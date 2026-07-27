/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.subject;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SubjectManagementService
extends SchoolBaseService {
    private final AccessControlService accessControlService;

    public SubjectManagementService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
    }

    public List<Map<String, Object>> listSubjects(String mode) {
        StringBuilder sql = new StringBuilder("SELECT id, subject_code AS subjectCode, subject_name AS subjectName, min_score AS minScore, max_score AS maxScore, status, created_at AS createdAt FROM school_subject WHERE 1 = 1");
        ArrayList<Long> args = new ArrayList<Long>();
        if (this.accessControlService.hasRole("TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN") && !this.accessControlService.hasRole("HEAD_TEACHER")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class_subject cs WHERE cs.subject_id = school_subject.id AND cs.teacher_user_id = ? AND cs.status = 1)");
            args.add(this.accessControlService.currentUserId());
        } else if (this.accessControlService.hasRole("HEAD_TEACHER") && this.accessControlService.hasRole("TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN") && "teacher".equals(mode)) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class_subject cs WHERE cs.subject_id = school_subject.id AND cs.teacher_user_id = ? AND cs.status = 1)");
            args.add(this.accessControlService.currentUserId());
        }
        sql.append(" ORDER BY id DESC");
        return this.jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> getSubject(long id) {
        return this.first(this.jdbcTemplate.queryForList("SELECT id, subject_code AS subjectCode, subject_name AS subjectName, min_score AS minScore, max_score AS maxScore, status, created_at AS createdAt FROM school_subject WHERE id = ?", new Object[]{id}));
    }

    public Map<String, Object> createSubject(Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        String subjectCode = this.requiredString(request, "subjectCode");
        String subjectName = this.requiredString(request, "subjectName");
        double minScore = this.optionalDouble(request, "minScore", 0.0);
        double maxScore = this.optionalDouble(request, "maxScore", 100.0);
        Integer status = this.optionalInteger(request, "status", 1);
        this.accessControlService.ensureValidSubjectScoreRange(minScore, maxScore);
        if (this.exists("SELECT COUNT(1) FROM school_subject WHERE subject_code = ?", subjectCode) > 0) {
            throw new IllegalArgumentException("课程编码已存在");
        }
        long id = this.insert("school_subject", "INSERT INTO school_subject (subject_code, subject_name, min_score, max_score, status) VALUES (?, ?, ?, ?, ?)", subjectCode, subjectName, minScore, maxScore, status);
        return this.getSubject(id);
    }

    public Map<String, Object> updateSubject(long id, Map<String, Object> request) {
        Double nextMaxScore;
        this.accessControlService.ensureSuperAdmin();
        ArrayList<Object> args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE school_subject SET ");
        boolean first = true;
        if (request.containsKey("subjectCode")) {
            sql.append("subject_code = ?");
            args.add(this.requiredString(request, "subjectCode"));
            first = false;
        }
        if (request.containsKey("subjectName")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("subject_name = ?");
            args.add(this.requiredString(request, "subjectName"));
            first = false;
        }
        if (request.containsKey("minScore")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("min_score = ?");
            args.add(this.optionalDouble(request, "minScore", 0.0));
            first = false;
        }
        if (request.containsKey("maxScore")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("max_score = ?");
            args.add(this.optionalDouble(request, "maxScore", 100.0));
            first = false;
        }
        if (request.containsKey("status")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("status = ?");
            args.add(this.optionalInteger(request, "status", 1));
        }
        if (args.isEmpty()) {
            return this.getSubject(id);
        }
        Double nextMinScore = request.containsKey("minScore") ? this.optionalDouble(request, "minScore", 0.0) : null;
        Double d = nextMaxScore = request.containsKey("maxScore") ? this.optionalDouble(request, "maxScore", 100.0) : null;
        if (nextMinScore != null || nextMaxScore != null) {
            Map<String, Object> current = this.getSubject(id);
            this.accessControlService.ensureValidSubjectScoreRange(nextMinScore == null ? this.optionalDouble(current, "minScore", 0.0) : nextMinScore, nextMaxScore == null ? this.optionalDouble(current, "maxScore", 100.0) : nextMaxScore);
        }
        sql.append(", updated_at = CURRENT_TIMESTAMP WHERE id = ?");
        args.add(id);
        this.jdbcTemplate.update(sql.toString(), args.toArray());
        return this.getSubject(id);
    }

    public void deleteSubject(long id) {
        this.accessControlService.ensureSuperAdmin();
        this.jdbcTemplate.update("UPDATE school_subject SET status = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
    }

    public List<Map<String, Object>> listGradeSubjects(long academicTermId, int gradeLevel) {
        this.accessControlService.ensureReadableAcademicConfig();
        return this.jdbcTemplate.queryForList("SELECT gs.id, gs.academic_term_id AS academicTermId, gs.grade_level AS gradeLevel, gs.subject_id AS subjectId, s.subject_name AS subjectName, gs.is_required AS isRequired, gs.sort_order AS sortOrder, gs.status, gs.created_at AS createdAt FROM school_grade_subject gs LEFT JOIN school_subject s ON s.id = gs.subject_id WHERE gs.academic_term_id = ? AND gs.grade_level = ? ORDER BY gs.sort_order, gs.id", new Object[]{academicTermId, gradeLevel});
    }

    public void saveGradeSubjects(Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        long academicTermId = this.requiredLong(request, "academicTermId");
        int gradeLevel = this.requiredInteger(request, "gradeLevel");
        List<Map<String, Object>> subjects = this.mapList(request.get("subjects"));
        this.jdbcTemplate.update("DELETE FROM school_grade_subject WHERE academic_term_id = ? AND grade_level = ?", new Object[]{academicTermId, gradeLevel});
        for (Map<String, Object> subject : subjects) {
            long subjectId = this.requiredLong(subject, "subjectId");
            int isRequired = this.optionalInteger(subject, "isRequired", 1);
            int sortOrder = this.optionalInteger(subject, "sortOrder", 0);
            int status = this.optionalInteger(subject, "status", 1);
            this.insert("school_grade_subject", "INSERT INTO school_grade_subject (academic_term_id, grade_level, subject_id, is_required, sort_order, status) VALUES (?, ?, ?, ?, ?, ?)", academicTermId, gradeLevel, subjectId, isRequired, sortOrder, status);
        }
    }
}

