/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.infrastructure.auth;

import com.xinshi.admin.domain.class_.SchoolClassRepository;
import com.xinshi.admin.domain.classsubject.ClassSubjectRepository;
import com.xinshi.admin.domain.courseresult.CourseResultRepository;
import com.xinshi.admin.domain.overallcomment.OverallCommentRepository;
import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
import com.xinshi.admin.domain.student.StudentRepository;
import com.xinshi.admin.domain.studentparent.StudentParentRepository;
import com.xinshi.admin.domain.transcript.TranscriptRepository;
import com.xinshi.admin.interfaces.web.security.ForbiddenException;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuthorizationService
implements AuthorizationService {
    private final AuthSession authSession;
    private final JdbcTemplate jdbcTemplate;
    private final SchoolClassRepository classRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final StudentRepository studentRepository;
    private final StudentParentRepository studentParentRepository;
    private final CourseResultRepository courseResultRepository;
    private final OverallCommentRepository overallCommentRepository;
    private final TranscriptRepository transcriptRepository;

    public DefaultAuthorizationService(AuthSession authSession, JdbcTemplate jdbcTemplate, SchoolClassRepository classRepository, ClassSubjectRepository classSubjectRepository, StudentRepository studentRepository, StudentParentRepository studentParentRepository, CourseResultRepository courseResultRepository, OverallCommentRepository overallCommentRepository, TranscriptRepository transcriptRepository) {
        this.authSession = authSession;
        this.jdbcTemplate = jdbcTemplate;
        this.classRepository = classRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.studentRepository = studentRepository;
        this.studentParentRepository = studentParentRepository;
        this.courseResultRepository = courseResultRepository;
        this.overallCommentRepository = overallCommentRepository;
        this.transcriptRepository = transcriptRepository;
    }

    @Override
    public void ensureSuperAdmin() {
        if (!this.authSession.hasRole("SUPER_ADMIN")) {
            throw new ForbiddenException("仅超级管理员可操作");
        }
    }

    @Override
    public void ensureHeadTeacherOrAdmin() {
        if (!this.authSession.hasRole("SUPER_ADMIN") && !this.authSession.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可操作");
        }
    }

    @Override
    public void ensureTeacherCanWriteResults() {
        if (!(this.authSession.hasRole("SUPER_ADMIN") || this.authSession.hasRole("HEAD_TEACHER") || this.authSession.hasRole("TEACHER"))) {
            throw new ForbiddenException("仅管理员、班主任和教师可操作");
        }
    }

    @Override
    public void ensureCanManageStudents() {
        if (!this.authSession.hasRole("SUPER_ADMIN") && !this.authSession.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可操作");
        }
    }

    @Override
    public void ensureCanGenerateTranscript() {
        if (!this.authSession.hasRole("SUPER_ADMIN") && !this.authSession.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可生成成绩单");
        }
    }

    @Override
    public void ensureReadableAcademicConfig() {
        if (!this.authSession.hasRole("SUPER_ADMIN") && !this.authSession.hasRole("HEAD_TEACHER")) {
            throw new ForbiddenException("仅管理员和班主任可查看");
        }
    }

    @Override
    public void ensureCanAccessClass(long classId) {
        Integer count;
        if (this.authSession.hasRole("SUPER_ADMIN")) {
            return;
        }
        Map result = this.jdbcTemplate.queryForMap("SELECT id, head_teacher_user_id FROM school_class WHERE id = ? AND is_deleted = 0", new Object[]{classId});
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("班级不存在");
        }
        long headTeacherUserId = ((Number)result.get("head_teacher_user_id")).longValue();
        if (this.authSession.hasRole("HEAD_TEACHER") && headTeacherUserId == this.authSession.userId()) {
            return;
        }
        if (this.authSession.hasRole("TEACHER") && (count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class_subject WHERE class_id = ? AND teacher_user_id = ? AND status = 1", Integer.class, new Object[]{classId, this.authSession.userId()})) != null && count > 0) {
            return;
        }
        throw new ForbiddenException("无权访问该班级");
    }

    @Override
    public void ensureCanManageClass(long classId) {
        this.ensureSuperAdmin();
    }

    @Override
    public void ensureCanAccessStudent(long studentId) {
        Integer count;
        if (this.authSession.hasRole("SUPER_ADMIN")) {
            return;
        }
        Map result = this.jdbcTemplate.queryForMap("SELECT id, class_id FROM school_student WHERE id = ? AND is_deleted = 0", new Object[]{studentId});
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("学生不存在");
        }
        long classId = ((Number)result.get("class_id")).longValue();
        if (this.authSession.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClass(classId);
            return;
        }
        if (this.authSession.hasRole("PARENT") && (count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_student_parent WHERE student_id = ? AND parent_user_id = ?", Integer.class, new Object[]{studentId, this.authSession.userId()})) != null && count > 0) {
            return;
        }
        throw new ForbiddenException("无权访问该学生");
    }

    @Override
    public void ensureCanManageStudent(long studentId) {
        this.ensureHeadTeacherOrAdmin();
        if (this.authSession.hasRole("SUPER_ADMIN")) {
            return;
        }
        this.ensureCanAccessStudent(studentId);
    }

    @Override
    public void ensureCanAccessClassSubject(long classSubjectId) {
        Object teacherUserIdObj;
        if (this.authSession.hasRole("SUPER_ADMIN")) {
            return;
        }
        Map result = this.jdbcTemplate.queryForMap("SELECT id, class_id, teacher_user_id FROM school_class_subject WHERE id = ?", new Object[]{classSubjectId});
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("班级课程不存在");
        }
        long classId = ((Number)result.get("class_id")).longValue();
        if (this.authSession.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClass(classId);
            return;
        }
        if (this.authSession.hasRole("TEACHER") && (teacherUserIdObj = result.get("teacher_user_id")) != null && ((Number)teacherUserIdObj).longValue() == this.authSession.userId()) {
            return;
        }
        throw new ForbiddenException("无权访问该班级课程");
    }

    @Override
    public void ensureCanManageClassSubject(long classSubjectId) {
        this.ensureHeadTeacherOrAdmin();
        this.ensureCanAccessClassSubject(classSubjectId);
    }

    @Override
    public void ensureCanAccessResult(long resultId) {
        if (this.authSession.hasRole("SUPER_ADMIN")) {
            return;
        }
        Map result = this.jdbcTemplate.queryForMap("SELECT id, class_subject_id, student_id FROM school_student_course_result WHERE id = ?", new Object[]{resultId});
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("成绩不存在");
        }
        long classSubjectId = ((Number)result.get("class_subject_id")).longValue();
        long studentId = ((Number)result.get("student_id")).longValue();
        if (this.authSession.hasRole("TEACHER") || this.authSession.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClassSubject(classSubjectId);
            return;
        }
        if (this.authSession.hasRole("PARENT")) {
            this.ensureCanAccessStudent(studentId);
            return;
        }
        throw new ForbiddenException("无权访问该成绩");
    }

    @Override
    public void ensureCanAccessComment(long commentId) {
        if (this.authSession.hasRole("SUPER_ADMIN")) {
            return;
        }
        Map result = this.jdbcTemplate.queryForMap("SELECT id, class_id, student_id FROM school_student_overall_comment WHERE id = ?", new Object[]{commentId});
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("评语不存在");
        }
        long classId = ((Number)result.get("class_id")).longValue();
        long studentId = ((Number)result.get("student_id")).longValue();
        if (this.authSession.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClass(classId);
            return;
        }
        if (this.authSession.hasRole("PARENT")) {
            this.ensureCanAccessStudent(studentId);
            return;
        }
        throw new ForbiddenException("无权访问该评语");
    }

    @Override
    public void ensureCanAccessTranscript(long transcriptId) {
        if (this.authSession.hasRole("SUPER_ADMIN")) {
            return;
        }
        Map result = this.jdbcTemplate.queryForMap("SELECT id, class_id, student_id FROM school_transcript WHERE id = ?", new Object[]{transcriptId});
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("成绩单不存在");
        }
        long classId = ((Number)result.get("class_id")).longValue();
        long studentId = ((Number)result.get("student_id")).longValue();
        if (this.authSession.hasRole("HEAD_TEACHER")) {
            this.ensureCanAccessClass(classId);
            return;
        }
        if (this.authSession.hasRole("PARENT")) {
            this.ensureCanAccessStudent(studentId);
            return;
        }
        throw new ForbiddenException("无权访问该成绩单");
    }

    @Override
    public void ensureStudentBelongsToClass(long studentId, long classId) {
        Map result = this.jdbcTemplate.queryForMap("SELECT id FROM school_student WHERE id = ? AND class_id = ? AND is_deleted = 0", new Object[]{studentId, classId});
        if (result == null || result.isEmpty()) {
            throw new ForbiddenException("学生不属于该班级");
        }
    }

    @Override
    public void ensureMutableUser(long userId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id WHERE u.id = ? AND u.is_deleted = 0 AND r.is_protected = 1", Integer.class, new Object[]{userId});
        if (count != null && count > 0) {
            throw new IllegalArgumentException("系统保护角色不能通过普通管理接口修改");
        }
    }

    @Override
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

    @Override
    public void ensureReadableCommentScope(Long academicTermId, Long classId, Long studentId) {
        if (classId != null) {
            this.ensureCanAccessClass(classId);
        }
        if (studentId != null) {
            this.ensureCanAccessStudent(studentId);
        }
    }

    @Override
    public void ensureReadableTranscriptScope(Long academicTermId, Long classId, Long studentId) {
        if (classId != null) {
            this.ensureCanAccessClass(classId);
        }
        if (studentId != null) {
            this.ensureCanAccessStudent(studentId);
        }
    }
}

