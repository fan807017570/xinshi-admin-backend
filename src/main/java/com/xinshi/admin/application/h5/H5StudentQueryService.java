package com.xinshi.admin.application.h5;

import com.xinshi.admin.infrastructure.security.H5RequestContext;
import com.xinshi.admin.infrastructure.security.H5SecuritySupport;
import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only, publication-aware parent queries for the H5 application. */
@Service
public class H5StudentQueryService {
    private final JdbcTemplate jdbcTemplate;
    private final H5SecuritySupport securitySupport;

    public H5StudentQueryService(JdbcTemplate jdbcTemplate, H5SecuritySupport securitySupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.securitySupport = securitySupport;
    }

    public List<Map<String, Object>> students() {
        return jdbcTemplate.queryForList(
                "SELECT s.id AS studentId, CONCAT(LEFT(s.student_name,1),'*') AS studentNameMasked, "
                        + "c.class_name AS className FROM school_student_parent sp "
                        + "JOIN school_student s ON s.id=sp.student_id AND s.status=1 AND s.is_deleted=0 "
                        + "JOIN school_class c ON c.id=s.class_id AND c.status=1 AND c.is_deleted=0 "
                        + "WHERE sp.parent_user_id=? ORDER BY sp.is_primary DESC,s.student_no,s.id",
                parentUserId());
    }

    public List<Map<String, Object>> terms(long studentId) {
        ensureBound(studentId);
        List<Map<String, Object>> terms = jdbcTemplate.queryForList(
                "SELECT t.id AS academicTermId,t.term_name AS termName,"
                        + "MAX(CASE WHEN r.status=2 THEN 1 ELSE 0 END) AS hasScores,"
                        + "MAX(CASE WHEN oc.status=2 AND oc.published_at IS NOT NULL THEN 1 ELSE 0 END) AS hasTeacherComment,"
                        + "MAX(CASE WHEN a.status=2 AND a.published_at IS NOT NULL THEN 1 ELSE 0 END) AS hasHonors "
                        + "FROM school_academic_term t "
                        + "LEFT JOIN school_student_course_result r ON r.academic_term_id=t.id AND r.student_id=? "
                        + "LEFT JOIN school_student_overall_comment oc ON oc.academic_term_id=t.id AND oc.student_id=? "
                        + "LEFT JOIN school_student_achievement a ON a.academic_term_id=t.id AND a.student_id=? "
                        + "WHERE t.status=1 GROUP BY t.id,t.term_name "
                        + "HAVING hasScores=1 OR hasTeacherComment=1 OR hasHonors=1 "
                        + "ORDER BY t.start_date DESC,t.id DESC",
                studentId, studentId, studentId);
        for (Map<String, Object> term : terms) {
            term.put("examTypes", jdbcTemplate.queryForList(
                    "SELECT DISTINCT et.id AS examTypeId,et.exam_type_name AS examTypeName "
                            + "FROM school_student_course_result r JOIN school_exam_type et ON et.id=r.exam_type_id AND et.status=1 "
                            + "JOIN school_class_subject cs ON cs.id=r.class_subject_id AND cs.status=1 "
                            + "WHERE r.student_id=? AND r.academic_term_id=? AND r.status=2 ORDER BY et.sort_order,et.id",
                    studentId, term.get("academicTermId")));
        }
        return terms;
    }

    public Map<String, Object> scores(long studentId, long termId, long examTypeId) {
        Map<String, Object> student = ensureBound(studentId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT r.id AS resultId,su.id AS subjectId,su.subject_name AS subjectName,r.score,"
                        + "su.max_score AS maxScore,su.sort_order AS sortOrder,"
                        + "CASE WHEN r.performance_comment IS NOT NULL OR r.strengths IS NOT NULL "
                        + "OR r.improvement_points IS NOT NULL THEN 1 ELSE 0 END AS hasDetail,"
                        + "t.term_name AS termName,et.exam_type_name AS examTypeName "
                        + "FROM school_student_course_result r "
                        + "JOIN school_academic_term t ON t.id=r.academic_term_id AND t.status=1 "
                        + "JOIN school_exam_type et ON et.id=r.exam_type_id AND et.status=1 "
                        + "JOIN school_class_subject cs ON cs.id=r.class_subject_id AND cs.status=1 "
                        + "JOIN school_subject su ON su.id=cs.subject_id AND su.status=1 "
                        + "WHERE r.student_id=? AND r.academic_term_id=? AND r.exam_type_id=? AND r.status=2 "
                        + "ORDER BY su.sort_order,su.id",
                studentId, termId, examTypeId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("student", student);
        data.put("term", pair("academicTermId", termId, "termName", rows.isEmpty() ? "" : rows.get(0).get("termName")));
        data.put("examType", pair("examTypeId", examTypeId, "examTypeName", rows.isEmpty() ? "" : rows.get(0).get("examTypeName")));
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            total = total.add((BigDecimal) row.get("score"));
            row.remove("termName");
            row.remove("examTypeName");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalScore", total);
        summary.put("averageScore", rows.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP));
        summary.put("subjectCount", rows.size());
        summary.put("classRank", null);
        data.put("summary", summary);
        data.put("subjects", rows);
        return data;
    }

    public Map<String, Object> result(long resultId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT r.id AS resultId,r.student_id AS studentId,su.subject_name AS subjectName,r.score,"
                        + "su.max_score AS maxScore,r.performance_comment AS performanceComment,r.strengths,"
                        + "r.improvement_points AS improvementPoints,t.term_name AS termName,et.exam_type_name AS examTypeName "
                        + "FROM school_student_course_result r "
                        + "JOIN school_student_parent sp ON sp.student_id=r.student_id AND sp.parent_user_id=? "
                        + "JOIN school_student s ON s.id=r.student_id AND s.status=1 AND s.is_deleted=0 "
                        + "JOIN school_academic_term t ON t.id=r.academic_term_id AND t.status=1 "
                        + "JOIN school_exam_type et ON et.id=r.exam_type_id AND et.status=1 "
                        + "JOIN school_class_subject cs ON cs.id=r.class_subject_id AND cs.status=1 "
                        + "JOIN school_subject su ON su.id=cs.subject_id AND su.status=1 "
                        + "WHERE r.id=? AND r.status=2 LIMIT 1",
                parentUserId(), resultId);
        if (rows.isEmpty()) {
            throw new H5ApiException(HttpStatus.NOT_FOUND, "RESULT_NOT_FOUND", "未找到可查看的成绩");
        }
        return rows.get(0);
    }

    public Map<String, Object> teacherComment(long studentId, long termId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("student", ensureBound(studentId));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT oc.id AS commentId,oc.academic_term_id AS academicTermId,t.term_name AS termName,"
                        + "oc.teacher_name_snapshot AS teacherNameDisplay,oc.overall_comment AS overallComment,"
                        + "oc.strengths,oc.improvement_points AS improvementPoints,oc.published_at AS publishedAt "
                        + "FROM school_student_overall_comment oc JOIN school_academic_term t ON t.id=oc.academic_term_id AND t.status=1 "
                        + "WHERE oc.student_id=? AND oc.academic_term_id=? AND oc.status=2 AND oc.published_at IS NOT NULL LIMIT 1",
                studentId, termId);
        data.put("comment", rows.isEmpty() ? null : rows.get(0));
        return data;
    }

    public Map<String, Object> honors(long studentId, long termId, int page, int pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("student", ensureBound(studentId));
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, pageSize));
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM school_student_achievement a JOIN school_academic_term t ON t.id=a.academic_term_id AND t.status=1 "
                        + "WHERE a.student_id=? AND a.academic_term_id=? AND a.status=2 AND a.published_at IS NOT NULL",
                Integer.class, studentId, termId);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT a.id AS honorId,a.academic_term_id AS academicTermId,t.term_name AS termName,"
                        + "a.achievement_text AS achievementText,a.awarded_on AS awardedOn,a.published_at AS publishedAt "
                        + "FROM school_student_achievement a JOIN school_academic_term t ON t.id=a.academic_term_id AND t.status=1 "
                        + "WHERE a.student_id=? AND a.academic_term_id=? AND a.status=2 AND a.published_at IS NOT NULL "
                        + "ORDER BY a.awarded_on DESC,a.id DESC LIMIT ? OFFSET ?",
                studentId, termId, safeSize, (safePage - 1) * safeSize);
        data.put("items", items);
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        data.put("total", total == null ? 0 : total);
        data.put("hasNext", safePage * safeSize < (total == null ? 0 : total));
        return data;
    }

    private Map<String, Object> ensureBound(long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id AS studentId,s.student_name AS studentName,c.class_name AS className "
                        + "FROM school_student_parent sp JOIN school_student s ON s.id=sp.student_id AND s.status=1 AND s.is_deleted=0 "
                        + "JOIN school_class c ON c.id=s.class_id AND c.status=1 AND c.is_deleted=0 "
                        + "WHERE sp.parent_user_id=? AND s.id=? LIMIT 1", parentUserId(), studentId);
        if (rows.isEmpty()) {
            throw new H5ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "未找到已绑定学生");
        }
        Map<String, Object> row = rows.get(0);
        row.put("studentNameMasked", securitySupport.maskName(String.valueOf(row.remove("studentName"))));
        return row;
    }

    private long parentUserId() {
        Long id = H5RequestContext.require().getParentUserId();
        if (id == null) {
            throw new H5ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_REQUIRED", "请先完成家长注册和学生绑定");
        }
        return id;
    }

    private Map<String, Object> pair(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }
}
