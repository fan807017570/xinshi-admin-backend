/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.courseresult;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import com.xinshi.admin.interfaces.web.security.ForbiddenException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CourseResultService
extends SchoolBaseService {
    private final AccessControlService accessControlService;

    public CourseResultService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
    }

    public List<Map<String, Object>> listStudentResults(Long academicTermId, Long classId, Long studentId, Long classSubjectId, Long examTypeId) {
        this.accessControlService.ensureReadableResultScope(academicTermId, classId, studentId, classSubjectId);
        StringBuilder sql = new StringBuilder("SELECT r.id, r.academic_term_id AS academicTermId, t.term_name AS termName, r.class_subject_id AS classSubjectId, cs.class_id AS classId, c.class_name AS className, r.student_id AS studentId, s.student_name AS studentName, cs.subject_id AS subjectId, su.subject_name AS subjectName, r.score, r.performance_comment AS performanceComment, su.min_score AS minScore, su.max_score AS maxScore, r.strengths, r.improvement_points AS improvementPoints, cs.teacher_user_id AS teacherUserId, tu.real_name AS teacherName, r.evaluator_user_id AS evaluatorUserId, u.real_name AS evaluatorName, r.evaluated_at AS evaluatedAt, r.status, r.created_at AS createdAt, r.exam_type_id AS examTypeId, et.exam_type_name AS examTypeName FROM school_student_course_result r LEFT JOIN school_academic_term t ON t.id = r.academic_term_id LEFT JOIN school_class_subject cs ON cs.id = r.class_subject_id LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.id = r.student_id LEFT JOIN school_subject su ON su.id = cs.subject_id LEFT JOIN sys_user tu ON tu.id = cs.teacher_user_id LEFT JOIN sys_user u ON u.id = r.evaluator_user_id LEFT JOIN school_exam_type et ON et.id = r.exam_type_id WHERE 1 = 1");
        ArrayList<Long> args = new ArrayList<Long>();
        Long currentUserId = this.accessControlService.currentUserId();
        if (academicTermId != null) {
            sql.append(" AND r.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            sql.append(" AND cs.class_id = ?");
            args.add(classId);
        }
        if (studentId != null) {
            sql.append(" AND r.student_id = ?");
            args.add(studentId);
        }
        if (classSubjectId != null) {
            sql.append(" AND r.class_subject_id = ?");
            args.add(classSubjectId);
        }
        if (examTypeId != null) {
            sql.append(" AND r.exam_type_id = ?");
            args.add(examTypeId);
        }
        if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = cs.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(currentUserId);
        } else if (this.accessControlService.hasRole("TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND cs.teacher_user_id = ?");
            args.add(currentUserId);
        } else if (this.accessControlService.hasRole("PARENT") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = r.student_id AND sp.parent_user_id = ?)");
            args.add(currentUserId);
        }
        sql.append(" ORDER BY r.id DESC");
        return this.jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> listTeacherScoreEntries(Long academicTermId, Long classId, Long subjectId, Long examTypeId, String keyword, String mode) {
        return this.listTeacherScoreEntries(academicTermId, classId, subjectId, examTypeId, keyword, mode, new PageRequest(1, 200)).getItems();
    }

    public PageResult<Map<String, Object>> listTeacherScoreEntries(Long academicTermId, Long classId, Long subjectId, Long examTypeId, String keyword, String mode, PageRequest pageRequest) {
        this.accessControlService.ensureTeacherCanWriteResults();
        String fromClause = "FROM school_class_subject cs LEFT JOIN school_academic_term t ON t.id = cs.academic_term_id LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.class_id = cs.class_id AND s.is_deleted = 0 AND s.status = 1 LEFT JOIN school_subject su ON su.id = cs.subject_id LEFT JOIN school_student_course_result r ON r.academic_term_id = cs.academic_term_id AND r.class_subject_id = cs.id AND r.student_id = s.id LEFT JOIN sys_user tu ON tu.id = cs.teacher_user_id LEFT JOIN sys_user u ON u.id = r.evaluator_user_id LEFT JOIN school_exam_type et ON et.id = r.exam_type_id";
        StringBuilder where = new StringBuilder(" WHERE cs.status = 1 AND c.is_deleted = 0 AND s.id IS NOT NULL");
        ArrayList<Object> args = new ArrayList<Object>();
        Long currentUserId = this.accessControlService.currentUserId();
        if (academicTermId != null) {
            where.append(" AND cs.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            where.append(" AND cs.class_id = ?");
            args.add(classId);
        }
        if (subjectId != null) {
            where.append(" AND cs.subject_id = ?");
            args.add(subjectId);
        }
        if (examTypeId != null) {
            where.append(" AND r.exam_type_id = ?");
            args.add(examTypeId);
        }
        if (StringUtils.hasText((String)keyword)) {
            where.append(" AND (s.student_name LIKE ? OR s.student_no LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        if (!this.accessControlService.hasRole("SUPER_ADMIN")) {
            if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("TEACHER")) {
                where.append(" AND c.head_teacher_user_id = ?");
                args.add(currentUserId);
            } else if (this.accessControlService.hasRole("TEACHER") && !this.accessControlService.hasRole("HEAD_TEACHER")) {
                where.append(" AND cs.teacher_user_id = ?");
                args.add(currentUserId);
            } else if (this.accessControlService.hasRole("HEAD_TEACHER") && this.accessControlService.hasRole("TEACHER")) {
                if ("teacher".equals(mode)) {
                    where.append(" AND cs.teacher_user_id = ?");
                } else {
                    where.append(" AND c.head_teacher_user_id = ?");
                }
                args.add(currentUserId);
            } else {
                throw new ForbiddenException("无权查看任课成绩");
            }
        }
        String countSql = "SELECT COUNT(1) " + fromClause + where;
        long total = (Long)this.jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        String orderBy = " ORDER BY c.grade_level ASC, c.id DESC, su.id ASC, s.student_no ASC, s.id ASC";
        String dataSql = "SELECT r.id, cs.academic_term_id AS academicTermId, t.term_name AS termName, cs.id AS classSubjectId, cs.class_id AS classId, c.class_name AS className, s.id AS studentId, s.student_no AS studentNo, s.student_name AS studentName, cs.subject_id AS subjectId, su.subject_name AS subjectName, su.min_score AS minScore, su.max_score AS maxScore, r.score, r.performance_comment AS performanceComment, r.strengths, r.improvement_points AS improvementPoints, cs.teacher_user_id AS teacherUserId, tu.real_name AS teacherName, r.evaluator_user_id AS evaluatorUserId, u.real_name AS evaluatorName, r.evaluated_at AS evaluatedAt, COALESCE(r.status, 0) AS status, r.created_at AS createdAt, r.exam_type_id AS examTypeId, et.exam_type_name AS examTypeName " + fromClause + where + orderBy + " LIMIT ? OFFSET ?";
        args.add(pageRequest.limit());
        args.add(pageRequest.offset());
        List items = this.jdbcTemplate.queryForList(dataSql, args.toArray());
        return new PageResult<Map<String, Object>>(items, total, pageRequest.page(), pageRequest.size());
    }

    public Map<String, Object> saveStudentResult(Map<String, Object> request) {
        List examTypeRows;
        this.accessControlService.ensureTeacherCanWriteResults();
        long academicTermId = this.requiredLong(request, "academicTermId");
        Long requestedClassSubjectId = this.optionalLong(request, "classSubjectId");
        long studentId = this.requiredLong(request, "studentId");
        double score = this.requiredDouble(request, "score");
        Long examTypeId = this.optionalLong(request, "examTypeId");
        String performanceComment = this.optionalString(request, "performanceComment", null);
        String strengths = this.optionalString(request, "strengths", null);
        String improvementPoints = this.optionalString(request, "improvementPoints", null);
        Long evalUserId = this.optionalLong(request, "evaluatorUserId");
        long evaluatorUserId = evalUserId != null ? evalUserId : this.accessControlService.currentUserId();
        int status = this.optionalInteger(request, "status", 1);
        long classSubjectId = requestedClassSubjectId == null ? this.ensureClassSubjectForResult(request, academicTermId) : requestedClassSubjectId.longValue();
        this.accessControlService.ensureCanAccessClassSubject(classSubjectId);
        if (examTypeId != null && (examTypeRows = this.jdbcTemplate.queryForList("SELECT id FROM school_exam_type WHERE id = ? AND status = 1", new Object[]{examTypeId})).isEmpty()) {
            throw new IllegalArgumentException("考试类型不存在或已停用");
        }
        List classSubjectRows = this.jdbcTemplate.queryForList("SELECT cs.class_id AS classId, s.min_score AS minScore, s.max_score AS maxScore FROM school_class_subject cs LEFT JOIN school_subject s ON s.id = cs.subject_id WHERE cs.id = ? AND cs.academic_term_id = ?", new Object[]{classSubjectId, academicTermId});
        if (classSubjectRows.isEmpty()) {
            throw new IllegalArgumentException("班级课程不存在");
        }
        Map classSubject = (Map)classSubjectRows.get(0);
        this.accessControlService.ensureStudentBelongsToClass(studentId, this.requiredLong(classSubject, "classId"));
        this.accessControlService.ensureScoreInSubjectRange(score, classSubject);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List exactMatch = this.jdbcTemplate.queryForList("SELECT id FROM school_student_course_result WHERE academic_term_id = ? AND class_subject_id = ? AND student_id = ? AND (exam_type_id = ? OR (exam_type_id IS NULL AND ? IS NULL))", new Object[]{academicTermId, classSubjectId, studentId, examTypeId, examTypeId});
        if (exactMatch.isEmpty()) {
            List sameCombo = this.jdbcTemplate.queryForList("SELECT id FROM school_student_course_result WHERE academic_term_id = ? AND class_subject_id = ? AND student_id = ?", new Object[]{academicTermId, classSubjectId, studentId});
            if (sameCombo.isEmpty()) {
                this.insert("school_student_course_result", "INSERT INTO school_student_course_result (academic_term_id, class_subject_id, student_id, exam_type_id, score, performance_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", academicTermId, classSubjectId, studentId, examTypeId, score, performanceComment, strengths, improvementPoints, evaluatorUserId, now, status);
            } else {
                for (int i = 1; i < sameCombo.size(); ++i) {
                    this.jdbcTemplate.update("DELETE FROM school_student_course_result WHERE id = ?", new Object[]{this.requiredLong((Map)sameCombo.get(i), "id")});
                }
                Long existingId = this.requiredLong((Map)sameCombo.get(0), "id");
                this.jdbcTemplate.update("UPDATE school_student_course_result SET score = ?, performance_comment = ?, strengths = ?, improvement_points = ?, exam_type_id = ?, evaluator_user_id = ?, evaluated_at = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{score, performanceComment, strengths, improvementPoints, examTypeId, evaluatorUserId, now, status, existingId});
            }
        } else {
            Long existingId = this.requiredLong((Map)exactMatch.get(0), "id");
            this.jdbcTemplate.update("UPDATE school_student_course_result SET score = ?, performance_comment = ?, strengths = ?, improvement_points = ?, exam_type_id = ?, evaluator_user_id = ?, evaluated_at = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{score, performanceComment, strengths, improvementPoints, examTypeId, evaluatorUserId, now, status, existingId});
        }
        if (!exactMatch.isEmpty()) {
            return this.first(this.jdbcTemplate.queryForList("SELECT r.id, r.academic_term_id AS academicTermId, r.class_subject_id AS classSubjectId, r.student_id AS studentId, r.score, r.exam_type_id AS examTypeId, r.performance_comment AS performanceComment, r.strengths, r.improvement_points AS improvementPoints, r.evaluator_user_id AS evaluatorUserId, r.evaluated_at AS evaluatedAt, r.status, r.created_at AS createdAt FROM school_student_course_result r WHERE r.id = ?", new Object[]{this.requiredLong((Map)exactMatch.get(0), "id")}));
        }
        return this.first(this.jdbcTemplate.queryForList("SELECT r.id, r.academic_term_id AS academicTermId, r.class_subject_id AS classSubjectId, r.student_id AS studentId, r.score, r.exam_type_id AS examTypeId, r.performance_comment AS performanceComment, r.strengths, r.improvement_points AS improvementPoints, r.evaluator_user_id AS evaluatorUserId, r.evaluated_at AS evaluatedAt, r.status, r.created_at AS createdAt FROM school_student_course_result r WHERE r.academic_term_id = ? AND r.class_subject_id = ? AND r.student_id = ? AND (r.exam_type_id = ? OR (r.exam_type_id IS NULL AND ? IS NULL))", new Object[]{academicTermId, classSubjectId, studentId, examTypeId, examTypeId}));
    }

    private long ensureClassSubjectForResult(Map<String, Object> request, long academicTermId) {
        long classId = this.requiredLong(request, "classId");
        long subjectId = this.requiredLong(request, "subjectId");
        this.accessControlService.ensureCanAccessClass(classId);
        List existing = this.jdbcTemplate.queryForList("SELECT id FROM school_class_subject WHERE academic_term_id = ? AND class_id = ? AND subject_id = ?", new Object[]{academicTermId, classId, subjectId});
        if (!existing.isEmpty()) {
            return this.requiredLong((Map)existing.get(0), "id");
        }
        List subjects = this.jdbcTemplate.queryForList("SELECT id FROM school_subject WHERE id = ? AND status = 1", new Object[]{subjectId});
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("科目不存在或已停用");
        }
        return this.insert("school_class_subject", "INSERT INTO school_class_subject (academic_term_id, class_id, subject_id, status) VALUES (?, ?, ?, 1)", academicTermId, classId, subjectId);
    }

    public Map<String, Object> publishStudentResult(long id) {
        this.accessControlService.ensureTeacherCanWriteResults();
        this.accessControlService.ensureCanAccessResult(id);
        this.jdbcTemplate.update("UPDATE school_student_course_result SET status = 2, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
        return this.getResult(id);
    }

    public Map<String, Object> getResult(long id) {
        Map<String, Object> result = this.first(this.jdbcTemplate.queryForList("SELECT r.id, r.academic_term_id AS academicTermId, r.class_subject_id AS classSubjectId, r.student_id AS studentId, r.score, r.performance_comment AS performanceComment, r.strengths, r.improvement_points AS improvementPoints, r.evaluator_user_id AS evaluatorUserId, r.evaluated_at AS evaluatedAt, r.status, r.created_at AS createdAt FROM school_student_course_result r WHERE r.id = ?", new Object[]{id}));
        this.accessControlService.ensureReadableResultRow(result);
        return result;
    }

    public void deleteStudentResult(long id) {
        this.jdbcTemplate.update("DELETE FROM school_student_course_result WHERE id = ?", new Object[]{id});
    }
}

