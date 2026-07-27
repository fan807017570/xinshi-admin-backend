/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.student;

import com.xinshi.admin.domain.shared.DomainException;
import java.time.LocalDateTime;

public class Student {
    private final long id;
    private String studentNo;
    private String studentName;
    private int gender;
    private long classId;
    private String className;
    private int status;
    private String remark;
    private final LocalDateTime createdAt;

    private Student(long id, String studentNo, String studentName, int gender, long classId, String className, int status, String remark, LocalDateTime createdAt) {
        this.id = id;
        this.studentNo = studentNo;
        this.studentName = studentName;
        this.gender = gender;
        this.classId = classId;
        this.className = className;
        this.status = status;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public static Student create(String studentNo, String studentName, int gender, long classId, String remark) {
        if (Student.isBlank(studentNo)) {
            throw new DomainException("学号不能为空");
        }
        if (Student.isBlank(studentName)) {
            throw new DomainException("学生姓名不能为空");
        }
        return new Student(0L, studentNo.trim(), studentName.trim(), gender, classId, null, 1, remark, LocalDateTime.now());
    }

    public static Student rehydrate(long id, String studentNo, String studentName, int gender, long classId, String className, int status, String remark, LocalDateTime createdAt) {
        return new Student(id, studentNo, studentName, gender, classId, className, status, remark, createdAt);
    }

    public void update(String studentNo, String studentName, Integer gender, Long classId, String remark, Integer status) {
        if (studentNo != null) {
            this.studentNo = studentNo.trim();
        }
        if (studentName != null) {
            this.studentName = studentName.trim();
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (classId != null) {
            this.classId = classId;
        }
        if (remark != null) {
            this.remark = remark;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public void deactivate() {
        this.status = 0;
    }

    public long getId() {
        return this.id;
    }

    public String getStudentNo() {
        return this.studentNo;
    }

    public String getStudentName() {
        return this.studentName;
    }

    public int getGender() {
        return this.gender;
    }

    public long getClassId() {
        return this.classId;
    }

    public String getClassName() {
        return this.className;
    }

    public int getStatus() {
        return this.status;
    }

    public String getRemark() {
        return this.remark;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

