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
package com.xinshi.admin.infrastructure.persistence.class_;

import com.xinshi.admin.domain.class_.SchoolClass;
import com.xinshi.admin.domain.class_.SchoolClassRepository;
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
public class MySqlSchoolClassRepository
implements SchoolClassRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlSchoolClassRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SchoolClass> findById(long id) {
        List classes = this.jdbcTemplate.query("SELECT c.id, c.class_code, c.class_name, c.grade_session, c.grade_level, c.head_teacher_user_id, u.real_name, c.is_key_class, c.status, c.created_at FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id WHERE c.id = ? AND c.is_deleted = 0", new Object[]{id}, this::mapRow);
        return classes.stream().findFirst();
    }

    @Override
    public Optional<SchoolClass> findByCode(String classCode) {
        if (classCode == null || classCode.trim().isEmpty()) {
            return Optional.empty();
        }
        List classes = this.jdbcTemplate.query("SELECT c.id, c.class_code, c.class_name, c.grade_session, c.grade_level, c.head_teacher_user_id, u.real_name, c.is_key_class, c.status, c.created_at FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id WHERE c.class_code = ? AND c.is_deleted = 0", new Object[]{classCode.trim()}, this::mapRow);
        return classes.stream().findFirst();
    }

    @Override
    public List<SchoolClass> findAll() {
        return this.jdbcTemplate.query("SELECT c.id, c.class_code, c.class_name, c.grade_session, c.grade_level, c.head_teacher_user_id, u.real_name, c.is_key_class, c.status, c.created_at FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id WHERE c.is_deleted = 0 ORDER BY c.grade_level ASC, c.id DESC", this::mapRow);
    }

    @Override
    public List<SchoolClass> findAll(String gradeSession, String mode, long userId, List<String> roles, int limit, long offset) {
        StringBuilder where = this.buildWhereClause(gradeSession, mode, userId, roles);
        List<Object> args = this.buildArgs(gradeSession, userId, limit, offset);
        return this.jdbcTemplate.query("SELECT c.id, c.class_code, c.class_name, c.grade_session, c.grade_level, c.head_teacher_user_id, u.real_name, c.is_key_class, c.status, c.created_at FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id" + where + " ORDER BY c.grade_level ASC, c.id DESC LIMIT ? OFFSET ?", args.toArray(), this::mapRow);
    }

    @Override
    public long count(String gradeSession, String mode, long userId, List<String> roles) {
        StringBuilder where = this.buildWhereClause(gradeSession, mode, userId, roles);
        List<Object> args = this.buildArgs(gradeSession, userId, 0, 0L);
        args = args.subList(0, args.size() - 2);
        String sql = "SELECT COUNT(1) FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id" + where;
        Long count = (Long)this.jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public List<SchoolClass> findByHeadTeacher(long userId) {
        return this.jdbcTemplate.query("SELECT c.id, c.class_code, c.class_name, c.grade_session, c.grade_level, c.head_teacher_user_id, u.real_name, c.is_key_class, c.status, c.created_at FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id WHERE c.head_teacher_user_id = ? AND c.is_deleted = 0", new Object[]{userId}, this::mapRow);
    }

    @Override
    public SchoolClass save(SchoolClass schoolClass) {
        if (schoolClass.getId() == 0L) {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            this.jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO school_class (class_code, class_name, grade_session, grade_level, head_teacher_user_id, is_key_class, status, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, 0)", 1);
                ps.setString(1, schoolClass.getClassCode());
                ps.setString(2, schoolClass.getClassName());
                ps.setString(3, schoolClass.getGradeSession());
                ps.setInt(4, schoolClass.getGradeLevel());
                ps.setLong(5, schoolClass.getHeadTeacherUserId());
                ps.setInt(6, schoolClass.getIsKeyClass());
                ps.setInt(7, schoolClass.getStatus());
                return ps;
            }, (KeyHolder)keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("Insert class failed");
            }
            return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted class not found"));
        }
        this.update(schoolClass);
        return schoolClass;
    }

    @Override
    public void update(SchoolClass schoolClass) {
        this.jdbcTemplate.update("UPDATE school_class SET class_code = ?, class_name = ?, grade_session = ?, grade_level = ?, head_teacher_user_id = ?, is_key_class = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0", new Object[]{schoolClass.getClassCode(), schoolClass.getClassName(), schoolClass.getGradeSession(), schoolClass.getGradeLevel(), schoolClass.getHeadTeacherUserId(), schoolClass.getIsKeyClass(), schoolClass.getStatus(), schoolClass.getId()});
    }

    @Override
    public void deactivate(long id) {
        this.jdbcTemplate.update("UPDATE school_class SET status = 0, is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{id});
    }

    @Override
    public long count() {
        Long count = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class WHERE is_deleted = 0", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public boolean existsById(long id) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class WHERE id = ? AND is_deleted = 0", Integer.class, new Object[]{id});
        return count != null && count > 0;
    }

    private StringBuilder buildWhereClause(String gradeSession, String mode, long userId, List<String> roles) {
        StringBuilder where = new StringBuilder(" WHERE c.is_deleted = 0");
        if (roles.contains("HEAD_TEACHER") && !roles.contains("SUPER_ADMIN") && !roles.contains("TEACHER")) {
            where.append(" AND c.head_teacher_user_id = ?");
        } else if (roles.contains("TEACHER") && !roles.contains("SUPER_ADMIN") && !roles.contains("HEAD_TEACHER")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_class_subject cs WHERE cs.class_id = c.id AND cs.teacher_user_id = ? AND cs.status = 1)");
        } else if (roles.contains("HEAD_TEACHER") && roles.contains("TEACHER") && !roles.contains("SUPER_ADMIN")) {
            if ("teacher".equals(mode)) {
                where.append(" AND EXISTS (SELECT 1 FROM school_class_subject cs WHERE cs.class_id = c.id AND cs.teacher_user_id = ? AND cs.status = 1)");
            } else {
                where.append(" AND c.head_teacher_user_id = ?");
            }
        }
        if (StringUtils.hasText((String)gradeSession)) {
            where.append(" AND c.grade_session = ?");
        }
        return where;
    }

    private List<Object> buildArgs(String gradeSession, long userId, int limit, long offset) {
        ArrayList<Object> args = new ArrayList<Object>();
        if (StringUtils.hasText((String)gradeSession)) {
            args.add(gradeSession);
        }
        args.add(limit);
        args.add(offset);
        return args;
    }

    private SchoolClass mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SchoolClass.rehydrate(rs.getLong("id"), rs.getString("class_code"), rs.getString("class_name"), rs.getString("grade_session"), rs.getInt("grade_level"), rs.getLong("head_teacher_user_id"), rs.getString("real_name"), rs.getInt("is_key_class"), rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}

