/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.infrastructure.persistence.student;

import com.xinshi.admin.domain.student.Student;
import com.xinshi.admin.domain.student.StudentRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class MySqlStudentRepository
implements StudentRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT_SQL = "SELECT s.id, s.student_no, s.student_name, s.gender, s.class_id, c.class_name, s.status, s.remark, s.created_at FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id";

    public MySqlStudentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Student> findById(long id) {
        List students = this.jdbcTemplate.query("SELECT s.id, s.student_no, s.student_name, s.gender, s.class_id, c.class_name, s.status, s.remark, s.created_at FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id WHERE s.id = ? AND s.is_deleted = 0", new Object[]{id}, this::mapRow);
        return students.stream().findFirst();
    }

    @Override
    public Optional<Student> findByStudentNo(String studentNo) {
        if (studentNo == null || studentNo.trim().isEmpty()) {
            return Optional.empty();
        }
        List students = this.jdbcTemplate.query("SELECT s.id, s.student_no, s.student_name, s.gender, s.class_id, c.class_name, s.status, s.remark, s.created_at FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id WHERE s.student_no = ?", new Object[]{studentNo.trim()}, this::mapRow);
        return students.stream().findFirst();
    }

    @Override
    public List<Student> findAll(Long classId, String keyword, Integer status, long userId, List<String> roles, int limit, long offset) {
        StringBuilder where = new StringBuilder(" WHERE s.is_deleted = 0");
        ArrayList<Object> args = new ArrayList<Object>();
        this.appendRoleFilter(where, args, classId, userId, roles);
        this.appendCommonFilters(where, args, classId, keyword, status);
        args.add(limit);
        args.add(offset);
        return this.jdbcTemplate.query(SELECT_SQL + where + " ORDER BY s.id DESC LIMIT ? OFFSET ?", args.toArray(), this::mapRow);
    }

    @Override
    public long count(Long classId, String keyword, Integer status, long userId, List<String> roles) {
        StringBuilder where = new StringBuilder(" WHERE s.is_deleted = 0");
        ArrayList<Object> args = new ArrayList<Object>();
        this.appendRoleFilter(where, args, classId, userId, roles);
        this.appendCommonFilters(where, args, classId, keyword, status);
        Long count = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id" + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public boolean existsInClass(long studentId, long classId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_student WHERE id = ? AND class_id = ? AND is_deleted = 0", Integer.class, new Object[]{studentId, classId});
        return count != null && count > 0;
    }

    @Override
    public Student save(Student student) {
        if (student.getId() == 0L) {
            long id = this.insert(student);
            return this.findById(id).orElseThrow(() -> new IllegalStateException("Inserted student not found"));
        }
        this.update(student);
        return student;
    }

    @Override
    public void update(Student student) {
        this.jdbcTemplate.update("UPDATE school_student SET student_no = ?, student_name = ?, gender = ?, class_id = ?, remark = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0", new Object[]{student.getStudentNo(), student.getStudentName(), student.getGender(), student.getClassId(), student.getRemark(), student.getStatus(), student.getId()});
    }

    @Override
    public void deactivate(long id) {
        this.jdbcTemplate.update("UPDATE school_student SET status = 0, is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
    }

    private void appendRoleFilter(StringBuilder where, List<Object> args, Long classId, long userId, List<String> roles) {
        if (roles.contains("HEAD_TEACHER") && !roles.contains("SUPER_ADMIN") && classId == null) {
            where.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = s.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(userId);
        }
        if (roles.contains("PARENT") && !roles.contains("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = s.id AND sp.parent_user_id = ?)");
            args.add(userId);
        }
    }

    private void appendCommonFilters(StringBuilder where, List<Object> args, Long classId, String keyword, Integer status) {
        if (classId != null) {
            where.append(" AND s.class_id = ?");
            args.add(classId);
        }
        if (StringUtils.hasText((String)keyword)) {
            where.append(" AND (s.student_no LIKE ? OR s.student_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            where.append(" AND s.status = ?");
            args.add(status);
        }
    }

    private long insert(Student student) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_student (student_no, student_name, gender, class_id, status, remark, is_deleted) VALUES (?, ?, ?, ?, 1, ?, 0)", 1);
            ps.setString(1, student.getStudentNo());
            ps.setString(2, student.getStudentName());
            ps.setInt(3, student.getGender());
            ps.setLong(4, student.getClassId());
            ps.setString(5, student.getRemark());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert student failed");
        }
        return key.longValue();
    }

    private Student mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Student.rehydrate(rs.getLong("id"), rs.getString("student_no"), rs.getString("student_name"), rs.getInt("gender"), rs.getLong("class_id"), rs.getString("class_name"), rs.getInt("status"), rs.getString("remark"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}

