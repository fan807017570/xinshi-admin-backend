/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.school;

import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.interfaces.web.security.AuthContext;
import com.xinshi.admin.interfaces.web.security.ForbiddenException;
import com.xinshi.admin.interfaces.web.security.UnauthorizedException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService
extends SchoolBaseService {
    public AccessControlService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public Map<String, Object> currentSession() {
        Map<String, Object> session = AuthContext.get();
        if (session == null || session.isEmpty()) {
            throw new UnauthorizedException("未登录");
        }
        return session;
    }

    public List<String> currentRoles() {
        return this.stringList(this.currentSession().get("roles"));
    }

    public boolean hasRole(String roleCode) {
        return this.currentRoles().contains(roleCode);
    }

    public long currentUserId() {
        Object value = this.currentSession().get("userId");
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    public void ensureSuperAdmin() {
        if (!this.hasRole("SUPER_ADMIN")) {
            throw new ForbiddenException("仅超级管理员可操作");
        }
    }

    public void ensureMutableUser(long userId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id WHERE u.id = ? AND u.is_deleted = 0 AND r.is_protected = 1", Integer.class, new Object[]{userId});
        if (count != null && count > 0) {
            throw new IllegalArgumentException("系统保护角色不能通过普通管理接口修改");
        }
    }

    public void ensureReadableAcademicConfig() {
        if (!this.hasRole("SUPER_ADMIN") && !this.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可查看");
        }
    }

    public void ensureCanManageStudents() {
        if (!this.hasRole("SUPER_ADMIN") && !this.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可操作");
        }
    }

    public void ensureHeadTeacherOrAdmin() {
        if (!this.hasRole("SUPER_ADMIN") && !this.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可操作");
        }
    }

    public void ensureTeacherCanWriteResults() {
        if (!(this.hasRole("SUPER_ADMIN") || this.hasRole("HEAD_TEACHER") || this.hasRole("TEACHER"))) {
            throw new ForbiddenException("仅管理员、班主任和教师可操作");
        }
    }

    public void ensureCanGenerateTranscript() {
        if (!this.hasRole("SUPER_ADMIN") && !this.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可生成成绩单");
        }
    }

    public void ensureValidSubjectScoreRange(double minScore, double maxScore) {
        if (minScore < 0.0 || maxScore < 0.0) {
            throw new IllegalArgumentException("课程分数范围不能小于 0");
        }
        if (minScore > maxScore) {
            throw new IllegalArgumentException("课程最小分不能大于最大分");
        }
    }

    public void ensureScoreInSubjectRange(double score, Map<String, Object> subjectConfig) {
        double minScore = this.optionalDouble(subjectConfig, "minScore", 0.0);
        double maxScore = this.optionalDouble(subjectConfig, "maxScore", 100.0);
        if (score < minScore || score > maxScore) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "成绩必须在 %.2f 到 %.2f 之间", minScore, maxScore));
        }
    }

    public void ensureCanAccessClass(long classId) {
        Integer count;
        Long headTeacherUserId;
        if (this.hasRole("SUPER_ADMIN")) {
            return;
        }
        List classes = this.jdbcTemplate.queryForList("SELECT id, head_teacher_user_id AS headTeacherUserId FROM school_class WHERE id = ? AND is_deleted = 0", new Object[]{classId});
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("班级不存在");
        }
        if (this.hasRole("HEAD_TEACHER") && (headTeacherUserId = this.optionalLong((Map)classes.get(0), "headTeacherUserId")) != null && headTeacherUserId.longValue() == this.currentUserId()) {
            return;
        }
        if (this.hasRole("TEACHER") && (count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class_subject WHERE class_id = ? AND teacher_user_id = ? AND status = 1", Integer.class, new Object[]{classId, this.currentUserId()})) != null && count > 0) {
            return;
        }
        throw new ForbiddenException("无权访问该班级");
    }

    public void ensureCanAccessStudent(long studentId) {
        Integer count;
        if (this.hasRole("SUPER_ADMIN")) {
            return;
        }
        List students = this.jdbcTemplate.queryForList("SELECT id, class_id AS classId FROM school_student WHERE id = ? AND is_deleted = 0", new Object[]{studentId});
        if (students.isEmpty()) {
            throw new IllegalArgumentException("学生不存在");
        }
        Long classId = this.optionalLong((Map)students.get(0), "classId");
        if (this.hasRole("HEAD_TEACHER") && classId != null) {
            this.ensureCanAccessClass(classId);
            return;
        }
        if (this.hasRole("PARENT") && (count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_student_parent WHERE student_id = ? AND parent_user_id = ?", Integer.class, new Object[]{studentId, this.currentUserId()})) != null && count > 0) {
            return;
        }
        throw new ForbiddenException("无权访问该学生");
    }

    public void ensureStudentBelongsToClass(long studentId, long classId) {
        List students = this.jdbcTemplate.queryForList("SELECT id FROM school_student WHERE id = ? AND class_id = ? AND is_deleted = 0", new Object[]{studentId, classId});
        if (students.isEmpty()) {
            throw new ForbiddenException("学生不属于该班级");
        }
    }

    public void ensureCanAccessClassSubject(long classSubjectId) {
        Long teacherUserId;
        if (this.hasRole("SUPER_ADMIN")) {
            return;
        }
        List rows = this.jdbcTemplate.queryForList("SELECT id, class_id AS classId, teacher_user_id AS teacherUserId FROM school_class_subject WHERE id = ?", new Object[]{classSubjectId});
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("班级课程不存在");
        }
        Map row = (Map)rows.get(0);
        Long classId = this.optionalLong(row, "classId");
        if (this.hasRole("HEAD_TEACHER") && classId != null) {
            this.ensureCanAccessClass(classId);
            return;
        }
        if (this.hasRole("TEACHER") && (teacherUserId = this.optionalLong(row, "teacherUserId")) != null && teacherUserId.longValue() == this.currentUserId()) {
            return;
        }
        throw new ForbiddenException("无权访问该班级课程");
    }

    public void ensureCanAccessResult(long resultId) {
        if (this.hasRole("SUPER_ADMIN")) {
            return;
        }
        List rows = this.jdbcTemplate.queryForList("SELECT id, class_subject_id AS classSubjectId, student_id AS studentId FROM school_student_course_result WHERE id = ?", new Object[]{resultId});
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("成绩不存在");
        }
        Map row = (Map)rows.get(0);
        if (this.hasRole("TEACHER") || this.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClassSubject(this.requiredLong(row, "classSubjectId"));
            return;
        }
        if (this.hasRole("PARENT")) {
            this.ensureCanAccessStudent(this.requiredLong(row, "studentId"));
            return;
        }
        throw new ForbiddenException("无权访问该成绩");
    }

    public void ensureCanAccessComment(long commentId) {
        if (this.hasRole("SUPER_ADMIN")) {
            return;
        }
        List rows = this.jdbcTemplate.queryForList("SELECT id, class_id AS classId, student_id AS studentId FROM school_student_overall_comment WHERE id = ?", new Object[]{commentId});
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("评语不存在");
        }
        Map row = (Map)rows.get(0);
        if (this.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClass(this.requiredLong(row, "classId"));
            return;
        }
        if (this.hasRole("PARENT")) {
            this.ensureCanAccessStudent(this.requiredLong(row, "studentId"));
            return;
        }
        throw new ForbiddenException("无权访问该评语");
    }

    public void ensureCanAccessTranscript(long transcriptId) {
        if (this.hasRole("SUPER_ADMIN")) {
            return;
        }
        List rows = this.jdbcTemplate.queryForList("SELECT id, class_id AS classId, student_id AS studentId FROM school_transcript WHERE id = ?", new Object[]{transcriptId});
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("成绩单不存在");
        }
        Map row = (Map)rows.get(0);
        if (this.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClass(this.requiredLong(row, "classId"));
            return;
        }
        if (this.hasRole("PARENT")) {
            this.ensureCanAccessStudent(this.requiredLong(row, "studentId"));
            return;
        }
        throw new ForbiddenException("无权访问该成绩单");
    }

    public void ensureReadableResultScope(Long academicTermId, Long classId, Long studentId, Long classSubjectId) {
        if (classId != null) {
            this.ensureCanAccessClass(classId);
        }
        if (studentId != null) {
            this.ensureCanAccessStudent(studentId);
        }
        if (classSubjectId != null) {
            this.ensureCanAccessClassSubject(classSubjectId);
        }
    }

    public void ensureReadableCommentScope(Long academicTermId, Long classId, Long studentId) {
        if (classId != null) {
            this.ensureCanAccessClass(classId);
        }
        if (studentId != null) {
            this.ensureCanAccessStudent(studentId);
        }
    }

    public void ensureReadableTranscriptScope(Long academicTermId, Long classId, Long studentId) {
        if (classId != null) {
            this.ensureCanAccessClass(classId);
        }
        if (studentId != null) {
            this.ensureCanAccessStudent(studentId);
        }
    }

    public void ensureReadableResultRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        this.ensureCanAccessResult(this.requiredLong(row, "id"));
    }

    public void ensureReadableCommentRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        this.ensureCanAccessComment(this.requiredLong(row, "id"));
    }

    public void ensureReadableTranscriptRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        this.ensureCanAccessTranscript(this.requiredLong(row, "id"));
    }
}

