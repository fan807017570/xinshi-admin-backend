/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.dao.DuplicateKeyException
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.student;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StudentManagementService
extends SchoolBaseService {
    private static final DateTimeFormatter STUDENT_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddmmss");
    private static final Pattern DIGIT_GROUP_PATTERN = Pattern.compile("\\d+");
    private static final int STUDENT_NO_RANDOM_BOUND = 1000;
    private static final int STUDENT_NO_MAX_GENERATE_ATTEMPTS = 20;
    private final AccessControlService accessControlService;

    public StudentManagementService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
    }

    public PageResult<Map<String, Object>> listStudents(Long classId, String keyword, Integer status, PageRequest pageRequest) {
        if (classId != null) {
            this.accessControlService.ensureCanAccessClass(classId);
        }
        StringBuilder where = new StringBuilder(" WHERE s.is_deleted = 0");
        ArrayList<Object> args = new ArrayList<Object>();
        if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN") && classId == null) {
            where.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = s.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(this.accessControlService.currentUserId());
        }
        if (this.accessControlService.hasRole("PARENT") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = s.id AND sp.parent_user_id = ?)");
            args.add(this.accessControlService.currentUserId());
        }
        if (classId != null) {
            where.append(" AND s.class_id = ?");
            args.add(classId);
        }
        if (StringUtils.hasText((String)keyword)) {
            where.append(" AND (s.student_no LIKE ? OR s.student_name LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (status != null) {
            where.append(" AND s.status = ?");
            args.add(status);
        }
        String countSql = "SELECT COUNT(1) FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id" + where;
        long total = (Long)this.jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        StringBuilder dataSql = new StringBuilder("SELECT s.id, s.student_no AS studentNo, s.student_name AS studentName, s.gender, s.class_id AS classId, c.class_name AS className, s.status, s.remark, s.created_at AS createdAt, s.updated_at AS updatedAt FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id");
        dataSql.append((CharSequence)where);
        dataSql.append(" ORDER BY s.id DESC LIMIT ? OFFSET ?");
        args.add(pageRequest.limit());
        args.add(pageRequest.offset());
        List items = this.jdbcTemplate.queryForList(dataSql.toString(), args.toArray());
        return new PageResult<Map<String, Object>>(items, total, pageRequest.page(), pageRequest.size());
    }

    public Map<String, Object> createStudent(Map<String, Object> request) {
        this.accessControlService.ensureCanManageStudents();
        String studentName = this.requiredString(request, "studentName");
        int gender = this.optionalInteger(request, "gender", 0);
        long classId = this.requiredLong(request, "classId");
        String remark = this.optionalString(request, "remark", null);
        this.accessControlService.ensureCanAccessClass(classId);
        Map<String, Object> clazz = this.classForStudentNo(classId);
        if (clazz.isEmpty()) {
            throw new IllegalArgumentException("班级不存在");
        }
        for (int attempt = 0; attempt < 20; ++attempt) {
            String studentNo = this.generateStudentNo(clazz, classId);
            try {
                long id = this.insert("school_student", "INSERT INTO school_student (student_no, student_name, gender, class_id, status, remark, is_deleted) VALUES (?, ?, ?, ?, 1, ?, 0)", studentNo, studentName, gender, classId, remark);
                return this.getStudent(id);
            }
            catch (DuplicateKeyException ex) {
                if (attempt != 19) continue;
                throw new IllegalStateException("自动生成学号冲突，请重试", ex);
            }
        }
        throw new IllegalStateException("自动生成学号失败，请重试");
    }

    public Map<String, Object> getStudent(long id) {
        this.accessControlService.ensureCanAccessStudent(id);
        return this.first(this.jdbcTemplate.queryForList("SELECT s.id, s.student_no AS studentNo, s.student_name AS studentName, s.gender, s.class_id AS classId, c.class_name AS className, s.status, s.remark, s.created_at AS createdAt FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id WHERE s.id = ? AND s.is_deleted = 0", new Object[]{id}));
    }

    public Map<String, Object> updateStudent(long id, Map<String, Object> request) {
        this.accessControlService.ensureCanManageStudents();
        this.accessControlService.ensureCanAccessStudent(id);
        ArrayList<Object> args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE school_student SET ");
        boolean first = true;
        if (request.containsKey("studentNo")) {
            sql.append("student_no = ?");
            args.add(this.requiredString(request, "studentNo"));
            first = false;
        }
        if (request.containsKey("studentName")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("student_name = ?");
            args.add(this.requiredString(request, "studentName"));
            first = false;
        }
        if (request.containsKey("gender")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("gender = ?");
            args.add(this.optionalInteger(request, "gender", 0));
            first = false;
        }
        if (request.containsKey("classId")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("class_id = ?");
            args.add(this.requiredLong(request, "classId"));
            first = false;
        }
        if (request.containsKey("remark")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("remark = ?");
            args.add(this.optionalString(request, "remark", null));
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
            return this.getStudent(id);
        }
        sql.append(", updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0");
        args.add(id);
        this.jdbcTemplate.update(sql.toString(), args.toArray());
        return this.getStudent(id);
    }

    public void deleteStudent(long id) {
        this.accessControlService.ensureCanManageStudents();
        this.accessControlService.ensureCanAccessStudent(id);
        this.jdbcTemplate.update("UPDATE school_student SET status = 0, is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
    }

    public void bindParents(long studentId, Map<String, Object> request) {
        this.accessControlService.ensureCanManageStudents();
        this.accessControlService.ensureCanAccessStudent(studentId);
        List<Map<String, Object>> parents = this.mapList(request.get("parents"));
        this.jdbcTemplate.update("DELETE FROM school_student_parent WHERE student_id = ?", new Object[]{studentId});
        for (Map<String, Object> parent : parents) {
            long parentUserId = this.requiredLong(parent, "parentUserId");
            String relationType = this.requiredString(parent, "relationType");
            int isPrimary = this.optionalInteger(parent, "isPrimary", 0);
            this.insert("school_student_parent", "INSERT INTO school_student_parent (student_id, parent_user_id, relation_type, is_primary) VALUES (?, ?, ?, ?)", studentId, parentUserId, relationType, isPrimary);
        }
    }

    private Map<String, Object> classForStudentNo(long classId) {
        return this.first(this.jdbcTemplate.queryForList("SELECT id, class_code AS classCode, class_name AS className, grade_level AS gradeLevel FROM school_class WHERE id = ? AND is_deleted = 0", new Object[]{classId}));
    }

    private String generateStudentNo(Map<String, Object> clazz, long classId) {
        String timePart = LocalDateTime.now().format(STUDENT_NO_TIME_FORMATTER);
        String gradePart = String.valueOf(this.optionalInteger(clazz, "gradeLevel", 0));
        String classPart = this.extractClassNumber(String.valueOf(clazz.getOrDefault("className", "")), String.valueOf(clazz.getOrDefault("classCode", "")), classId);
        String randomPart = String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        return timePart + gradePart + classPart + randomPart;
    }

    private String extractClassNumber(String className, String classCode, long classId) {
        String classNumber = this.lastDigitGroup(className);
        if (!StringUtils.hasText((String)classNumber)) {
            classNumber = this.lastDigitGroup(classCode);
        }
        return StringUtils.hasText((String)classNumber) ? classNumber : String.valueOf(classId);
    }

    private String lastDigitGroup(String text) {
        if (!StringUtils.hasText((String)text)) {
            return "";
        }
        Matcher matcher = DIGIT_GROUP_PATTERN.matcher(text);
        String last = "";
        while (matcher.find()) {
            last = matcher.group();
        }
        return last;
    }
}

