/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.student;

import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
import com.xinshi.admin.domain.student.Student;
import com.xinshi.admin.domain.student.StudentRepository;
import com.xinshi.admin.domain.studentparent.StudentParent;
import com.xinshi.admin.domain.studentparent.StudentParentRepository;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StudentApplicationService {
    private final StudentRepository studentRepository;
    private final StudentParentRepository studentParentRepository;
    private final AuthorizationService authorizationService;
    private final AuthSession authSession;

    public StudentApplicationService(StudentRepository studentRepository, StudentParentRepository studentParentRepository, AuthorizationService authorizationService, AuthSession authSession) {
        this.studentRepository = studentRepository;
        this.studentParentRepository = studentParentRepository;
        this.authorizationService = authorizationService;
        this.authSession = authSession;
    }

    public PageResult<Student> listStudents(Long classId, String keyword, Integer status, PageRequest pageRequest) {
        if (classId != null) {
            this.authorizationService.ensureCanAccessClass(classId);
        }
        long userId = this.authSession.userId();
        List<String> roles = this.authSession.roles();
        long total = this.studentRepository.count(classId, keyword, status, userId, roles);
        List<Student> items = this.studentRepository.findAll(classId, keyword, status, userId, roles, pageRequest.limit(), pageRequest.offset());
        return new PageResult<Student>(items, total, pageRequest.page(), pageRequest.size());
    }

    public Student getStudent(long id) {
        this.authorizationService.ensureCanAccessStudent(id);
        return this.studentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
    }

    public Student createStudent(String studentNo, String studentName, int gender, long classId, String remark) {
        this.authorizationService.ensureCanManageStudents();
        if (this.studentRepository.findByStudentNo(studentNo).isPresent()) {
            throw new IllegalArgumentException("学号已存在");
        }
        Student student = Student.create(studentNo, studentName, gender, classId, remark);
        return this.studentRepository.save(student);
    }

    public Student updateStudent(long id, String studentNo, String studentName, Integer gender, Long classId, String remark, Integer status) {
        this.authorizationService.ensureCanManageStudents();
        this.authorizationService.ensureCanAccessStudent(id);
        Student student = this.getStudent(id);
        student.update(studentNo, studentName, gender, classId, remark, status);
        this.studentRepository.update(student);
        return this.getStudent(id);
    }

    public void deleteStudent(long id) {
        this.authorizationService.ensureCanManageStudents();
        this.authorizationService.ensureCanAccessStudent(id);
        this.studentRepository.deactivate(id);
    }

    public void bindParents(long studentId, List<Map<String, Object>> parentMaps) {
        this.authorizationService.ensureCanManageStudents();
        this.authorizationService.ensureCanAccessStudent(studentId);
        ArrayList<StudentParent> parents = new ArrayList<StudentParent>();
        for (Map<String, Object> parentMap : parentMaps) {
            long parentUserId = this.toLong(parentMap.get("parentUserId"));
            String relationType = String.valueOf(parentMap.getOrDefault("relationType", ""));
            int isPrimary = this.toInt(parentMap.get("isPrimary"), 0);
            parents.add(StudentParent.create(parentUserId, relationType, isPrimary));
        }
        this.studentParentRepository.saveBatch(studentId, parents);
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

