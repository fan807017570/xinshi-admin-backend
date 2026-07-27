/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.stereotype.Repository
 */
package com.xinshi.admin.infrastructure.persistence.transcript;

import com.xinshi.admin.domain.transcript.Transcript;
import com.xinshi.admin.domain.transcript.TranscriptRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlTranscriptRepository
implements TranscriptRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT_SQL = "SELECT tr.id, tr.transcript_no, tr.academic_term_id, t.term_name, tr.class_id, c.class_name, tr.student_id, s.student_name, tr.pdf_file_name, tr.pdf_file_path, tr.generated_by, u.real_name, tr.generated_at, tr.status, tr.created_at FROM school_transcript tr LEFT JOIN school_academic_term t ON t.id = tr.academic_term_id LEFT JOIN school_class c ON c.id = tr.class_id LEFT JOIN school_student s ON s.id = tr.student_id LEFT JOIN sys_user u ON u.id = tr.generated_by";

    public MySqlTranscriptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Transcript> findById(long id) {
        List list = this.jdbcTemplate.query("SELECT tr.id, tr.transcript_no, tr.academic_term_id, t.term_name, tr.class_id, c.class_name, tr.student_id, s.student_name, tr.pdf_file_name, tr.pdf_file_path, tr.generated_by, u.real_name, tr.generated_at, tr.status, tr.created_at FROM school_transcript tr LEFT JOIN school_academic_term t ON t.id = tr.academic_term_id LEFT JOIN school_class c ON c.id = tr.class_id LEFT JOIN school_student s ON s.id = tr.student_id LEFT JOIN sys_user u ON u.id = tr.generated_by WHERE tr.id = ?", new Object[]{id}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public Optional<Transcript> findByTermAndStudent(long academicTermId, long studentId) {
        List list = this.jdbcTemplate.query("SELECT tr.id, tr.transcript_no, tr.academic_term_id, t.term_name, tr.class_id, c.class_name, tr.student_id, s.student_name, tr.pdf_file_name, tr.pdf_file_path, tr.generated_by, u.real_name, tr.generated_at, tr.status, tr.created_at FROM school_transcript tr LEFT JOIN school_academic_term t ON t.id = tr.academic_term_id LEFT JOIN school_class c ON c.id = tr.class_id LEFT JOIN school_student s ON s.id = tr.student_id LEFT JOIN sys_user u ON u.id = tr.generated_by WHERE tr.academic_term_id = ? AND tr.student_id = ?", new Object[]{academicTermId, studentId}, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public List<Transcript> findAll(Long academicTermId, Long classId, Long studentId, long userId, List<String> roles, int limit, long offset) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        ArrayList<Number> args = new ArrayList<Number>();
        if (academicTermId != null) {
            where.append(" AND tr.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            where.append(" AND tr.class_id = ?");
            args.add(classId);
        }
        if (studentId != null) {
            where.append(" AND tr.student_id = ?");
            args.add(studentId);
        }
        if (roles.contains("HEAD_TEACHER") && !roles.contains("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = tr.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(userId);
        } else if (roles.contains("PARENT") && !roles.contains("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = tr.student_id AND sp.parent_user_id = ?)");
            args.add(userId);
        }
        args.add(limit);
        args.add(offset);
        return this.jdbcTemplate.query(SELECT_SQL + where + " ORDER BY tr.id DESC LIMIT ? OFFSET ?", args.toArray(), this::mapRow);
    }

    @Override
    public long count(Long academicTermId, Long classId, Long studentId, long userId, List<String> roles) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        ArrayList<Long> args = new ArrayList<Long>();
        if (academicTermId != null) {
            where.append(" AND tr.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            where.append(" AND tr.class_id = ?");
            args.add(classId);
        }
        if (studentId != null) {
            where.append(" AND tr.student_id = ?");
            args.add(studentId);
        }
        if (roles.contains("HEAD_TEACHER") && !roles.contains("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = tr.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(userId);
        } else if (roles.contains("PARENT") && !roles.contains("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = tr.student_id AND sp.parent_user_id = ?)");
            args.add(userId);
        }
        Long count = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_transcript tr LEFT JOIN school_class c ON c.id = tr.class_id LEFT JOIN school_student s ON s.id = tr.student_id" + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public Transcript save(Transcript transcript) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO school_transcript (transcript_no, academic_term_id, class_id, student_id, pdf_file_name, pdf_file_path, generated_by, generated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", 1);
            ps.setString(1, transcript.getTranscriptNo());
            ps.setLong(2, transcript.getAcademicTermId());
            ps.setLong(3, transcript.getClassId());
            ps.setLong(4, transcript.getStudentId());
            ps.setString(5, transcript.getPdfFileName());
            ps.setString(6, transcript.getPdfFilePath());
            ps.setLong(7, transcript.getGeneratedBy());
            ps.setTimestamp(8, transcript.getGeneratedAt() != null ? Timestamp.valueOf(transcript.getGeneratedAt()) : null);
            ps.setInt(9, transcript.getStatus());
            return ps;
        }, (KeyHolder)keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert transcript failed");
        }
        return this.findById(key.longValue()).orElseThrow(() -> new IllegalStateException("Inserted transcript not found"));
    }

    @Override
    public void update(Transcript transcript) {
        this.jdbcTemplate.update("UPDATE school_transcript SET class_id = ?, pdf_file_name = ?, pdf_file_path = ?, generated_by = ?, generated_at = ?, status = 1, updated_at = CURRENT_TIMESTAMP WHERE academic_term_id = ? AND student_id = ?", new Object[]{transcript.getClassId(), transcript.getPdfFileName(), transcript.getPdfFilePath(), transcript.getGeneratedBy(), transcript.getGeneratedAt() != null ? Timestamp.valueOf(transcript.getGeneratedAt()) : null, transcript.getAcademicTermId(), transcript.getStudentId()});
    }

    private Transcript mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp generatedAt = rs.getTimestamp("generated_at");
        return Transcript.rehydrate(rs.getLong("id"), rs.getString("transcript_no"), rs.getLong("academic_term_id"), rs.getString("term_name"), rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("student_id"), rs.getString("student_name"), rs.getString("pdf_file_name"), rs.getString("pdf_file_path"), rs.getLong("generated_by"), rs.getString("real_name"), generatedAt != null ? generatedAt.toLocalDateTime() : null, rs.getInt("status"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}

