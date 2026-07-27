/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.class_;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClassManagementService
extends SchoolBaseService {
    private final AccessControlService accessControlService;

    public ClassManagementService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
    }

    public PageResult<Map<String, Object>> listClasses(String gradeSession, Integer gradeLevel, String mode, PageRequest pageRequest) {
        String session = StringUtils.hasText((String)gradeSession) ? gradeSession.trim() : null;
        StringBuilder where = new StringBuilder(" WHERE c.is_deleted = 0");
        ArrayList<Object> args = new ArrayList<Object>();
        if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN") && !this.accessControlService.hasRole("TEACHER")) {
            where.append(" AND c.head_teacher_user_id = ?");
            args.add(this.accessControlService.currentUserId());
        } else if (this.accessControlService.hasRole("TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN") && !this.accessControlService.hasRole("HEAD_TEACHER")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_class_subject cs WHERE cs.class_id = c.id AND cs.teacher_user_id = ? AND cs.status = 1)");
            args.add(this.accessControlService.currentUserId());
        } else if (this.accessControlService.hasRole("HEAD_TEACHER") && this.accessControlService.hasRole("TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            if ("teacher".equals(mode)) {
                where.append(" AND EXISTS (SELECT 1 FROM school_class_subject cs WHERE cs.class_id = c.id AND cs.teacher_user_id = ? AND cs.status = 1)");
            } else {
                where.append(" AND c.head_teacher_user_id = ?");
            }
            args.add(this.accessControlService.currentUserId());
        }
        if (StringUtils.hasText((String)session)) {
            where.append(" AND c.grade_session = ?");
            args.add(session);
        }
        if (gradeLevel != null) {
            where.append(" AND c.grade_level = ?");
            args.add(gradeLevel);
        }
        String countSql = "SELECT COUNT(1) FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id" + where;
        long total = (Long)this.jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        StringBuilder dataSql = new StringBuilder("SELECT c.id, c.class_code AS classCode, c.class_name AS className, c.grade_session AS gradeSession, c.grade_level AS gradeLevel, eg.grade_name AS gradeName, c.head_teacher_user_id AS headTeacherUserId, u.real_name AS headTeacherName, c.is_key_class AS isKeyClass, c.course_combination AS courseCombination, c.foreign_language AS foreignLanguage, c.ph_or_hi AS phOrHi, c.elective_two AS electiveTwo, c.status, c.created_at AS createdAt FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id LEFT JOIN school_enroll_grade eg ON eg.grade_level = c.grade_level");
        dataSql.append((CharSequence)where);
        dataSql.append(" ORDER BY c.grade_level ASC, c.id DESC LIMIT ? OFFSET ?");
        args.add(pageRequest.limit());
        args.add(pageRequest.offset());
        List items = this.jdbcTemplate.queryForList(dataSql.toString(), args.toArray());
        return new PageResult<Map<String, Object>>(items, total, pageRequest.page(), pageRequest.size());
    }

    public Map<String, Object> getClass(long id) {
        this.accessControlService.ensureCanAccessClass(id);
        return this.first(this.jdbcTemplate.queryForList("SELECT c.id, c.class_code AS classCode, c.class_name AS className, c.grade_session AS gradeSession, c.grade_level AS gradeLevel, eg.grade_name AS gradeName, c.head_teacher_user_id AS headTeacherUserId, u.real_name AS headTeacherName, c.is_key_class AS isKeyClass, c.course_combination AS courseCombination, c.foreign_language AS foreignLanguage, c.ph_or_hi AS phOrHi, c.elective_two AS electiveTwo, c.status, c.created_at AS createdAt FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id LEFT JOIN school_enroll_grade eg ON eg.grade_level = c.grade_level WHERE c.id = ? AND c.is_deleted = 0", new Object[]{id}));
    }

    public Map<String, Object> createClass(Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        String classNo = this.requiredString(request, "classNo");
        this.validateClassNo(classNo);
        String gradeSession = this.requiredString(request, "gradeSession");
        Integer gradeLevel = this.requiredInteger(request, "gradeLevel");
        Long headTeacherUserId = this.requiredLong(request, "headTeacherUserId");
        Integer isKeyClass = this.optionalInteger(request, "isKeyClass", 0);
        Integer status = this.optionalInteger(request, "status", 1);
        String foreignLanguage = this.optionalString(request, "foreignLanguage", null);
        String phOrHi = this.optionalString(request, "phOrHi", null);
        String electiveTwo = this.optionalString(request, "electiveTwo", null);
        String courseCombination = null;
        if (foreignLanguage != null && phOrHi != null && electiveTwo != null) {
            courseCombination = foreignLanguage + "-" + phOrHi + "-" + electiveTwo.replace(",", "-");
        }
        String gradeName = this.getGradeName(gradeLevel);
        String classCode = this.buildClassCode(gradeName, classNo, courseCombination);
        String className = this.buildClassName(gradeName, classNo, courseCombination);
        if (this.exists("SELECT COUNT(1) FROM school_class WHERE class_code = ? AND is_deleted = 0", classCode) > 0) {
            throw new IllegalArgumentException("班级编码已存在");
        }
        long id = this.insert("school_class", "INSERT INTO school_class (class_code, class_name, grade_session, grade_level, head_teacher_user_id, is_key_class, course_combination, foreign_language, ph_or_hi, elective_two, status, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", classCode, className, gradeSession, gradeLevel, headTeacherUserId, isKeyClass, courseCombination, foreignLanguage, phOrHi, electiveTwo, status);
        return this.getClass(id);
    }

    public Map<String, Object> updateClass(long id, Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        if (request.containsKey("classCode")) {
            throw new IllegalArgumentException("班级编码不可修改");
        }
        if (request.containsKey("className")) {
            throw new IllegalArgumentException("班级名称由系统自动生成，不可手动修改");
        }
        boolean needRegenClassInfo = request.containsKey("classNo") || request.containsKey("gradeLevel")
            || request.containsKey("foreignLanguage") || request.containsKey("phOrHi") || request.containsKey("electiveTwo");
        ArrayList<Object> args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE school_class SET ");
        boolean first = true;
        if (request.containsKey("gradeSession")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("grade_session = ?");
            args.add(this.requiredString(request, "gradeSession"));
            first = false;
        }
        if (request.containsKey("gradeLevel")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("grade_level = ?");
            args.add(this.requiredInteger(request, "gradeLevel"));
            first = false;
        }
        if (request.containsKey("headTeacherUserId")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("head_teacher_user_id = ?");
            args.add(this.optionalLong(request, "headTeacherUserId"));
            first = false;
        }
        if (request.containsKey("isKeyClass")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("is_key_class = ?");
            args.add(this.optionalInteger(request, "isKeyClass", 0));
            first = false;
        }
        if (request.containsKey("status")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("status = ?");
            args.add(this.optionalInteger(request, "status", 1));
            first = false;
        }
        if (request.containsKey("foreignLanguage")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("foreign_language = ?");
            args.add(this.optionalString(request, "foreignLanguage", null));
            first = false;
        }
        if (request.containsKey("phOrHi")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("ph_or_hi = ?");
            args.add(this.optionalString(request, "phOrHi", null));
            first = false;
        }
        if (request.containsKey("electiveTwo")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("elective_two = ?");
            args.add(this.optionalString(request, "electiveTwo", null));
            first = false;
        }
        if (needRegenClassInfo) {
            String classNo;
            String string = classNo = request.containsKey("classNo") ? this.requiredString(request, "classNo") : null;
            if (classNo != null) {
                this.validateClassNo(classNo);
            }
            Map<String, Object> current = this.getClass(id);
            int effectiveGradeLevel = request.containsKey("gradeLevel") ? this.requiredInteger(request, "gradeLevel") : this.optionalInteger(current, "gradeLevel", 0);
            String effectiveClassNo = classNo != null ? classNo : this.extractClassNo(current);
            String effectiveForeignLanguage = request.containsKey("foreignLanguage") ? this.optionalString(request, "foreignLanguage", null) : (String) current.get("foreignLanguage");
            String effectivePhOrHi = request.containsKey("phOrHi") ? this.optionalString(request, "phOrHi", null) : (String) current.get("phOrHi");
            String effectiveElectiveTwo = request.containsKey("electiveTwo") ? this.optionalString(request, "electiveTwo", null) : (String) current.get("electiveTwo");
            String courseCombination = null;
            if (effectiveForeignLanguage != null && effectivePhOrHi != null && effectiveElectiveTwo != null) {
                courseCombination = effectiveForeignLanguage + "-" + effectivePhOrHi + "-" + effectiveElectiveTwo.replace(",", "-");
            }
            String gradeName = this.getGradeName(effectiveGradeLevel);
            String newClassCode = this.buildClassCode(gradeName, effectiveClassNo, courseCombination);
            String newClassName = this.buildClassName(gradeName, effectiveClassNo, courseCombination);
            if (this.exists("SELECT COUNT(1) FROM school_class WHERE class_code = ? AND is_deleted = 0 AND id <> ?", newClassCode, id) > 0) {
                throw new IllegalArgumentException("班级编码已存在");
            }
            if (!first) {
                sql.append(", ");
            }
            sql.append("class_code = ?, class_name = ?, course_combination = ?");
            args.add(newClassCode);
            args.add(newClassName);
            args.add(courseCombination);
            first = false;
        }
        if (args.isEmpty()) {
            return this.getClass(id);
        }
        sql.append(", updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0");
        args.add(id);
        this.jdbcTemplate.update(sql.toString(), args.toArray());
        return this.getClass(id);
    }

    public void deleteClass(long id) {
        this.accessControlService.ensureSuperAdmin();
        this.jdbcTemplate.update("UPDATE school_class SET status = 0, is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
    }

    public List<Map<String, Object>> listClassSubjects(long academicTermId, long classId) {
        this.accessControlService.ensureCanAccessClass(classId);
        this.ensureClassSubjects(academicTermId, classId);
        return this.jdbcTemplate.queryForList("SELECT cs.id, cs.academic_term_id AS academicTermId, cs.class_id AS classId, c.class_name AS className, cs.subject_id AS subjectId, s.subject_name AS subjectName, cs.source_grade_subject_id AS sourceGradeSubjectId, s.min_score AS minScore, s.max_score AS maxScore, cs.teacher_user_id AS teacherUserId, u.real_name AS teacherName, cs.status, cs.created_at AS createdAt FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_subject s ON s.id = cs.subject_id LEFT JOIN sys_user u ON u.id = cs.teacher_user_id WHERE cs.academic_term_id = ? AND cs.class_id = ? ORDER BY cs.id", new Object[]{academicTermId, classId});
    }

    private void ensureClassSubjects(long academicTermId, long classId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class_subject WHERE academic_term_id = ? AND class_id = ?", Integer.class, new Object[]{academicTermId, classId});
        if (count != null && count > 0) {
            return;
        }
        List classes = this.jdbcTemplate.queryForList("SELECT grade_level AS gradeLevel FROM school_class WHERE id = ? AND is_deleted = 0", new Object[]{classId});
        if (classes.isEmpty()) {
            return;
        }
        Integer gradeLevel = this.optionalInteger((Map)classes.get(0), "gradeLevel", null);
        if (gradeLevel == null) {
            return;
        }
        List<Map<String, Object>> gradeSubjects = this.jdbcTemplate.queryForList("SELECT gs.id AS gradeSubjectId, gs.subject_id AS subjectId, gs.status AS status FROM school_grade_subject gs WHERE gs.academic_term_id = ? AND gs.grade_level = ? AND gs.status = 1 ORDER BY gs.sort_order, gs.id", new Object[]{academicTermId, gradeLevel});
        for (Map<String, Object> gradeSubject : gradeSubjects) {
            this.insert("school_class_subject", "INSERT INTO school_class_subject (academic_term_id, class_id, subject_id, source_grade_subject_id, status) VALUES (?, ?, ?, ?, ?)", academicTermId, classId, this.requiredLong(gradeSubject, "subjectId"), this.requiredLong(gradeSubject, "gradeSubjectId"), this.optionalInteger(gradeSubject, "status", 1));
        }
    }

    public Map<String, Object> createClassSubject(Map<String, Object> request) {
        this.accessControlService.ensureHeadTeacherOrAdmin();
        long academicTermId = this.requiredLong(request, "academicTermId");
        long classId = this.requiredLong(request, "classId");
        long subjectId = this.requiredLong(request, "subjectId");
        Long teacherUserId = this.optionalLong(request, "teacherUserId");
        this.accessControlService.ensureCanAccessClass(classId);
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class_subject WHERE academic_term_id = ? AND class_id = ? AND subject_id = ?", Integer.class, new Object[]{academicTermId, classId, subjectId});
        if (count != null && count > 0) {
            throw new IllegalArgumentException("该班级已配置此课程");
        }
        long id = this.insert("school_class_subject", "INSERT INTO school_class_subject (academic_term_id, class_id, subject_id, teacher_user_id, status) VALUES (?, ?, ?, ?, 1)", academicTermId, classId, subjectId, teacherUserId);
        return this.first(this.jdbcTemplate.queryForList("SELECT cs.id, cs.academic_term_id AS academicTermId, cs.class_id AS classId, c.class_name AS className, cs.subject_id AS subjectId, s.subject_name AS subjectName, cs.source_grade_subject_id AS sourceGradeSubjectId, s.min_score AS minScore, s.max_score AS maxScore, cs.teacher_user_id AS teacherUserId, u.real_name AS teacherName, cs.status, cs.created_at AS createdAt FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_subject s ON s.id = cs.subject_id LEFT JOIN sys_user u ON u.id = cs.teacher_user_id WHERE cs.id = ?", new Object[]{id}));
    }

    public void deleteClassSubject(long id) {
        Integer resultCount;
        this.accessControlService.ensureHeadTeacherOrAdmin();
        List rows = this.jdbcTemplate.queryForList("SELECT id, class_id AS classId FROM school_class_subject WHERE id = ?", new Object[]{id});
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("班级课程不存在");
        }
        Map row = (Map)rows.get(0);
        Long classId = this.optionalLong(row, "classId");
        if (classId != null) {
            this.accessControlService.ensureCanAccessClass(classId);
        }
        if ((resultCount = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_student_course_result WHERE class_subject_id = ?", Integer.class, new Object[]{id})) != null && resultCount > 0) {
            throw new IllegalArgumentException("该课程已有成绩记录，无法删除。如需删除，请先清空相关成绩记录。");
        }
        this.jdbcTemplate.update("DELETE FROM school_class_subject WHERE id = ?", new Object[]{id});
    }

    public List<Map<String, Object>> searchTeachers(String keyword) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT u.id, u.real_name AS realName, u.login_name AS loginName FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id WHERE u.is_deleted = 0 AND r.role_code = 'TEACHER' AND u.status = 1");
        ArrayList<String> args = new ArrayList<String>();
        if (StringUtils.hasText((String)keyword)) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(u.real_name) LIKE ? OR LOWER(u.login_name) LIKE ?)");
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY u.real_name ASC LIMIT 20");
        return this.jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public void saveClassTeacher(long classSubjectId, long teacherUserId) {
        this.accessControlService.ensureCanAccessClassSubject(classSubjectId);
        this.jdbcTemplate.update("UPDATE school_class_subject SET teacher_user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{teacherUserId, classSubjectId});
    }

    public void batchSaveClassTeachers(Map<String, Object> request) {
        List<Map<String, Object>> assignments = this.mapList(request.get("assignments"));
        for (Map<String, Object> assignment : assignments) {
            this.saveClassTeacher(this.requiredLong(assignment, "classSubjectId"), this.requiredLong(assignment, "teacherUserId"));
        }
    }

    private void validateClassNo(String classNo) {
        if (classNo == null || !classNo.matches("\\d+")) {
            throw new IllegalArgumentException("班级编号仅允许输入数字");
        }
    }

    private String getGradeName(int gradeLevel) {
        List rows = this.jdbcTemplate.queryForList("SELECT grade_name FROM school_enroll_grade WHERE grade_level = ? AND status = 1", new Object[]{gradeLevel});
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该年级（level=" + gradeLevel + "）未在招收年级中配置");
        }
        return (String)((Map)rows.get(0)).get("grade_name");
    }

    private String buildClassCode(String gradeName, String classNo, String courseCombination) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String code = date + gradeName + classNo;
        if (courseCombination != null) {
            code = code + "-" + courseCombination;
        }
        return code;
    }

    private String buildClassName(String gradeName, String classNo, String courseCombination) {
        String name = gradeName + "(" + classNo + ")班";
        if (courseCombination != null) {
            String comboLabel = this.combinationLabel(courseCombination);
            if (comboLabel != null && !comboLabel.isEmpty()) {
                name = name + "-" + comboLabel;
            }
        }
        return name;
    }

    private String combinationLabel(String courseCombination) {
        if (courseCombination == null) return null;
        String[] parts = courseCombination.split("-");
        if (parts.length < 4) return courseCombination;
        // Map codes to Chinese short names
        String fl = "EN".equals(parts[0]) ? "英" : "JA".equals(parts[0]) ? "日" : parts[0];
        String ph = "PH".equals(parts[1]) ? "物" : "HI".equals(parts[1]) ? "史" : parts[1];
        String e1 = this.shortElectiveLabel(parts[2]);
        String e2 = this.shortElectiveLabel(parts[3]);
        return fl + "-" + ph + "-" + e1 + "-" + e2;
    }

    private String shortElectiveLabel(String code) {
        switch (code) {
            case "CH": return "化";
            case "BI": return "生";
            case "GE": return "地";
            case "PO": return "政";
            default: return code;
        }
    }

    private String extractClassNo(Map<String, Object> clazz) {
        String className = (String)clazz.get("className");
        if (className == null) {
            throw new IllegalArgumentException("无法从班级名称中提取班级编号");
        }
        int leftParen = className.lastIndexOf(40);
        int rightParen = className.lastIndexOf(41);
        if (leftParen < 0 || rightParen <= leftParen) {
            throw new IllegalArgumentException("班级名称格式不正确，无法提取班级编号");
        }
        return className.substring(leftParen + 1, rightParen);
    }
}

