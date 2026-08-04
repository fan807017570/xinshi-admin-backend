/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.fontbox.ttf.TrueTypeCollection
 *  org.apache.fontbox.ttf.TrueTypeFont
 *  org.apache.pdfbox.pdmodel.PDDocument
 *  org.apache.pdfbox.pdmodel.PDPage
 *  org.apache.pdfbox.pdmodel.PDPageContentStream
 *  org.apache.pdfbox.pdmodel.PDPageContentStream$AppendMode
 *  org.apache.pdfbox.pdmodel.common.PDRectangle
 *  org.apache.pdfbox.pdmodel.font.PDFont
 *  org.apache.pdfbox.pdmodel.font.PDType0Font
 *  org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
 *  org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
 *  org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.transcript;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.domain.achievement.AchievementRepository;
import com.xinshi.admin.domain.achievement.StudentAchievement;
import com.xinshi.admin.domain.honortype.HonorTypeRepository;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TranscriptService
extends SchoolBaseService {
    private static final Logger log = LoggerFactory.getLogger(TranscriptService.class);
    private final AccessControlService accessControlService;
    private final AchievementRepository achievementRepository;
    private final HonorTypeRepository honorTypeRepository;
    @Value(value="${xinshi.transcript.output-dir}")
    private String transcriptOutputDir;
    @Value(value="${xinshi.school.name-zh:新实中学}")
    private String schoolNameZh;
    @Value(value="${xinshi.school.name-en:}")
    private String schoolNameEn;
    @Value(value="${xinshi.school.address-zh:}")
    private String schoolAddressZh;
    @Value(value="${xinshi.school.address-en:}")
    private String schoolAddressEn;
    @Value(value="${xinshi.school.phone:}")
    private String schoolPhone;
    @Value(value="${xinshi.school.post-code:}")
    private String schoolPostCode;

    public TranscriptService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService, AchievementRepository achievementRepository, HonorTypeRepository honorTypeRepository) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
        this.achievementRepository = achievementRepository;
        this.honorTypeRepository = honorTypeRepository;
    }

    public PageResult<Map<String, Object>> listTranscripts(Long academicTermId, Long classId, Long studentId, PageRequest pageRequest) {
        this.accessControlService.ensureReadableTranscriptScope(academicTermId, classId, studentId);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        ArrayList<Number> args = new ArrayList<Number>();
        Long currentUserId = this.accessControlService.currentUserId();
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
        if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = tr.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(currentUserId);
        } else if (this.accessControlService.hasRole("PARENT") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            where.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = tr.student_id AND sp.parent_user_id = ?)");
            args.add(currentUserId);
        }
        String fromClause = "FROM school_transcript tr LEFT JOIN school_academic_term t ON t.id = tr.academic_term_id LEFT JOIN school_class c ON c.id = tr.class_id LEFT JOIN school_student s ON s.id = tr.student_id LEFT JOIN sys_user u ON u.id = tr.generated_by";
        String countSql = "SELECT COUNT(1) " + fromClause + where;
        long total = (Long)this.jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        StringBuilder dataSql = new StringBuilder("SELECT tr.id, tr.transcript_no AS transcriptNo, tr.academic_term_id AS academicTermId, t.term_name AS termName, tr.class_id AS classId, c.class_name AS className, tr.student_id AS studentId, s.student_name AS studentName, tr.pdf_file_name AS pdfFileName, tr.pdf_file_path AS pdfFilePath, tr.generated_by AS generatedBy, u.real_name AS generatedByName, tr.generated_at AS generatedAt, tr.status, tr.created_at AS createdAt ");
        dataSql.append(fromClause);
        dataSql.append((CharSequence)where);
        dataSql.append(" ORDER BY tr.id DESC LIMIT ? OFFSET ?");
        args.add(pageRequest.limit());
        args.add(pageRequest.offset());
        List items = this.jdbcTemplate.queryForList(dataSql.toString(), args.toArray());
        return new PageResult<Map<String, Object>>(items, total, pageRequest.page(), pageRequest.size());
    }

    public Map<String, Object> generateTranscript(Map<String, Object> request) {
        Map<String, Object> existing;
        if (!StringUtils.hasText(this.transcriptOutputDir)) {
            throw new IllegalStateException("成绩单输出目录未配置，请联系管理员");
        }
        this.accessControlService.ensureCanGenerateTranscript();
        long academicTermId = this.requiredLong(request, "academicTermId");
        long classId = this.requiredLong(request, "classId");
        long studentId = this.requiredLong(request, "studentId");
        Long examTypeId = this.optionalLong(request, "examTypeId");
        Long generatedByUserId = this.optionalLong(request, "generatedByUserId");
        this.accessControlService.ensureCanAccessClass(classId);
        this.accessControlService.ensureCanAccessStudent(studentId);
        this.accessControlService.ensureStudentBelongsToClass(studentId, classId);
        Map<String, Object> student = this.getStudent(studentId);
        if (student.isEmpty()) {
            throw new IllegalArgumentException("学生不存在");
        }
        Map<String, Object> clazz = this.getClass(classId);
        if (clazz.isEmpty()) {
            throw new IllegalArgumentException("班级不存在");
        }
        Map<String, Object> term = this.getAcademicTerm(academicTermId);
        if (term.isEmpty()) {
            throw new IllegalArgumentException("学期不存在");
        }
        List<Map<String, Object>> results = this.listStudentResults(academicTermId, classId, studentId, null, examTypeId);
        this.ensureTranscriptResultsComplete(academicTermId, classId, results);
        List<Map<String, Object>> comments = this.listOverallComments(academicTermId, classId, studentId);
        Map<String, Object> comment = comments.isEmpty() ? Collections.emptyMap() : comments.get(0);
        String transcriptNo = "TR-" + academicTermId + "-" + studentId;
        if (examTypeId != null) {
            transcriptNo = transcriptNo + "-" + examTypeId;
        }
        if (!(existing = this.transcriptByTermAndStudent(academicTermId, studentId)).isEmpty()) {
            transcriptNo = Objects.toString(existing.get("transcriptNo"), transcriptNo);
        }
        Path outputDir = Paths.get(this.transcriptOutputDir, new String[0]);
        try {
            Files.createDirectories(outputDir, new FileAttribute[0]);
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot create transcript directory: " + outputDir, e);
        }
        String pdfFileName = this.buildTranscriptFileName(student, term);
        Path pdfPath = outputDir.resolve(pdfFileName);
        String examTypeName = null;
        if (examTypeId != null) {
            Map<String, Object> examType = this.first(this.jdbcTemplate.queryForList("SELECT exam_type_name AS examTypeName FROM school_exam_type WHERE id = ?", new Object[]{examTypeId}));
            examTypeName = Objects.toString(examType.get("examTypeName"), null);
        }
        this.writeTranscriptPdf(pdfPath, transcriptNo, student, clazz, term, results, comment, examTypeName);
        if (existing.isEmpty()) {
            this.insert("school_transcript", "INSERT INTO school_transcript (transcript_no, academic_term_id, class_id, student_id, pdf_file_name, pdf_file_path, generated_by, generated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", transcriptNo, academicTermId, classId, studentId, pdfFileName, pdfPath.toAbsolutePath().toString(), generatedByUserId, Timestamp.valueOf(LocalDateTime.now()), 1);
        } else {
            this.jdbcTemplate.update("UPDATE school_transcript SET class_id = ?, pdf_file_name = ?, pdf_file_path = ?, generated_by = ?, generated_at = ?, status = 1, updated_at = CURRENT_TIMESTAMP WHERE academic_term_id = ? AND student_id = ?", new Object[]{classId, pdfFileName, pdfPath.toAbsolutePath().toString(), generatedByUserId, Timestamp.valueOf(LocalDateTime.now()), academicTermId, studentId});
        }
        return this.transcriptByTermAndStudent(academicTermId, studentId);
    }

    public Map<String, Object> regenerateTranscript(long transcriptId) {
        if (!StringUtils.hasText(this.transcriptOutputDir)) {
            throw new IllegalStateException("成绩单输出目录未配置，请联系管理员");
        }
        this.accessControlService.ensureCanGenerateTranscript();
        Map<String, Object> transcript = this.getTranscript(transcriptId);
        if (transcript.isEmpty()) {
            throw new IllegalArgumentException("成绩单不存在");
        }
        long academicTermId = this.requiredLong(transcript, "academicTermId");
        long classId = this.requiredLong(transcript, "classId");
        long studentId = this.requiredLong(transcript, "studentId");
        Long generatedByUserId = this.accessControlService.currentUserId();
        Map<String, Object> student = this.getStudent(studentId);
        Map<String, Object> clazz = this.getClass(classId);
        Map<String, Object> term = this.getAcademicTerm(academicTermId);
        Long examTypeId = this.optionalLong(transcript, "examTypeId");
        List<Map<String, Object>> results = this.listStudentResults(academicTermId, classId, studentId, null, examTypeId);
        this.ensureTranscriptResultsComplete(academicTermId, classId, results);
        List<Map<String, Object>> comments = this.listOverallComments(academicTermId, classId, studentId);
        Map<String, Object> comment = comments.isEmpty() ? Collections.emptyMap() : comments.get(0);
        String transcriptNo = Objects.toString(transcript.get("transcriptNo"), "TR-" + academicTermId + "-" + studentId);
        Path outputDir = Paths.get(this.transcriptOutputDir, new String[0]);
        try {
            Files.createDirectories(outputDir, new FileAttribute[0]);
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot create transcript directory: " + outputDir, e);
        }
        String pdfFileName = this.buildTranscriptFileName(student, term);
        Path pdfPath = outputDir.resolve(pdfFileName);
        String examTypeName = null;
        if (examTypeId != null) {
            Map<String, Object> examType = this.first(this.jdbcTemplate.queryForList("SELECT exam_type_name AS examTypeName FROM school_exam_type WHERE id = ?", new Object[]{examTypeId}));
            examTypeName = Objects.toString(examType.get("examTypeName"), null);
        }
        this.writeTranscriptPdf(pdfPath, transcriptNo, student, clazz, term, results, comment, examTypeName);
        this.jdbcTemplate.update("UPDATE school_transcript SET pdf_file_name = ?, pdf_file_path = ?, generated_by = ?, generated_at = ?, status = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", new Object[]{pdfFileName, pdfPath.toAbsolutePath().toString(), generatedByUserId, Timestamp.valueOf(LocalDateTime.now()), transcriptId});
        return this.getTranscript(transcriptId);
    }

    public Map<String, Object> getTranscript(long id) {
        Map<String, Object> transcript = this.first(this.jdbcTemplate.queryForList("SELECT tr.id, tr.transcript_no AS transcriptNo, tr.academic_term_id AS academicTermId, t.term_name AS termName, tr.class_id AS classId, c.class_name AS className, tr.student_id AS studentId, s.student_name AS studentName, tr.pdf_file_name AS pdfFileName, tr.pdf_file_path AS pdfFilePath, tr.generated_by AS generatedBy, u.real_name AS generatedByName, tr.generated_at AS generatedAt, tr.status, tr.created_at AS createdAt FROM school_transcript tr LEFT JOIN school_academic_term t ON t.id = tr.academic_term_id LEFT JOIN school_class c ON c.id = tr.class_id LEFT JOIN school_student s ON s.id = tr.student_id LEFT JOIN sys_user u ON u.id = tr.generated_by WHERE tr.id = ?", new Object[]{id}));
        this.accessControlService.ensureReadableTranscriptRow(transcript);
        return transcript;
    }

    public Map<String, Object> transcriptByTermAndStudent(long academicTermId, long studentId) {
        Map<String, Object> transcript = this.first(this.jdbcTemplate.queryForList("SELECT tr.id, tr.transcript_no AS transcriptNo, tr.academic_term_id AS academicTermId, t.term_name AS termName, tr.class_id AS classId, c.class_name AS className, tr.student_id AS studentId, s.student_name AS studentName, tr.pdf_file_name AS pdfFileName, tr.pdf_file_path AS pdfFilePath, tr.generated_by AS generatedBy, u.real_name AS generatedByName, tr.generated_at AS generatedAt, tr.status, tr.created_at AS createdAt FROM school_transcript tr LEFT JOIN school_academic_term t ON t.id = tr.academic_term_id LEFT JOIN school_class c ON c.id = tr.class_id LEFT JOIN school_student s ON s.id = tr.student_id LEFT JOIN sys_user u ON u.id = tr.generated_by WHERE tr.academic_term_id = ? AND tr.student_id = ?", new Object[]{academicTermId, studentId}));
        this.accessControlService.ensureReadableTranscriptRow(transcript);
        return transcript;
    }

    public Path transcriptFilePath(long transcriptId) {
        Map<String, Object> transcript = this.getTranscript(transcriptId);
        if (transcript.isEmpty()) {
            throw new IllegalArgumentException("成绩单不存在");
        }
        String filePath = Objects.toString(transcript.get("pdfFilePath"), "");
        if (!StringUtils.hasText((String)filePath)) {
            throw new IllegalStateException("成绩单文件不存在");
        }
        return Paths.get(filePath, new String[0]);
    }

    private String buildTranscriptFileName(Map<String, Object> student, Map<String, Object> term) {
        String studentName = this.sanitizeFileNamePart(Objects.toString(student.get("studentName"), "学生"));
        String termName = this.sanitizeFileNamePart(Objects.toString(term.get("termName"), "学期"));
        return studentName + "-" + termName + ".pdf";
    }

    private String sanitizeFileNamePart(String value) {
        String cleaned = this.safeText(value).replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return StringUtils.hasText((String)cleaned) ? cleaned : "未命名";
    }

    private void ensureTranscriptResultsComplete(long academicTermId, long classId, List<Map<String, Object>> results) {
        List<Map<String, Object>> classSubjects = this.listClassSubjects(academicTermId, classId);
        if (classSubjects.isEmpty()) {
            throw new IllegalArgumentException("当前班级未配置课程，不能生成成绩单");
        }
        // 按 subjectId（科目逻辑ID）比较，而非 classSubjectId（关联表主键），
        // 以兼容学生换班后旧成绩的 classSubjectId 与新班级不匹配的场景
        Set<Long> resultSubjectIds = results.stream()
            .map(result -> this.optionalLong((Map<String, Object>) result, "subjectId"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List missingSubjectNames = classSubjects.stream()
            .filter(subject -> {
                Long subjectId = this.optionalLong((Map<String, Object>) subject, "subjectId");
                return subjectId != null && !resultSubjectIds.contains(subjectId);
            })
            .map(subject -> Objects.toString(subject.get("subjectName"), "未知课程"))
            .collect(Collectors.toList());
        if (!missingSubjectNames.isEmpty()) {
            throw new IllegalArgumentException("以下课程尚未录入成绩，不能生成成绩单：" + String.join((CharSequence) "、", missingSubjectNames));
        }
    }

    private Map<String, Object> getStudent(long id) {
        this.accessControlService.ensureCanAccessStudent(id);
        return this.first(this.jdbcTemplate.queryForList("SELECT s.id, s.student_no AS studentNo, s.student_name AS studentName, s.gender, s.class_id AS classId, c.class_name AS className, s.status, s.remark, s.created_at AS createdAt FROM school_student s LEFT JOIN school_class c ON c.id = s.class_id WHERE s.id = ? AND s.is_deleted = 0", new Object[]{id}));
    }

    private Map<String, Object> getClass(long id) {
        this.accessControlService.ensureCanAccessClass(id);
        return this.first(this.jdbcTemplate.queryForList("SELECT c.id, c.class_code AS classCode, c.class_name AS className, c.grade_session AS gradeSession, c.grade_level AS gradeLevel, eg.grade_name AS gradeName, c.head_teacher_user_id AS headTeacherUserId, u.real_name AS headTeacherName, c.is_key_class AS isKeyClass, c.status, c.created_at AS createdAt FROM school_class c LEFT JOIN sys_user u ON u.id = c.head_teacher_user_id LEFT JOIN school_enroll_grade eg ON eg.grade_level = c.grade_level WHERE c.id = ? AND c.is_deleted = 0", new Object[]{id}));
    }

    private Map<String, Object> getAcademicTerm(long id) {
        return this.first(this.jdbcTemplate.queryForList("SELECT id, term_code AS termCode, academic_year AS academicYear, term_name AS termName, start_date AS startDate, end_date AS endDate, status, created_at AS createdAt FROM school_academic_term WHERE id = ?", new Object[]{id}));
    }

    private List<Map<String, Object>> listStudentResults(Long academicTermId, Long classId, Long studentId, Long classSubjectId, Long examTypeId) {
        this.accessControlService.ensureReadableResultScope(academicTermId, classId, studentId, classSubjectId);
        StringBuilder sql = new StringBuilder("SELECT r.id, r.academic_term_id AS academicTermId, t.term_name AS termName, r.class_subject_id AS classSubjectId, cs.class_id AS classId, c.class_name AS className, r.student_id AS studentId, s.student_name AS studentName, cs.subject_id AS subjectId, su.subject_name AS subjectName, r.score, r.performance_comment AS performanceComment, su.min_score AS minScore, su.max_score AS maxScore, r.strengths, r.improvement_points AS improvementPoints, cs.teacher_user_id AS teacherUserId, tu.real_name AS teacherName, r.evaluator_user_id AS evaluatorUserId, u.real_name AS evaluatorName, r.evaluated_at AS evaluatedAt, r.status, r.created_at AS createdAt FROM school_student_course_result r LEFT JOIN school_academic_term t ON t.id = r.academic_term_id LEFT JOIN school_class_subject cs ON cs.id = r.class_subject_id LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_student s ON s.id = r.student_id LEFT JOIN school_subject su ON su.id = cs.subject_id LEFT JOIN sys_user tu ON tu.id = cs.teacher_user_id LEFT JOIN sys_user u ON u.id = r.evaluator_user_id WHERE 1 = 1");
        ArrayList<Long> args = new ArrayList<Long>();
        Long currentUserId = this.accessControlService.currentUserId();
        if (academicTermId != null) {
            sql.append(" AND r.academic_term_id = ?");
            args.add(academicTermId);
        }
        // 不再按 classId 过滤成绩：学生换班后，旧成绩的 class_subject 属于原班级，
        // 但成绩单生成需要汇总该学生所有成绩，并按科目检测缺失情况
        if (studentId != null) {
            sql.append(" AND r.student_id = ?");
            args.add(studentId);
        }
        if (classSubjectId != null) {
            sql.append(" AND r.class_subject_id = ?");
            args.add(classSubjectId);
        }
        if (examTypeId != null) {
            sql.append(" AND (r.exam_type_id = ? OR (r.exam_type_id IS NULL AND ? IS NULL))");
            args.add(examTypeId);
            args.add(examTypeId);
        }
        if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = cs.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(currentUserId);
        } else if (this.accessControlService.hasRole("TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND cs.teacher_user_id = ?");
            args.add(currentUserId);
        } else if (this.accessControlService.hasRole("PARENT") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = r.student_id AND sp.parent_user_id = ?)");
            args.add(currentUserId);
        }
        sql.append(" ORDER BY r.id DESC");
        return this.jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> listOverallComments(Long academicTermId, Long classId, Long studentId) {
        this.accessControlService.ensureReadableCommentScope(academicTermId, classId, studentId);
        StringBuilder sql = new StringBuilder("SELECT o.id, o.academic_term_id AS academicTermId, t.term_name AS termName, o.class_id AS classId, c.class_name AS className, o.student_id AS studentId, s.student_name AS studentName, o.overall_comment AS overallComment, o.strengths, o.improvement_points AS improvementPoints, o.evaluator_user_id AS evaluatorUserId, u.real_name AS evaluatorName, o.evaluated_at AS evaluatedAt, o.status, o.created_at AS createdAt FROM school_student_overall_comment o LEFT JOIN school_academic_term t ON t.id = o.academic_term_id LEFT JOIN school_class c ON c.id = o.class_id LEFT JOIN school_student s ON s.id = o.student_id LEFT JOIN sys_user u ON u.id = o.evaluator_user_id WHERE 1 = 1");
        ArrayList<Long> args = new ArrayList<Long>();
        Long currentUserId = this.accessControlService.currentUserId();
        if (academicTermId != null) {
            sql.append(" AND o.academic_term_id = ?");
            args.add(academicTermId);
        }
        if (classId != null) {
            sql.append(" AND o.class_id = ?");
            args.add(classId);
        }
        if (studentId != null) {
            sql.append(" AND o.student_id = ?");
            args.add(studentId);
        }
        if (this.accessControlService.hasRole("HEAD_TEACHER") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_class x WHERE x.id = o.class_id AND x.is_deleted = 0 AND x.head_teacher_user_id = ?)");
            args.add(currentUserId);
        } else if (this.accessControlService.hasRole("PARENT") && !this.accessControlService.hasRole("SUPER_ADMIN")) {
            sql.append(" AND EXISTS (SELECT 1 FROM school_student_parent sp WHERE sp.student_id = o.student_id AND sp.parent_user_id = ?)");
            args.add(currentUserId);
        }
        sql.append(" ORDER BY o.id DESC");
        return this.jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> listClassSubjects(long academicTermId, long classId) {
        this.accessControlService.ensureCanAccessClass(classId);
        this.ensureClassSubjects(academicTermId, classId);
        return this.jdbcTemplate.queryForList("SELECT cs.id, cs.academic_term_id AS academicTermId, cs.class_id AS classId, c.class_name AS className, cs.subject_id AS subjectId, s.subject_name AS subjectName, cs.source_grade_subject_id AS sourceGradeSubjectId, s.min_score AS minScore, s.max_score AS maxScore, cs.teacher_user_id AS teacherUserId, u.real_name AS teacherName, cs.status, cs.created_at AS createdAt FROM school_class_subject cs LEFT JOIN school_class c ON c.id = cs.class_id LEFT JOIN school_subject s ON s.id = cs.subject_id LEFT JOIN sys_user u ON u.id = cs.teacher_user_id WHERE cs.academic_term_id = ? AND cs.class_id = ? ORDER BY cs.id", new Object[]{academicTermId, classId});
    }

    private void ensureClassSubjects(long academicTermId, long classId) {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_class_subject WHERE academic_term_id = ? AND class_id = ?", Integer.class, new Object[]{academicTermId, classId});
        if (count != null && count > 0) {
            return;
        }
        List classes = this.jdbcTemplate.queryForList("SELECT grade_level AS gradeLevel FROM school_class WHERE id = ? AND is_deleted = 0", new Object[]{classId});
        if (classes.isEmpty()) {
            return;
        }
        Integer gradeLevel = this.optionalInteger((Map)classes.get(0), "gradeLevel", null);
        if (gradeLevel == null) {
            return;
        }
        List<Map<String, Object>> gradeSubjects = this.jdbcTemplate.queryForList("SELECT gs.id AS gradeSubjectId, gs.subject_id AS subjectId, gs.status AS status FROM school_grade_subject gs WHERE gs.academic_term_id = ? AND gs.grade_level = ? AND gs.status = 1 ORDER BY gs.sort_order, gs.id", new Object[]{academicTermId, gradeLevel});
        for (Map<String, Object> gradeSubject : gradeSubjects) {
            this.insert("school_class_subject", "INSERT INTO school_class_subject (academic_term_id, class_id, subject_id, source_grade_subject_id, status) VALUES (?, ?, ?, ?, ?)", academicTermId, classId, this.requiredLong(gradeSubject, "subjectId"), this.requiredLong(gradeSubject, "gradeSubjectId"), this.optionalInteger(gradeSubject, "status", 1));
        }
    }

    private void writeTranscriptPdf(Path path, String transcriptNo, Map<String, Object> student, Map<String, Object> clazz, Map<String, Object> term, List<Map<String, Object>> results, Map<String, Object> comment, String examTypeName) {
        try (PDDocument document = new PDDocument();){
            PDFont serifRegular = this.loadSingleFont(document, new String[]{"fonts/NotoSerifCJKsc-Regular.otf", "fonts/NotoSansSC-Regular.ttf"});
            PDFont serifBold = this.loadSingleFont(document, new String[]{"fonts/NotoSerifCJKsc-Bold.otf", "fonts/NotoSansSC-Regular.ttf"});
            PDFont headerFont = this.loadHeaderFont(document, serifBold);
            PDImageXObject logoImage = this.loadLogoImage(document);
            PDImageXObject watermarkImage = this.loadWatermarkImage(document);
            long academicTermId = this.requiredLong(term, "id");
            long studentId = this.requiredLong(student, "id");
            List<StudentAchievement> achievements = this.achievementRepository.findByTermAndStudent(academicTermId, studentId);
            HashMap<Long, String> honorTypeNames = new HashMap<Long, String>();
            for (StudentAchievement a : achievements) {
                if (a.getHonorTypeId() == null || honorTypeNames.containsKey(a.getHonorTypeId())) continue;
                this.honorTypeRepository.findById(a.getHonorTypeId()).ifPresent(ht -> honorTypeNames.put(ht.getId(), ht.getHonorTypeName()));
            }
            String advisorName = Objects.toString(clazz.get("headTeacherName"), "未配置");
            String studentName = Objects.toString(student.get("studentName"), "");
            ArrayList<GradeResult> grades = new ArrayList<GradeResult>();
            for (Map<String, Object> result : results) {
                double score = TranscriptService.parseScore(Objects.toString(result.get("score"), ""));
                double maxScore = TranscriptService.parseScore(Objects.toString(result.get("maxScore"), "100"));
                grades.add(GradeCalculator.calculate(score, maxScore));
            }
            try (TranscriptPdfWriter writer = new TranscriptPdfWriter(document, serifRegular, serifBold, headerFont, logoImage, watermarkImage, this.schoolNameZh, this.schoolNameEn, this.schoolAddressZh, this.schoolAddressEn, this.schoolPhone, this.schoolPostCode, achievements, honorTypeNames, examTypeName);) {
                writer.writeReportTitle(examTypeName);
                writer.writeStudentInfoTable(studentName, Objects.toString(student.get("studentNo"), ""), Objects.toString(clazz.get("gradeName"), Objects.toString(clazz.get("gradeLevel"), "")), Objects.toString(clazz.get("className"), ""), Objects.toString(term.get("academicYear"), ""), Objects.toString(term.get("termName"), ""));
                writer.writeScoreTable(results, grades);
                writer.writeAchievements();
                // 各科教师评语：学生姓名和班主任只在第一个 block 中显示
                boolean isFirst = true;
                for (Map<String, Object> result : results) {
                    String teacherName = Objects.toString(result.get("teacherName"), Objects.toString(result.get("evaluatorName"), "未配置"));
                    String subjectName = Objects.toString(result.get("subjectName"), "");
                    String perfComment = Objects.toString(result.get("performanceComment"), "");
                    String strengths = Objects.toString(result.get("strengths"), "");
                    String improvements = Objects.toString(result.get("improvementPoints"), "");
                    if (isFirst) {
                        writer.writeCommentBlock(studentName, advisorName, subjectName, teacherName, perfComment, strengths, improvements);
                        isFirst = false;
                    } else {
                        writer.writeSubjectCommentBlock(subjectName, teacherName, perfComment, strengths, improvements);
                    }
                }
                writer.writeOverallComment(studentName, advisorName, Objects.toString(comment.get("overallComment"), "无"), Objects.toString(comment.get("strengths"), ""), Objects.toString(comment.get("improvementPoints"), ""));
                writer.writeClosingNote();
            }
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            try (OutputStream outputStream = Files.newOutputStream(path, new OpenOption[0]);){
                document.save(outputStream);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("生成成绩单失败", e);
        }
    }

    private static double parseScore(String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        }
        catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String extractSemester(Map<String, Object> term) {
        String[] parts;
        String last;
        String termCode = Objects.toString(term.get("termCode"), "");
        if (termCode.contains("-") && (last = (parts = termCode.split("-"))[parts.length - 1]).matches("\\d+")) {
            return last;
        }
        String termName = Objects.toString(term.get("termName"), "");
        if (termName.contains("一") || termName.contains("1")) {
            return "1";
        }
        if (termName.contains("二") || termName.contains("2")) {
            return "2";
        }
        if (termName.contains("三") || termName.contains("3")) {
            return "3";
        }
        return "";
    }

    private PDImageXObject loadWatermarkImage(PDDocument document) {
        try {
            String[] watermarkPaths;
            for (String watermarkPath : watermarkPaths = new String[]{"resources/picture/watermark.png", "xinshi-admin-backend/resources/picture/watermark.png", "../resources/picture/watermark.png", "../xinshi-admin-backend/resources/picture/watermark.png"}) {
                Path path = Paths.get(watermarkPath, new String[0]);
                if (!Files.exists(path, new LinkOption[0])) continue;
                return this.loadOptimizedImage(document, path, 768);
            }
        }
        catch (IOException | RuntimeException exception) {
            // empty catch block
        }
        return null;
    }

    private PDImageXObject loadLogoImage(PDDocument document) {
        try {
            String[] logoPaths;
            for (String logoPath : logoPaths = new String[]{"resources/picture/xinshi_logo.png", "xinshi-admin-backend/resources/picture/xinshi_logo.png"}) {
                Path path = Paths.get(logoPath, new String[0]);
                if (!Files.exists(path, new LinkOption[0])) continue;
                return this.loadOptimizedImage(document, path, 128);
            }
        }
        catch (IOException | RuntimeException exception) {
            // empty catch block
        }
        return null;
    }

    private PDImageXObject loadOptimizedImage(PDDocument document, Path path, int maxDimension) throws IOException {
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) {
            return PDImageXObject.createFromFile((String)path.toAbsolutePath().toString(), (PDDocument)document);
        }
        BufferedImage optimized = this.resizeImage(source, maxDimension);
        return LosslessFactory.createFromImage((PDDocument)document, (BufferedImage)optimized);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private BufferedImage resizeImage(BufferedImage source, int maxDimension) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int longestSide = Math.max(sourceWidth, sourceHeight);
        boolean hasAlpha = source.getColorModel().hasAlpha();
        if (longestSide <= maxDimension) {
            return this.convertImageType(source, hasAlpha);
        }
        double scale = (double)maxDimension / (double)longestSide;
        int targetWidth = Math.max(1, (int)Math.round((double)sourceWidth * scale));
        int targetHeight = Math.max(1, (int)Math.round((double)sourceHeight * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, hasAlpha ? 2 : 1);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        }
        finally {
            graphics.dispose();
        }
        return resized;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private BufferedImage convertImageType(BufferedImage source, boolean hasAlpha) {
        int targetType;
        int n = targetType = hasAlpha ? 2 : 1;
        if (source.getType() == targetType) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), targetType);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage((Image)source, 0, 0, null);
        }
        finally {
            graphics.dispose();
        }
        return converted;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private PDFont loadSingleFont(PDDocument document, String[] classpathResources) throws IOException {
        String[] fontCandidates;
        for (String resourcePath : classpathResources) {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                log.debug("Classpath font not found: {}", (Object)resourcePath);
                continue;
            }
            try {
                PDType0Font font = PDType0Font.load((PDDocument)document, (InputStream)is);
                log.info("PDF font loaded from classpath: {}", (Object)resourcePath);
                PDType0Font pDType0Font = font;
                return pDType0Font;
            }
            catch (IOException | RuntimeException e) {
                log.warn("Failed to load classpath font {}: {}", (Object)resourcePath, (Object)e.getMessage());
            }
            finally {
                try {
                    is.close();
                }
                catch (IOException e) {}
            }
        }
        log.info("No classpath font available, falling back to system fonts");
        for (String candidate : fontCandidates = new String[]{"/System/Library/Fonts/Hiragino Sans GB.ttc", "/System/Library/Fonts/STHeiti Medium.ttc", "/System/Library/Fonts/PingFang.ttc", "/Library/Fonts/Arial Unicode.ttf", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc", "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf", "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf", "/usr/share/fonts/truetype/arphic/uming.ttc", "/usr/share/fonts/truetype/arphic/ukai.ttc", "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc", "/usr/local/share/fonts/NotoSansSC-Regular.ttf", "C:\\Windows\\Fonts\\simhei.ttf", "C:\\Windows\\Fonts\\simsun.ttc", "C:\\Windows\\Fonts\\msyh.ttc", "C:\\Windows\\Fonts\\msyhbd.ttc"}) {
            Path fontPath = Paths.get(candidate, new String[0]);
            if (!Files.exists(fontPath, new LinkOption[0])) continue;
            try {
                PDType0Font font;
                if (candidate.toLowerCase(Locale.ROOT).endsWith(".ttc")) {
                    font = (PDType0Font) this.loadFontFromCollection(document, fontPath.toFile());
                    if (font == null) continue;
                    log.info("PDF font loaded from system TTC: {}", (Object)candidate);
                    return font;
                }
                font = PDType0Font.load((PDDocument)document, (File)fontPath.toFile());
                log.info("PDF font loaded from system TTF: {}", (Object)candidate);
                return font;
            }
            catch (IOException | RuntimeException e) {
                log.warn("Failed to load system font {}: {}", (Object)candidate, (Object)e.getMessage());
            }
        }
        throw new IllegalStateException("无法找到中文字体文件，无法生成成绩单 PDF。请安装中文字体或将字体放入 classpath 的 fonts/ 目录下。");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private PDFont loadHeaderFont(PDDocument document, PDFont fallbackFont) {
        String[] systemFontCandidates;
        String[] classpathResources;
        for (String resourcePath : classpathResources = new String[]{"fonts/MaShanZheng-Regular.ttf"}) {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) continue;
            try {
                PDType0Font font = PDType0Font.load((PDDocument)document, (InputStream)is);
                log.info("PDF header font loaded from classpath: {}", (Object)resourcePath);
                PDType0Font pDType0Font = font;
                return pDType0Font;
            }
            catch (IOException | RuntimeException e) {
                log.warn("Failed to load classpath header font {}: {}", (Object)resourcePath, (Object)e.getMessage());
            }
            finally {
                try {
                    is.close();
                }
                catch (IOException e) {}
            }
        }
        for (String candidate : systemFontCandidates = new String[]{"/Library/Fonts/MaShanZheng-Regular.ttf", System.getProperty("user.home") + "/Library/Fonts/MaShanZheng-Regular.ttf", "/System/Library/Fonts/Supplemental/STKaiti.ttf", "/Library/Fonts/STKaiti.ttf", "/System/Library/Fonts/Supplemental/Kaiti.ttc", "/Library/Fonts/Kaiti.ttc", "C:\\Windows\\Fonts\\simkai.ttf", "C:\\Windows\\Fonts\\STXINGKA.TTF"}) {
            Path fontPath = Paths.get(candidate, new String[0]);
            if (!Files.exists(fontPath, new LinkOption[0])) continue;
            try {
                PDFont font = candidate.toLowerCase(Locale.ROOT).endsWith(".ttc") ? this.loadFontFromCollection(document, fontPath.toFile()) : PDType0Font.load((PDDocument)document, (File)fontPath.toFile());
                if (font == null) continue;
                log.info("PDF header font loaded from system: {}", (Object)candidate);
                return font;
            }
            catch (IOException | RuntimeException e) {
                log.warn("Failed to load system header font {}: {}", (Object)candidate, (Object)e.getMessage());
            }
        }
        return fallbackFont;
    }

    private PDFont loadFontFromCollection(PDDocument document, File fontFile) throws IOException {
        PDFont[] loadedFont = new PDFont[1];
        try (TrueTypeCollection collection = new TrueTypeCollection(fontFile);){
            collection.processAllFonts(trueTypeFont -> {
                if (loadedFont[0] == null) {
                    loadedFont[0] = PDType0Font.load((PDDocument)document, (TrueTypeFont)trueTypeFont, (boolean)false);
                } else {
                    trueTypeFont.close();
                }
            });
        }
        return loadedFont[0];
    }

    private static class TranscriptPdfWriter
    implements AutoCloseable {
        private static final float LEFT = 50.0f;
        private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
        private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
        private static final float RIGHT = PAGE_WIDTH - 50.0f;
        private static final float TOP = 752.0f;
        private static final float BODY_BOTTOM = 118.0f;
        private static final float HEADER_TITLE_Y = 806.0f;
        private static final float HEADER_SEPARATOR_Y = 782.0f;
        private static final float FOOTER_LINE_Y = 88.0f;
        private static final float FOOTER_TEXT_START_Y = 74.0f;
        private static final Color TEXT_COLOR = new Color(0, 0, 0);
        private static final Color MUTED_TEXT_COLOR = new Color(18, 18, 18);
        private static final Color SCHOOL_RED = new Color(142, 0, 0);
        private static final Color TABLE_BORDER_COLOR = new Color(150, 125, 125);
        private static final Color TABLE_HEADER_BG_COLOR = new Color(238, 222, 222);
        private static final float LINE_HEIGHT = 14.0f;
        private static final float TABLE_LINE_HEIGHT = 13.0f;
        private static final float TABLE_CELL_PADDING_TOP = 8.0f;
        private static final float TABLE_CELL_PADDING_X = 6.0f;
        private static final float TABLE_CELL_PADDING_BOTTOM = 8.0f;
        private final PDDocument document;
        private final PDFont regularFont;
        private final PDFont boldFont;
        private final PDFont headerFont;
        private final PDImageXObject logoImage;
        private final PDImageXObject watermarkImage;
        private final String schoolNameZh;
        private final String schoolNameEn;
        private final String schoolAddressZh;
        private final String schoolAddressEn;
        private final String schoolPhone;
        private final String schoolPostCode;
        private final List<StudentAchievement> achievements;
        private final Map<Long, String> honorTypeNames;
        private final String examTypeName;
        private PDPageContentStream content;
        private float y;
        private int pageNumber;

        TranscriptPdfWriter(PDDocument document, PDFont regularFont, PDFont boldFont, PDFont headerFont, PDImageXObject logoImage, PDImageXObject watermarkImage, String schoolNameZh, String schoolNameEn, String schoolAddressZh, String schoolAddressEn, String schoolPhone, String schoolPostCode, List<StudentAchievement> achievements, Map<Long, String> honorTypeNames, String examTypeName) throws IOException {
            this.document = document;
            this.regularFont = regularFont;
            this.boldFont = boldFont;
            this.headerFont = headerFont;
            this.logoImage = logoImage;
            this.watermarkImage = watermarkImage;
            this.schoolNameZh = schoolNameZh;
            this.schoolNameEn = schoolNameEn;
            this.schoolAddressZh = schoolAddressZh;
            this.schoolAddressEn = schoolAddressEn;
            this.schoolPhone = schoolPhone;
            this.schoolPostCode = schoolPostCode;
            this.achievements = achievements;
            this.honorTypeNames = honorTypeNames;
            this.examTypeName = examTypeName;
            this.newPage();
        }

        void writeReportTitle(String examTypeName) throws IOException {
            this.ensureSpace(50.0f);
            this.y -= 12.0f;
            this.writeCenteredText("成绩报告单", this.boldFont, 24, TEXT_COLOR, 32.0f);
            if (TranscriptPdfWriter.hasText(examTypeName)) {
                this.ensureSpace(18.0f);
                this.writeCenteredText(examTypeName, this.boldFont, 14, SCHOOL_RED, 22.0f);
            }
            this.y -= 2.0f;
        }

        void writeStudentInfoTable(String studentName, String studentNo, String gradeLevel, String className, String academicYear, String termName) throws IOException {
            this.ensureSpace(48.0f);
            this.drawCompactInfoRow(new String[]{"学号", TranscriptPdfWriter.safeText(studentNo), "姓名", TranscriptPdfWriter.safeText(studentName), "年级", TranscriptPdfWriter.safeText(gradeLevel)});
            this.drawCompactInfoRow(new String[]{"班级", TranscriptPdfWriter.safeText(className), "学年", TranscriptPdfWriter.safeText(academicYear), "学期", TranscriptPdfWriter.safeText(termName)});
            this.y -= 14.0f;
        }

        void writeScoreTable(List<Map<String, Object>> results, List<GradeResult> grades) throws IOException {
            if (results.isEmpty()) {
                this.writeLine("暂无课程成绩数据");
                return;
            }
            float colSubject = 315.0f;
            float colMark = 90.0f;
            float colGrade = 90.0f;
            float[] colWidths = new float[]{colSubject, colMark, colGrade};
            double totalScore = 0.0;
            int subjectCount = 0;
            this.ensureSpace(60.0f);
            this.drawScoreHeaderRow(colWidths);
            for (int i = 0; i < results.size(); ++i) {
                Map<String, Object> result = results.get(i);
                GradeResult grade = grades.get(i);
                String subjectName = Objects.toString(result.get("subjectName"), "");
                double score = TranscriptService.parseScore(Objects.toString(result.get("score"), ""));
                totalScore += score;
                ++subjectCount;
                String markText = TranscriptPdfWriter.formatMark(score);
                String[] cells = new String[]{subjectName, markText, grade.letterGrade};
                this.drawScoreDataRow(cells, colWidths);
            }
            this.drawScoreSummaryRow(colWidths, totalScore, subjectCount);
            this.y -= 10.0f;
        }

        private void drawScoreHeaderRow(float[] colWidths) throws IOException {
            float rowHeight = 28.0f;
            this.ensureSpace(rowHeight);
            float rowTop = this.y;
            this.content.setNonStrokingColor(TABLE_HEADER_BG_COLOR);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.fill();
            this.content.setStrokingColor(TABLE_BORDER_COLOR);
            this.content.setLineWidth(0.5f);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.stroke();
            this.drawColumnBorders(rowTop, rowHeight, colWidths);
            this.content.setNonStrokingColor(TEXT_COLOR);
            float x = 50.0f;
            this.drawHeaderCellText("课程", x, rowTop, colWidths[0], rowHeight, true);
            this.drawHeaderCellText("分数", x += colWidths[0], rowTop, colWidths[1], rowHeight, true);
            this.drawHeaderCellText("等级", x += colWidths[1], rowTop, colWidths[2], rowHeight, true);
            this.y -= rowHeight;
        }

        private void drawScoreDataRow(String[] cells, float[] colWidths) throws IOException {
            float rowHeight = 22.0f;
            this.ensureSpace(rowHeight);
            float rowTop = this.y;
            this.content.setStrokingColor(TABLE_BORDER_COLOR);
            this.content.setLineWidth(0.5f);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.stroke();
            this.drawColumnBorders(rowTop, rowHeight, colWidths);
            this.content.setNonStrokingColor(TEXT_COLOR);
            float x = 50.0f;
            this.drawCellText(cells[0], x, rowTop, colWidths[0], rowHeight, this.regularFont, 9, false);
            this.drawCellText(cells[1], x += colWidths[0], rowTop, colWidths[1], rowHeight, this.regularFont, 9, true);
            this.drawCellText(cells[2], x += colWidths[1], rowTop, colWidths[2], rowHeight, this.boldFont, 9, true);
            this.y -= rowHeight;
        }

        private void drawScoreSummaryRow(float[] colWidths, double totalScore, int subjectCount) throws IOException {
            float rowHeight = 24.0f;
            this.ensureSpace(rowHeight);
            float rowTop = this.y;
            this.content.setNonStrokingColor(TABLE_HEADER_BG_COLOR);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.fill();
            this.content.setStrokingColor(TABLE_BORDER_COLOR);
            this.content.setLineWidth(0.5f);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.stroke();
            this.drawColumnBorders(rowTop, rowHeight, colWidths);
            this.content.setNonStrokingColor(TEXT_COLOR);
            float x = 50.0f;
            this.drawCellText("成绩汇总", x, rowTop, colWidths[0], rowHeight, this.boldFont, 9, false);
            this.drawCellText("总分：" + TranscriptPdfWriter.formatMark(totalScore), x += colWidths[0], rowTop, colWidths[1], rowHeight, this.boldFont, 9, false);
            this.drawCellText("科目数量：" + subjectCount, x += colWidths[1], rowTop, colWidths[2], rowHeight, this.boldFont, 9, false);
            this.y -= rowHeight;
        }

        private void drawColumnBorders(float rowTop, float rowHeight, float[] colWidths) throws IOException {
            float x = 50.0f;
            for (int i = 0; i < colWidths.length - 1; ++i) {
                this.content.moveTo(x += colWidths[i], rowTop);
                this.content.lineTo(x, rowTop - rowHeight);
            }
            this.content.stroke();
        }

        void writeAchievements() throws IOException {
            if (this.achievements == null || this.achievements.isEmpty()) {
                return;
            }
            this.ensureSpace(80.0f);
            this.y -= 6.0f;
            float colHonorType = 120.0f;
            float colDetail = RIGHT - 50.0f - colHonorType;
            float[] colWidths = new float[]{colHonorType, colDetail};
            this.drawSectionTitleRow("荣誉成就", TranscriptPdfWriter.sum(colWidths));
            this.drawAchievementHeaderRow(colWidths);
            for (StudentAchievement a : this.achievements) {
                String honorTypeName = this.resolveHonorTypeName(a.getHonorTypeId());
                String detail = a.getAchievementText();
                this.drawAchievementDataRow(new String[]{honorTypeName, detail}, colWidths);
            }
            this.y -= 4.0f;
        }

        private String resolveHonorTypeName(Long honorTypeId) {
            if (honorTypeId == null) {
                return "";
            }
            String name = this.honorTypeNames.get(honorTypeId);
            return name != null ? name : "";
        }

        private void drawAchievementHeaderRow(float[] colWidths) throws IOException {
            float rowHeight = 28.0f;
            this.ensureSpace(rowHeight);
            float rowTop = this.y;
            this.content.setNonStrokingColor(TABLE_HEADER_BG_COLOR);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.fill();
            this.content.setStrokingColor(TABLE_BORDER_COLOR);
            this.content.setLineWidth(0.5f);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.stroke();
            this.drawColumnBorders(rowTop, rowHeight, colWidths);
            this.content.setNonStrokingColor(TEXT_COLOR);
            float x = 50.0f;
            this.drawHeaderCellText("荣誉类型", x, rowTop, colWidths[0], rowHeight, true);
            this.drawHeaderCellText("详细情况", x += colWidths[0], rowTop, colWidths[1], rowHeight, true);
            this.y -= rowHeight;
        }

        private void drawAchievementDataRow(String[] cells, float[] colWidths) throws IOException {
            float detailMaxWidth = colWidths[1] - 12.0f;
            List<String> detailLines = this.wrapAchievementDetail(cells[1], detailMaxWidth, this.regularFont, 10);
            float rowHeight = Math.max(24.0f, (float)detailLines.size() * 13.0f + 8.0f + 8.0f);
            this.ensureSpace(rowHeight);
            float rowTop = this.y;
            this.content.setStrokingColor(TABLE_BORDER_COLOR);
            this.content.setLineWidth(0.5f);
            this.content.addRect(50.0f, rowTop - rowHeight, TranscriptPdfWriter.sum(colWidths), rowHeight);
            this.content.stroke();
            this.drawColumnBorders(rowTop, rowHeight, colWidths);
            this.content.setNonStrokingColor(TEXT_COLOR);
            float honorTypeTextY = rowTop - rowHeight + (rowHeight - 9.0f) / 2.0f + 2.0f;
            this.content.beginText();
            this.content.setFont(this.boldFont, 9.0f);
            String safeHonorType = this.cleanPdfText(cells[0]);
            float honorTypeWidth = this.boldFont.getStringWidth(safeHonorType) / 1000.0f * 9.0f;
            float honorTypeTextX = 50.0f + Math.max(6.0f, (colWidths[0] - honorTypeWidth) / 2.0f);
            this.content.newLineAtOffset(honorTypeTextX, honorTypeTextY);
            this.content.showText(safeHonorType);
            this.content.endText();
            float detailTextY = rowTop - 8.0f - 10.0f;
            float detailTextX = 50.0f + colWidths[0] + 6.0f;
            for (String line : detailLines) {
                this.content.beginText();
                this.content.setFont(this.regularFont, 10.0f);
                this.content.newLineAtOffset(detailTextX, detailTextY);
                this.content.showText(this.cleanPdfText(line));
                this.content.endText();
                detailTextY -= 13.0f;
            }
            this.y -= rowHeight;
        }

        private List<String> wrapAchievementDetail(String text, float maxWidth, PDFont font, int fontSize) throws IOException {
            String[] paragraphs;
            String safe = text == null ? "" : text;
            ArrayList<String> lines = new ArrayList<String>();
            if (safe.isEmpty()) {
                lines.add("");
                return lines;
            }
            for (String paragraph : paragraphs = safe.split("\n", -1)) {
                int codePoint;
                if (paragraph.isEmpty()) {
                    lines.add("");
                    continue;
                }
                StringBuilder current = new StringBuilder();
                for (int offset = 0; offset < paragraph.length(); offset += Character.charCount(codePoint)) {
                    codePoint = paragraph.codePointAt(offset);
                    String next = new String(Character.toChars(codePoint));
                    String candidate = current + next;
                    if (current.length() > 0 && font.getStringWidth(this.cleanPdfText(candidate)) / 1000.0f * (float)fontSize > maxWidth) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(next);
                }
                if (current.length() <= 0) continue;
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            return lines;
        }

        void writeCommentBlock(String studentName, String advisor, String subject, String teacher, String performanceComment, String strengths, String improvements) throws IOException {
            StringBuilder sb = new StringBuilder();
            if (TranscriptPdfWriter.hasText(performanceComment)) {
                sb.append(performanceComment);
            }
            if (TranscriptPdfWriter.hasText(strengths)) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("优点：").append(strengths);
            }
            if (TranscriptPdfWriter.hasText(improvements)) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("需改进：").append(improvements);
            }
            String combined = sb.length() > 0 ? sb.toString() : "无";
            this.ensureSpace(120.0f);
            this.writeTableRow(new String[]{"学生姓名", TranscriptPdfWriter.safeText(studentName), "班主任", TranscriptPdfWriter.safeText(advisor)}, new float[]{75.0f, 190.0f, 75.0f, 155.0f}, true);
            this.writeTableRow(new String[]{"课程", TranscriptPdfWriter.safeText(subject), "任课老师", TranscriptPdfWriter.safeText(teacher)}, new float[]{75.0f, 190.0f, 75.0f, 155.0f}, false);
            this.writeTableRow(new String[]{"评语", combined}, new float[]{75.0f, 420.0f}, false);
            this.writeBlankLine();
        }

        void writeSubjectCommentBlock(String subject, String teacher, String performanceComment, String strengths, String improvements) throws IOException {
            StringBuilder sb = new StringBuilder();
            if (TranscriptPdfWriter.hasText(performanceComment)) {
                sb.append(performanceComment);
            }
            if (TranscriptPdfWriter.hasText(strengths)) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("优点：").append(strengths);
            }
            if (TranscriptPdfWriter.hasText(improvements)) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("需改进：").append(improvements);
            }
            String combined = sb.length() > 0 ? sb.toString() : "无";
            this.ensureSpace(100.0f);
            this.writeTableRow(new String[]{"课程", TranscriptPdfWriter.safeText(subject), "任课老师", TranscriptPdfWriter.safeText(teacher)}, new float[]{75.0f, 190.0f, 75.0f, 155.0f}, false);
            this.writeTableRow(new String[]{"评语", combined}, new float[]{75.0f, 420.0f}, false);
            this.writeBlankLine();
        }

        void writeOverallComment(String studentName, String advisor, String overallComment, String strengths, String improvements) throws IOException {
            StringBuilder sb = new StringBuilder();
            if (TranscriptPdfWriter.hasText(overallComment)) {
                sb.append(overallComment);
            }
            if (TranscriptPdfWriter.hasText(strengths)) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("优点：").append(strengths);
            }
            if (TranscriptPdfWriter.hasText(improvements)) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("需改进：").append(improvements);
            }
            String combined = sb.length() > 0 ? sb.toString() : "无";
            this.drawSectionTitleRow("班主任总体评价", 495.0f);
            this.writeTableRow(new String[]{"学生姓名", TranscriptPdfWriter.safeText(studentName), "班主任", TranscriptPdfWriter.safeText(advisor)}, new float[]{75.0f, 190.0f, 75.0f, 155.0f}, true);
            this.writeTableRow(new String[]{"评语", combined}, new float[]{75.0f, 420.0f}, false);
            this.writeBlankLine();
        }

        void writeFooter() throws IOException {
            this.drawPageFooter();
        }

        void writeClosingNote() throws IOException {
            this.ensureSpace(44.0f);
            this.y -= 18.0f;
            if (TranscriptPdfWriter.hasText(this.schoolNameZh)) {
                this.writeCenteredTextAt(this.y, this.schoolNameZh, this.boldFont, 11, TEXT_COLOR);
                this.y -= 16.0f;
            }
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
            this.writeCenteredTextAt(this.y, dateStr, this.regularFont, 9, TEXT_COLOR);
            this.y -= 12.0f;
        }

        void writeSection(String text) throws IOException {
            this.ensureSpace(30.0f);
            this.y -= 6.0f;
            this.writeTextAt(50.0f, this.y, text, this.boldFont, 14);
            this.y -= 24.0f;
        }

        private void drawSectionTitleRow(String title, float totalWidth) throws IOException {
            float rowHeight = 30.0f;
            this.ensureSpace(rowHeight);
            float rowTop = this.y;
            this.content.setNonStrokingColor(new Color(218, 195, 195));
            this.content.addRect(50.0f, rowTop - rowHeight, totalWidth, rowHeight);
            this.content.fill();
            this.content.setStrokingColor(TABLE_BORDER_COLOR);
            this.content.setLineWidth(0.5f);
            this.content.addRect(50.0f, rowTop - rowHeight, totalWidth, rowHeight);
            this.content.stroke();
            this.content.setNonStrokingColor(TEXT_COLOR);
            this.content.beginText();
            this.content.setFont(this.boldFont, 12.0f);
            String safeTitle = this.cleanPdfText(title);
            float titleWidth = this.boldFont.getStringWidth(safeTitle) / 1000.0f * 12.0f;
            float titleX = 50.0f + (totalWidth - titleWidth) / 2.0f;
            float titleY = rowTop - rowHeight + (rowHeight - 12.0f) / 2.0f + 2.0f;
            this.content.newLineAtOffset(titleX, titleY);
            this.content.showText(safeTitle);
            this.content.endText();
            this.y -= rowHeight;
        }

        void writeLine(String text) throws IOException {
            this.writeTextAt(50.0f, this.y, TranscriptPdfWriter.safeText(text), this.boldFont, 10);
            this.y -= 14.0f;
        }

        void writeBlankLine() throws IOException {
            this.ensureSpace(10.0f);
            this.y -= 8.0f;
        }

        private void drawCompactInfoRow(String[] cells) throws IOException {
            float[] widths = new float[]{45.0f, 135.0f, 45.0f, 115.0f, 45.0f, 110.0f};
            float rowHeight = 20.0f;
            this.ensureSpace(rowHeight);
            float rowTop = this.y;
            float x = 50.0f;
            for (int i = 0; i < cells.length; ++i) {
                boolean labelCell;
                boolean bl = labelCell = i % 2 == 0;
                if (labelCell) {
                    this.content.setNonStrokingColor(TABLE_HEADER_BG_COLOR);
                    this.content.addRect(x, rowTop - rowHeight, widths[i], rowHeight);
                    this.content.fill();
                }
                this.content.setStrokingColor(TABLE_BORDER_COLOR);
                this.content.setLineWidth(0.45f);
                this.content.addRect(x, rowTop - rowHeight, widths[i], rowHeight);
                this.content.stroke();
                this.content.setNonStrokingColor(TEXT_COLOR);
                this.drawCellText(cells[i], x, rowTop, widths[i], rowHeight, labelCell ? this.boldFont : this.regularFont, 8, labelCell);
                x += widths[i];
            }
            this.y -= rowHeight;
        }

        void writeTableRow(String[] cells, float[] widths, boolean header) throws IOException {
            ArrayList<List<String>> linesByCell = new ArrayList<List<String>>();
            int maxLines = 1;
            PDFont font = header ? this.boldFont : this.regularFont;
            int fontSize = 10;
            for (int i = 0; i < cells.length; ++i) {
                List<String> lines = this.splitCellText(cells[i], widths[i], font, fontSize);
                linesByCell.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            float rowHeight = Math.max(28.0f, (float)maxLines * 13.0f + 8.0f + 8.0f);
            this.ensureSpace(rowHeight);
            float x = 50.0f;
            float rowTop = this.y;
            for (int i = 0; i < cells.length; ++i) {
                if (header) {
                    this.content.setNonStrokingColor(TABLE_HEADER_BG_COLOR);
                    this.content.addRect(x, rowTop - rowHeight, widths[i], rowHeight);
                    this.content.fill();
                }
                this.content.setStrokingColor(TABLE_BORDER_COLOR);
                this.content.setLineWidth(0.5f);
                this.content.addRect(x, rowTop - rowHeight, widths[i], rowHeight);
                this.content.stroke();
                this.content.setNonStrokingColor(TEXT_COLOR);
                float textY = rowTop - 8.0f - 10.0f;
                for (String line : (List<String>)linesByCell.get(i)) {
                    String safeLine = this.cleanPdfText(line);
                    float textX = x + 6.0f;
                    if (header) {
                        float textWidth = font.getStringWidth(safeLine) / 1000.0f * (float)fontSize;
                        textX = x + Math.max(6.0f, (widths[i] - textWidth) / 2.0f);
                    }
                    this.content.beginText();
                    this.content.setFont(font, (float)fontSize);
                    this.content.newLineAtOffset(textX, textY);
                    this.content.showText(safeLine);
                    this.content.endText();
                    textY -= 13.0f;
                }
                x += widths[i];
            }
            this.y -= rowHeight;
        }

        private List<String> splitCellText(String text, float width, PDFont font, int fontSize) throws IOException {
            String[] paragraphs;
            String safe = text == null ? "" : text.replace('\r', '\n');
            float maxWidth = Math.max(16.0f, width - 12.0f);
            ArrayList<String> lines = new ArrayList<String>();
            if (safe.isEmpty()) {
                lines.add("");
                return lines;
            }
            for (String paragraph : paragraphs = safe.split("\n", -1)) {
                this.appendWrappedParagraph(lines, paragraph, maxWidth, font, fontSize);
            }
            return lines;
        }

        private void appendWrappedParagraph(List<String> lines, String paragraph, float maxWidth, PDFont font, int fontSize) throws IOException {
            int codePoint;
            if (paragraph == null || paragraph.isEmpty()) {
                lines.add("");
                return;
            }
            StringBuilder current = new StringBuilder();
            for (int offset = 0; offset < paragraph.length(); offset += Character.charCount(codePoint)) {
                codePoint = paragraph.codePointAt(offset);
                String next = new String(Character.toChars(codePoint));
                String candidate = current + next;
                if (current.length() > 0 && this.textWidth(candidate, font, fontSize) > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(next);
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
        }

        private void drawHeaderCellText(String text, float x, float rowTop, float cellWidth, float rowHeight, boolean isBold) throws IOException {
            this.content.beginText();
            this.content.setFont(isBold ? this.boldFont : this.regularFont, 9.0f);
            String safe = this.cleanPdfText(text);
            float textWidth = (isBold ? this.boldFont : this.regularFont).getStringWidth(safe) / 1000.0f * 9.0f;
            float textX = x + (cellWidth - textWidth) / 2.0f;
            float textY = rowTop - rowHeight + (rowHeight - 9.0f) / 2.0f + 2.0f;
            this.content.newLineAtOffset(Math.max(x + 2.0f, textX), textY);
            this.content.showText(safe);
            this.content.endText();
        }

        private void drawCellText(String text, float x, float rowTop, float cellWidth, float rowHeight, PDFont font, int fontSize, boolean center) throws IOException {
            this.content.beginText();
            this.content.setFont(font, (float)fontSize);
            String safe = this.cleanPdfText(text);
            float textWidth = font.getStringWidth(safe) / 1000.0f * (float)fontSize;
            float textX = center ? x + (cellWidth - textWidth) / 2.0f : x + 6.0f;
            float textY = rowTop - rowHeight + (rowHeight - (float)fontSize) / 2.0f + 2.0f;
            this.content.newLineAtOffset(Math.max(x + 2.0f, textX), textY);
            this.content.showText(safe);
            this.content.endText();
        }

        private void writeTextAt(float x, float yPos, String text, PDFont font, int fontSize) throws IOException {
            this.content.beginText();
            this.content.setFont(font, (float)fontSize);
            this.content.newLineAtOffset(x, yPos);
            this.content.showText(this.cleanPdfText(text));
            this.content.endText();
        }

        private void writeCenteredText(String text, PDFont font, int fontSize, Color color, float lineHeight) throws IOException {
            this.ensureSpace(lineHeight);
            String safe = this.cleanPdfText(text);
            float textWidth = font.getStringWidth(safe) / 1000.0f * (float)fontSize;
            this.content.setNonStrokingColor(color);
            this.content.beginText();
            this.content.setFont(font, (float)fontSize);
            this.content.newLineAtOffset((PAGE_WIDTH - textWidth) / 2.0f, this.y);
            this.content.showText(safe);
            this.content.endText();
            this.content.setNonStrokingColor(TEXT_COLOR);
            this.y -= lineHeight;
        }

        private void writeCenteredTextAt(float yPos, String text, PDFont font, int fontSize, Color color) throws IOException {
            String safe = this.cleanPdfText(text);
            float textWidth = font.getStringWidth(safe) / 1000.0f * (float)fontSize;
            this.content.setNonStrokingColor(color);
            this.content.beginText();
            this.content.setFont(font, (float)fontSize);
            this.content.newLineAtOffset((PAGE_WIDTH - textWidth) / 2.0f, yPos);
            this.content.showText(safe);
            this.content.endText();
            this.content.setNonStrokingColor(TEXT_COLOR);
        }

        private List<String> wrapText(String text, float maxWidth, PDFont font, int fontSize) throws IOException {
            int codePoint;
            String safe = text == null ? "" : text;
            ArrayList<String> lines = new ArrayList<String>();
            StringBuilder current = new StringBuilder();
            for (int offset = 0; offset < safe.length(); offset += Character.charCount(codePoint)) {
                codePoint = safe.codePointAt(offset);
                String next = new String(Character.toChars(codePoint));
                String candidate = current + next;
                if (current.length() > 0 && font.getStringWidth(candidate) / 1000.0f * (float)fontSize > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(next);
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            return lines;
        }

        private float textWidth(String text, PDFont font, int fontSize) throws IOException {
            return font.getStringWidth(this.cleanPdfText(text)) / 1000.0f * (float)fontSize;
        }

        private String cleanPdfText(String text) {
            if (text == null) {
                return "";
            }
            // NFKC normalization converts compatibility characters to canonical equivalents:
            // e.g. subscript ₁ (U+2081) → 1, superscript ⁿ (U+207F) → n, fullwidth A (U+FF21) → A
            // This prevents "No glyph for U+XXXX" errors when fonts lack these glyphs.
            String cleaned = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC);
            return cleaned.replace('\n', ' ').replace('\r', ' ');
        }

        private void ensureSpace(float lineHeight) throws IOException {
            if (this.y - lineHeight < 118.0f) {
                this.newPage();
            }
        }

        private void newPage() throws IOException {
            if (this.content != null) {
                this.content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            this.document.addPage(page);
            this.content = new PDPageContentStream(this.document, page, PDPageContentStream.AppendMode.OVERWRITE, true, true);
            ++this.pageNumber;
            this.drawWatermark();
            this.drawPageHeader();
            this.drawPageFooter();
            this.y = 752.0f;
        }

        private void drawPageHeader() throws IOException {
            this.content.setNonStrokingColor(TEXT_COLOR);
            this.drawSchoolHeaderBrand();
            if (TranscriptPdfWriter.hasText(this.schoolNameEn)) {
                this.writeCenteredTextAt(788.0f, this.schoolNameEn, this.regularFont, 8, MUTED_TEXT_COLOR);
            }
            this.content.setStrokingColor(SCHOOL_RED);
            this.content.setLineWidth(1.1f);
            this.content.moveTo(50.0f, 782.0f);
            this.content.lineTo(RIGHT, 782.0f);
            this.content.stroke();
            this.content.setNonStrokingColor(TEXT_COLOR);
        }

        private void drawSchoolHeaderBrand() throws IOException {
            if (!TranscriptPdfWriter.hasText(this.schoolNameZh)) {
                return;
            }
            int fontSize = 17;
            String safeName = this.cleanPdfText(this.schoolNameZh);
            float textWidth = this.headerFont.getStringWidth(safeName) / 1000.0f * (float)fontSize;
            float logoSize = this.logoImage == null ? 0.0f : 24.0f;
            float gap = this.logoImage == null ? 0.0f : 8.0f;
            float totalWidth = logoSize + gap + textWidth;
            float x = (PAGE_WIDTH - totalWidth) / 2.0f;
            if (this.logoImage != null) {
                this.content.drawImage(this.logoImage, x, 799.0f, logoSize, logoSize);
                x += logoSize + gap;
            }
            this.content.setNonStrokingColor(SCHOOL_RED);
            this.content.beginText();
            this.content.setFont(this.headerFont, (float)fontSize);
            this.content.newLineAtOffset(x, 806.0f);
            this.content.showText(safeName);
            this.content.endText();
            this.content.beginText();
            this.content.setFont(this.headerFont, (float)fontSize);
            this.content.newLineAtOffset(x + 0.35f, 806.0f);
            this.content.showText(safeName);
            this.content.endText();
            this.content.setNonStrokingColor(TEXT_COLOR);
        }

        private void drawPageFooter() throws IOException {
            this.content.setStrokingColor(TABLE_BORDER_COLOR);
            this.content.setLineWidth(0.5f);
            this.content.moveTo(50.0f, 88.0f);
            this.content.lineTo(RIGHT, 88.0f);
            this.content.stroke();
            this.content.setNonStrokingColor(MUTED_TEXT_COLOR);
            float footerY = 74.0f;
            int fontSize = 8;
            if (TranscriptPdfWriter.hasText(this.schoolAddressZh)) {
                this.writeCenteredTextAt(footerY, "地址：" + this.schoolAddressZh, this.regularFont, fontSize, MUTED_TEXT_COLOR);
                footerY -= 12.0f;
            }
            if (TranscriptPdfWriter.hasText(this.schoolNameZh)) {
                this.writeCenteredTextAt(footerY, this.schoolNameZh, this.boldFont, fontSize, MUTED_TEXT_COLOR);
                footerY -= 12.0f;
            }
            if (TranscriptPdfWriter.hasText(this.schoolPhone)) {
                this.writeCenteredTextAt(footerY, "电话：" + this.schoolPhone, this.regularFont, fontSize, MUTED_TEXT_COLOR);
            }
            this.content.setNonStrokingColor(TEXT_COLOR);
        }

        private void drawWatermark() throws IOException {
            if (this.watermarkImage == null) {
                return;
            }
            try {
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(Float.valueOf(0.2f));
                gs.setStrokingAlphaConstant(Float.valueOf(0.2f));
                this.content.setGraphicsStateParameters(gs);
                float maxWidth = PAGE_WIDTH * 0.9f;
                float imgWidth = this.watermarkImage.getWidth();
                float imgHeight = this.watermarkImage.getHeight();
                float scale = maxWidth / imgWidth;
                float drawWidth = imgWidth * scale;
                float drawHeight = imgHeight * scale;
                float x = (PAGE_WIDTH - drawWidth) / 2.0f;
                float yPos = (PAGE_HEIGHT - drawHeight) / 2.0f;
                this.content.drawImage(this.watermarkImage, x, yPos, drawWidth, drawHeight);
                gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(Float.valueOf(1.0f));
                gs.setStrokingAlphaConstant(Float.valueOf(1.0f));
                this.content.setGraphicsStateParameters(gs);
            }
            catch (IOException iOException) {
                log.warn("Failed to draw watermark image: {}", (Object)iOException.getMessage());
            }
        }

        @Override
        public void close() throws IOException {
            if (this.content != null) {
                this.content.close();
            }
        }

        private static float sum(float[] arr) {
            float total = 0.0f;
            for (float v : arr) {
                total += v;
            }
            return total;
        }

        private static boolean hasText(String s) {
            return s != null && !s.trim().isEmpty();
        }

        private static String safeText(String s) {
            return s == null ? "" : s;
        }

        static String formatMark(double score) {
            if (score == Math.floor(score) && !Double.isInfinite(score)) {
                return String.format("%.0f", score);
            }
            return String.format("%.2f", score);
        }
    }

    private static final class GradeCalculator {
        private GradeCalculator() {
        }

        static GradeResult calculate(double score, double maxScore) {
            double pct;
            double d = pct = maxScore > 0.0 ? score / maxScore * 100.0 : 0.0;
            if (pct >= 97.0) {
                return new GradeResult("A+", 4.0, pct);
            }
            if (pct >= 93.0) {
                return new GradeResult("A", 4.0, pct);
            }
            if (pct >= 90.0) {
                return new GradeResult("A-", 3.75, pct);
            }
            if (pct >= 87.0) {
                return new GradeResult("B+", 3.5, pct);
            }
            if (pct >= 83.0) {
                return new GradeResult("B", 3.0, pct);
            }
            if (pct >= 80.0) {
                return new GradeResult("B-", 2.75, pct);
            }
            if (pct >= 77.0) {
                return new GradeResult("C+", 2.5, pct);
            }
            if (pct >= 73.0) {
                return new GradeResult("C", 2.0, pct);
            }
            if (pct >= 70.0) {
                return new GradeResult("C-", 1.75, pct);
            }
            if (pct >= 67.0) {
                return new GradeResult("D+", 1.5, pct);
            }
            if (pct >= 63.0) {
                return new GradeResult("D", 1.0, pct);
            }
            if (pct >= 60.0) {
                return new GradeResult("D-", 0.75, pct);
            }
            return new GradeResult("F", 0.0, pct);
        }
    }

    private static final class GradeResult {
        final String letterGrade;
        final double gpaPoints;
        final double percentage;

        GradeResult(String letterGrade, double gpaPoints, double percentage) {
            this.letterGrade = letterGrade;
            this.gpaPoints = gpaPoints;
            this.percentage = percentage;
        }
    }
}

