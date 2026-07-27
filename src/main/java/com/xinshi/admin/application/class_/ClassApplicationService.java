/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.class_;

import com.xinshi.admin.domain.class_.SchoolClass;
import com.xinshi.admin.domain.class_.SchoolClassRepository;
import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
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
public class ClassApplicationService {
    private final SchoolClassRepository classRepository;
    private final AuthorizationService authorizationService;
    private final AuthSession authSession;
    private final JdbcTemplate jdbcTemplate;

    public ClassApplicationService(SchoolClassRepository classRepository, AuthorizationService authorizationService, AuthSession authSession, JdbcTemplate jdbcTemplate) {
        this.classRepository = classRepository;
        this.authorizationService = authorizationService;
        this.authSession = authSession;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<SchoolClass> listClasses(String gradeSession, String mode, PageRequest pageRequest) {
        List<String> roles = this.authSession.roles();
        long userId = this.authSession.userId();
        long total = this.classRepository.count(gradeSession, mode, userId, roles);
        List<SchoolClass> items = this.classRepository.findAll(gradeSession, mode, userId, roles, pageRequest.limit(), pageRequest.offset());
        return new PageResult<SchoolClass>(items, total, pageRequest.page(), pageRequest.size());
    }

    public SchoolClass getClass(long id) {
        this.authorizationService.ensureCanAccessClass(id);
        return this.classRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("班级不存在"));
    }

    public SchoolClass createClass(String classNo, String gradeSession, int gradeLevel, long headTeacherUserId, int isKeyClass, int status) {
        this.authorizationService.ensureSuperAdmin();
        this.validateClassNo(classNo);
        String gradeName = this.getGradeName(gradeLevel);
        String classCode = this.buildClassCode(gradeName, classNo);
        String className = this.buildClassName(gradeName, classNo);
        if (this.classRepository.findByCode(classCode).isPresent()) {
            throw new IllegalArgumentException("班级编码已存在");
        }
        SchoolClass schoolClass = SchoolClass.create(classCode, className, gradeSession, gradeLevel, headTeacherUserId, isKeyClass, status);
        return this.classRepository.save(schoolClass);
    }

    public SchoolClass updateClass(long id, String classNo, String gradeSession, Integer gradeLevel, Long headTeacherUserId, Integer isKeyClass, Integer status) {
        this.authorizationService.ensureSuperAdmin();
        SchoolClass schoolClass = this.classRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("班级不存在"));
        if (classNo != null) {
            this.validateClassNo(classNo);
        }
        String newClassCode = null;
        String newClassName = null;
        if (classNo != null || gradeLevel != null) {
            int effectiveGradeLevel = gradeLevel != null ? gradeLevel.intValue() : schoolClass.getGradeLevel();
            String effectiveClassNo = classNo != null ? classNo : this.extractClassNo(schoolClass.getClassName());
            String gradeName = this.getGradeName(effectiveGradeLevel);
            newClassCode = this.buildClassCode(gradeName, effectiveClassNo);
            newClassName = this.buildClassName(gradeName, effectiveClassNo);
        }
        schoolClass.update(newClassCode, newClassName, gradeSession, gradeLevel, headTeacherUserId, isKeyClass, status);
        this.classRepository.update(schoolClass);
        return this.classRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("班级不存在"));
    }

    public void deleteClass(long id) {
        this.authorizationService.ensureSuperAdmin();
        this.classRepository.deactivate(id);
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

    private String buildClassCode(String gradeName, String classNo) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return date + gradeName + classNo;
    }

    private String buildClassName(String gradeName, String classNo) {
        return gradeName + "(" + classNo + ")班";
    }

    private String extractClassNo(String className) {
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

