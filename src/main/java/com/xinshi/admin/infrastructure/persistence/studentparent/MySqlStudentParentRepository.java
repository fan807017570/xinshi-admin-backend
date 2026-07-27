/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.studentparent;

import com.xinshi.admin.domain.studentparent.StudentParent;
import com.xinshi.admin.domain.studentparent.StudentParentRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlStudentParentRepository
implements StudentParentRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlStudentParentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentParent> findByStudent(long studentId) {
        return this.jdbcTemplate.query("SELECT parent_user_id, relation_type, is_primary FROM school_student_parent WHERE student_id = ?", new Object[]{studentId}, (rs, rowNum) -> StudentParent.rehydrate(rs.getLong("parent_user_id"), rs.getString("relation_type"), rs.getInt("is_primary")));
    }

    @Override
    public void saveBatch(long studentId, List<StudentParent> parents) {
        this.jdbcTemplate.update("DELETE FROM school_student_parent WHERE student_id = ?", new Object[]{studentId});
        for (StudentParent parent : parents) {
            this.jdbcTemplate.update("INSERT INTO school_student_parent (student_id, parent_user_id, relation_type, is_primary) VALUES (?, ?, ?, ?)", new Object[]{studentId, parent.getParentUserId(), parent.getRelationType(), parent.getIsPrimary()});
        }
    }

    @Override
    public boolean isParentOf(long parentUserId, long studentId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_student_parent WHERE student_id = ? AND parent_user_id = ?", Integer.class, new Object[]{studentId, parentUserId});
        return count != null && count > 0;
    }
}

