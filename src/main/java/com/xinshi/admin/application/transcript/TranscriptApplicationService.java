/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.transcript;

import com.xinshi.admin.domain.academicterm.AcademicTermRepository;
import com.xinshi.admin.domain.class_.SchoolClassRepository;
import com.xinshi.admin.domain.classsubject.ClassSubject;
import com.xinshi.admin.domain.classsubject.ClassSubjectRepository;
import com.xinshi.admin.domain.courseresult.CourseResult;
import com.xinshi.admin.domain.courseresult.CourseResultRepository;
import com.xinshi.admin.domain.overallcomment.OverallCommentRepository;
import com.xinshi.admin.domain.shared.AuthSession;
import com.xinshi.admin.domain.shared.AuthorizationService;
import com.xinshi.admin.domain.student.StudentRepository;
import com.xinshi.admin.domain.transcript.Transcript;
import com.xinshi.admin.domain.transcript.TranscriptRepository;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TranscriptApplicationService {
    private final TranscriptRepository transcriptRepository;
    private final CourseResultRepository courseResultRepository;
    private final OverallCommentRepository overallCommentRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository classRepository;
    private final AcademicTermRepository academicTermRepository;
    private final AuthorizationService authorizationService;
    private final AuthSession authSession;

    public TranscriptApplicationService(TranscriptRepository transcriptRepository, CourseResultRepository courseResultRepository, OverallCommentRepository overallCommentRepository, ClassSubjectRepository classSubjectRepository, StudentRepository studentRepository, SchoolClassRepository classRepository, AcademicTermRepository academicTermRepository, AuthorizationService authorizationService, AuthSession authSession) {
        this.transcriptRepository = transcriptRepository;
        this.courseResultRepository = courseResultRepository;
        this.overallCommentRepository = overallCommentRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.studentRepository = studentRepository;
        this.classRepository = classRepository;
        this.academicTermRepository = academicTermRepository;
        this.authorizationService = authorizationService;
        this.authSession = authSession;
    }

    public PageResult<Transcript> listTranscripts(Long academicTermId, Long classId, Long studentId, PageRequest pageRequest) {
        this.authorizationService.ensureReadableTranscriptScope(academicTermId, classId, studentId);
        long userId = this.authSession.userId();
        List<String> roles = this.authSession.roles();
        long total = this.transcriptRepository.count(academicTermId, classId, studentId, userId, roles);
        List<Transcript> items = this.transcriptRepository.findAll(academicTermId, classId, studentId, userId, roles, pageRequest.limit(), pageRequest.offset());
        return new PageResult<Transcript>(items, total, pageRequest.page(), pageRequest.size());
    }

    public Transcript getTranscript(long id) {
        Transcript transcript = this.transcriptRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("成绩单不存在"));
        this.authorizationService.ensureCanAccessTranscript(id);
        return transcript;
    }

    public Transcript getTranscriptByTermAndStudent(long academicTermId, long studentId) {
        Transcript transcript = this.transcriptRepository.findByTermAndStudent(academicTermId, studentId).orElse(null);
        if (transcript != null) {
            this.authorizationService.ensureCanAccessTranscript(transcript.getId());
        }
        return transcript;
    }

    public void ensureTranscriptResultsComplete(long academicTermId, long classId) {
        List<ClassSubject> classSubjects = this.classSubjectRepository.findByTermAndClass(academicTermId, classId);
        if (classSubjects.isEmpty()) {
            throw new IllegalArgumentException("当前班级未配置课程，不能生成成绩单");
        }
        List<CourseResult> results = this.courseResultRepository.findByParams(academicTermId, classId, null, null, this.authSession.userId(), this.authSession.roles());
        // 按 subjectId（科目逻辑ID）比较，而非 classSubjectId（关联表主键），
        // 以兼容学生换班后旧成绩的 classSubjectId 与新班级不匹配的场景
        Set<Long> resultSubjectIds = results.stream()
            .map(CourseResult::getSubjectId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List missing = classSubjects.stream()
            .filter(cs -> !resultSubjectIds.contains(cs.getSubjectId()))
            .map(ClassSubject::getSubjectName)
            .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("以下课程尚未录入成绩，不能生成成绩单：" + String.join((CharSequence) "、", missing));
        }
    }
}

