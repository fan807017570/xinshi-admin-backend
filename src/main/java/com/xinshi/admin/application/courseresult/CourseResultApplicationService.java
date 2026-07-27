/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.courseresult;

import com.xinshi.admin.domain.classsubject.ClassSubject;
import com.xinshi.admin.domain.classsubject.ClassSubjectRepository;
import com.xinshi.admin.domain.courseresult.CourseResult;
import com.xinshi.admin.domain.courseresult.CourseResultRepository;
import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
import com.xinshi.admin.domain.subject.Subject;
import com.xinshi.admin.domain.subject.SubjectRepository;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseResultApplicationService {
    private final CourseResultRepository courseResultRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final AuthSession authSession;

    public CourseResultApplicationService(CourseResultRepository courseResultRepository, ClassSubjectRepository classSubjectRepository, SubjectRepository subjectRepository, JdbcTemplate jdbcTemplate, AuthorizationService authorizationService, AuthSession authSession) {
        this.courseResultRepository = courseResultRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.authSession = authSession;
    }

    public List<CourseResult> listStudentResults(Long academicTermId, Long classId, Long studentId, Long classSubjectId) {
        this.authorizationService.ensureReadableResultScope(academicTermId, classId, studentId, classSubjectId);
        return this.courseResultRepository.findByParams(academicTermId, classId, studentId, classSubjectId, this.authSession.userId(), this.authSession.roles());
    }

    public PageResult<Map<String, Object>> listTeacherScoreEntries(Long academicTermId, Long classId, Long subjectId, String keyword, String mode, PageRequest pageRequest) {
        this.authorizationService.ensureTeacherCanWriteResults();
        long userId = this.authSession.userId();
        List<String> roles = this.authSession.roles();
        long total = this.courseResultRepository.countTeacherScoreEntries(academicTermId, classId, subjectId, keyword, mode, userId, roles);
        List<CourseResult> results = this.courseResultRepository.findTeacherScoreEntries(academicTermId, classId, subjectId, keyword, mode, userId, roles, pageRequest.limit(), pageRequest.offset());
        ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (CourseResult r : results) {
            items.add(this.toResultMap(r));
        }
        return new PageResult<Map<String, Object>>(items, total, pageRequest.page(), pageRequest.size());
    }

    public CourseResult saveStudentResult(long academicTermId, Long requestedClassSubjectId, long studentId, double score, String performanceComment, String strengths, String improvementPoints, long evaluatorUserId, int status, Long classId, Long subjectId) {
        this.authorizationService.ensureTeacherCanWriteResults();
        long classSubjectId = requestedClassSubjectId == null ? this.ensureClassSubjectForResult(classId, subjectId, academicTermId) : requestedClassSubjectId.longValue();
        this.authorizationService.ensureCanAccessClassSubject(classSubjectId);
        ClassSubject cs = this.classSubjectRepository.findById(classSubjectId).orElseThrow(() -> new IllegalArgumentException("班级课程不存在"));
        this.authorizationService.ensureStudentBelongsToClass(studentId, cs.getClassId());
        Subject subject = this.subjectRepository.findById(cs.getSubjectId()).orElseThrow(() -> new IllegalArgumentException("科目不存在"));
        subject.validateScore(score);
        Optional<CourseResult> existing = this.courseResultRepository.findByTermClassSubjectStudent(academicTermId, classSubjectId, studentId);
        if (existing.isPresent()) {
            this.courseResultRepository.update(CourseResult.record(academicTermId, classSubjectId, studentId, score, performanceComment, strengths, improvementPoints, evaluatorUserId, status));
            return this.courseResultRepository.findByTermClassSubjectStudent(academicTermId, classSubjectId, studentId).orElseThrow(() -> new IllegalStateException("更新成绩后未找到记录"));
        }
        return this.courseResultRepository.save(CourseResult.record(academicTermId, classSubjectId, studentId, score, performanceComment, strengths, improvementPoints, evaluatorUserId, status));
    }

    public CourseResult publishStudentResult(long id) {
        this.authorizationService.ensureTeacherCanWriteResults();
        this.authorizationService.ensureCanAccessResult(id);
        this.courseResultRepository.publish(id);
        return this.courseResultRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("成绩不存在"));
    }

    public CourseResult getResult(long id) {
        CourseResult result = this.courseResultRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("成绩不存在"));
        this.authorizationService.ensureCanAccessResult(id);
        return result;
    }

    private long ensureClassSubjectForResult(Long classId, Long subjectId, long academicTermId) {
        if (classId == null || subjectId == null) {
            throw new IllegalArgumentException("classId 和 subjectId 不能为空");
        }
        this.authorizationService.ensureCanAccessClass(classId);
        Optional<ClassSubject> existing = this.classSubjectRepository.findByTermClassSubject(academicTermId, classId, subjectId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        ClassSubject cs = ClassSubject.create(academicTermId, classId, subjectId, null, 1);
        return this.classSubjectRepository.save(cs).getId();
    }

    private Map<String, Object> toResultMap(CourseResult r) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", r.getId());
        map.put("academicTermId", r.getAcademicTermId());
        map.put("termName", r.getTermName());
        map.put("classSubjectId", r.getClassSubjectId());
        map.put("classId", r.getClassId());
        map.put("className", r.getClassName());
        map.put("studentId", r.getStudentId());
        map.put("studentName", r.getStudentName());
        map.put("studentNo", r.getStudentNo());
        map.put("subjectId", r.getSubjectId());
        map.put("subjectName", r.getSubjectName());
        map.put("minScore", r.getMinScore());
        map.put("maxScore", r.getMaxScore());
        map.put("score", r.getScore());
        map.put("performanceComment", r.getPerformanceComment());
        map.put("strengths", r.getStrengths());
        map.put("improvementPoints", r.getImprovementPoints());
        map.put("teacherUserId", r.getTeacherUserId());
        map.put("teacherName", r.getTeacherName());
        map.put("evaluatorUserId", r.getEvaluatorUserId());
        map.put("evaluatorName", r.getEvaluatorName());
        map.put("evaluatedAt", r.getEvaluatedAt());
        map.put("status", r.getStatusCode());
        map.put("createdAt", r.getCreatedAt());
        return map;
    }
}

