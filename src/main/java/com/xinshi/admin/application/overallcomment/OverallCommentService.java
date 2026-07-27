/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.overallcomment;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OverallCommentService
extends SchoolBaseService {
    private final AccessControlService accessControlService;

    public OverallCommentService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
    }

    public List<Map<String, Object>> listOverallComments(Long academicTermId, Long classId, Long studentId) {
        this.accessControlService.ensureReadableCommentScope(academicTermId, classId, studentId);
        StringBuilder sql = new StringBuilder("SELECT o.id, o.academic_term_id AS academicTermId, t.term_name AS termName, o.class_id AS classId, c.class_name AS className, o.student_id AS studentId, s.student_name AS studentName, o.overall_comment AS overallComment, o.strengths, o.improvement_points AS improvementPoints, o.evaluator_user_id AS evaluatorUserId, u.real_name AS evaluatorName, o.evaluated_at AS evaluatedAt, o.status, o.created_at AS createdAt FROM school_student_overall_comment o LEFT JOIN school_academic_term t ON t.id = o.academic_term_id LEFT JOIN school_class c ON c.id = o.class_id LEFT JOIN school_student s ON s.id = o.student_id LEFT JOIN sys_user u ON u.id = o.evaluator_user_id WHERE 1 = 1");
        ArrayList<Long> args = new ArrayList<Long>();
        Long currentUserId = this.accessControlService.currentUserId();
        if (academicTermId != null) {
            sql.append(" AND o.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            sql.append(" AND o.class_id = ?");
            args.add(classId);
        }
        if (studentId != null) {
            sql.append(" AND o.student_id = ?");
            args.add(studentId);
        }
        if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = o.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(currentUserId);
        } else if (this.accessControlService.hasRole("PARENT") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = o.student_id AND sp.parent_user_id = ?)");
            args.add(currentUserId);
        }
        sql.append(" ORDER BY o.id DESC");
        return this.jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> saveOverallComment(Map<String, Object> request) {
        long academicTermId = this.requiredLong(request, "academicTermId");
        long classId = this.requiredLong(request, "classId");
        long studentId = this.requiredLong(request, "studentId");
        String overallComment = this.requiredString(request, "overallComment");
        String strengths = this.optionalString(request, "strengths", null);
        String improvementPoints = this.optionalString(request, "improvementPoints", null);
        long evaluatorUserId = this.requiredLong(request, "evaluatorUserId");
        int status = this.optionalInteger(request, "status", 1);
        this.accessControlService.ensureHeadTeacherOrAdmin();
        this.accessControlService.ensureCanAccessClass(classId);
        this.accessControlService.ensureStudentBelongsToClass(studentId, classId);
        List exist = this.jdbcTemplate.queryForList("SELECT id FROM school_student_overall_comment WHERE academic_term_id = ? AND student_id = ?", new Object[]{academicTermId, studentId});
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        if (exist.isEmpty()) {
            this.insert("school_student_overall_comment", "INSERT INTO school_student_overall_comment (academic_term_id, class_id, student_id, overall_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", academicTermId, classId, studentId, overallComment, strengths, improvementPoints, evaluatorUserId, now, status);
        } else {
            this.jdbcTemplate.update("UPDATE school_student_overall_comment SET class_id = ?, overall_comment = ?, strengths = ?, improvement_points = ?, evaluator_user_id = ?, evaluated_at = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE academic_term_id = ? AND student_id = ?", new Object[]{classId, overallComment, strengths, improvementPoints, evaluatorUserId, now, status, academicTermId, studentId});
        }
        return this.first(this.jdbcTemplate.queryForList("SELECT o.id, o.academic_term_id AS academicTermId, o.class_id AS classId, o.student_id AS studentId, o.overall_comment AS overallComment, o.strengths, o.improvement_points AS improvementPoints, o.evaluator_user_id AS evaluatorUserId, o.evaluated_at AS evaluatedAt, o.status, o.created_at AS createdAt FROM school_student_overall_comment o WHERE o.academic_term_id = ? AND o.student_id = ?", new Object[]{academicTermId, studentId}));
    }

    public Map<String, Object> getOverallComment(long id) {
        Map<String, Object> comment = this.first(this.jdbcTemplate.queryForList("SELECT o.id, o.academic_term_id AS academicTermId, o.class_id AS classId, o.student_id AS studentId, o.overall_comment AS overallComment, o.strengths, o.improvement_points AS improvementPoints, o.evaluator_user_id AS evaluatorUserId, o.evaluated_at AS evaluatedAt, o.status, o.created_at AS createdAt FROM school_student_overall_comment o WHERE o.id = ?", new Object[]{id}));
        this.accessControlService.ensureReadableCommentRow(comment);
        return comment;
    }
}

