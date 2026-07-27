/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.core.io.FileSystemResource
 *  org.springframework.core.io.Resource
 *  org.springframework.http.MediaType
 *  org.springframework.http.ResponseEntity
 *  org.springframework.http.ResponseEntity$BodyBuilder
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PatchMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package com.xinshi.admin.interfaces.web;

import com.xinshi.admin.application.academicterm.AcademicTermService;
import com.xinshi.admin.application.achievement.AchievementService;
import com.xinshi.admin.application.class_.ClassManagementService;
import com.xinshi.admin.application.commentpolish.CommentPolishService;
import com.xinshi.admin.application.courseresult.CourseResultService;
import com.xinshi.admin.application.courseresult.GradeExcelService;
import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.examtype.ExamTypeService;
import com.xinshi.admin.application.honortype.HonorTypeService;
import com.xinshi.admin.application.overallcomment.OverallCommentService;
import com.xinshi.admin.application.student.StudentManagementService;
import com.xinshi.admin.application.subject.SubjectManagementService;
import com.xinshi.admin.application.transcript.TranscriptService;
import com.xinshi.admin.application.user.UserManagementService;
import com.xinshi.admin.interfaces.dto.PageRequest;
import com.xinshi.admin.interfaces.dto.PageResult;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value={"/api"})
public class SchoolAdminController {
    private static final Logger log = LoggerFactory.getLogger(SchoolAdminController.class);
    private final UserManagementService userManagementService;
    private final AcademicTermService academicTermService;
    private final ClassManagementService classManagementService;
    private final SubjectManagementService subjectManagementService;
    private final StudentManagementService studentManagementService;
    private final CourseResultService courseResultService;
    private final OverallCommentService overallCommentService;
    private final TranscriptService transcriptService;
    private final AchievementService achievementService;
    private final HonorTypeService honorTypeService;
    private final ExamTypeService examTypeService;
    private final CommentPolishService commentPolishService;
    private final GradeExcelService gradeExcelService;
    private final AccessControlService accessControlService;

    public SchoolAdminController(UserManagementService userManagementService, AcademicTermService academicTermService, ClassManagementService classManagementService, SubjectManagementService subjectManagementService, StudentManagementService studentManagementService, CourseResultService courseResultService, OverallCommentService overallCommentService, TranscriptService transcriptService, AchievementService achievementService, HonorTypeService honorTypeService, ExamTypeService examTypeService, CommentPolishService commentPolishService, GradeExcelService gradeExcelService, AccessControlService accessControlService) {
        this.userManagementService = userManagementService;
        this.academicTermService = academicTermService;
        this.classManagementService = classManagementService;
        this.subjectManagementService = subjectManagementService;
        this.studentManagementService = studentManagementService;
        this.courseResultService = courseResultService;
        this.overallCommentService = overallCommentService;
        this.transcriptService = transcriptService;
        this.achievementService = achievementService;
        this.honorTypeService = honorTypeService;
        this.examTypeService = examTypeService;
        this.commentPolishService = commentPolishService;
        this.gradeExcelService = gradeExcelService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value={"/users"})
    public PageResult<Map<String, Object>> listUsers(@RequestParam(required=false) String keyword, @RequestParam(required=false) Integer status, @RequestParam(required=false) String roleCode, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return this.userManagementService.listUsers(keyword, status, roleCode, new PageRequest(page, size));
    }

    @GetMapping(value={"/users/login-name-exists"})
    public Map<String, Object> loginNameExists(@RequestParam String loginName) {
        return Collections.singletonMap("exists", this.userManagementService.loginNameExists(loginName));
    }

    @GetMapping(value={"/users/{id}"})
    public Map<String, Object> getUser(@PathVariable long id) {
        return this.userManagementService.getUser(id);
    }

    @PostMapping(value={"/users"})
    public Map<String, Object> createUser(@RequestBody Map<String, Object> request) {
        return this.userManagementService.createUser(request);
    }

    @PutMapping(value={"/users/{id}"})
    public Map<String, Object> updateUser(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.userManagementService.updateUser(id, request);
    }

    @PatchMapping(value={"/users/{id}/status"})
    public Map<String, Object> updateUserStatus(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.userManagementService.updateUserStatus(id, ((Number)request.getOrDefault("status", 1)).intValue());
    }

    @PutMapping(value={"/users/{id}/roles"})
    public void assignRoles(@PathVariable long id, @RequestBody Map<String, Object> request) {
        this.userManagementService.assignRoles(id, (List)request.get("roleCodes"));
    }

    @GetMapping(value={"/roles"})
    public List<Map<String, Object>> listRoles() {
        return this.userManagementService.listRoles();
    }

    @PostMapping(value={"/roles"})
    public Map<String, Object> createRole(@RequestBody Map<String, Object> request) {
        return this.userManagementService.createRole(request);
    }

    @GetMapping(value={"/academic-terms"})
    public List<Map<String, Object>> listAcademicTerms() {
        return this.academicTermService.listAcademicTerms();
    }

    @GetMapping(value={"/academic-terms/{id}"})
    public Map<String, Object> getAcademicTerm(@PathVariable long id) {
        return this.academicTermService.getAcademicTerm(id);
    }

    @PostMapping(value={"/academic-terms"})
    public Map<String, Object> createAcademicTerm(@RequestBody Map<String, Object> request) {
        return this.academicTermService.createAcademicTerm(request);
    }

    @PutMapping(value={"/academic-terms/{id}"})
    public Map<String, Object> updateAcademicTerm(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.academicTermService.updateAcademicTerm(id, request);
    }

    @GetMapping(value={"/classes"})
    public PageResult<Map<String, Object>> listClasses(@RequestParam(required=false) String gradeSession, @RequestParam(required=false) Integer gradeLevel, @RequestParam(required=false) String mode, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return this.classManagementService.listClasses(gradeSession, gradeLevel, mode, new PageRequest(page, size));
    }

    @GetMapping(value={"/classes/{id}"})
    public Map<String, Object> getClassDetail(@PathVariable long id) {
        return this.classManagementService.getClass(id);
    }

    @PostMapping(value={"/classes"})
    public Map<String, Object> createClass(@RequestBody Map<String, Object> request) {
        return this.classManagementService.createClass(request);
    }

    @PutMapping(value={"/classes/{id}"})
    public Map<String, Object> updateClass(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.classManagementService.updateClass(id, request);
    }

    @DeleteMapping(value={"/classes/{id}"})
    public void deleteClass(@PathVariable long id) {
        this.classManagementService.deleteClass(id);
    }

    @GetMapping(value={"/subjects"})
    public List<Map<String, Object>> listSubjects(@RequestParam(required=false) String mode) {
        return this.subjectManagementService.listSubjects(mode);
    }

    @GetMapping(value={"/subjects/{id}"})
    public Map<String, Object> getSubject(@PathVariable long id) {
        return this.subjectManagementService.getSubject(id);
    }

    @PostMapping(value={"/subjects"})
    public Map<String, Object> createSubject(@RequestBody Map<String, Object> request) {
        return this.subjectManagementService.createSubject(request);
    }

    @PutMapping(value={"/subjects/{id}"})
    public Map<String, Object> updateSubject(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.subjectManagementService.updateSubject(id, request);
    }

    @DeleteMapping(value={"/subjects/{id}"})
    public void deleteSubject(@PathVariable long id) {
        this.subjectManagementService.deleteSubject(id);
    }

    @GetMapping(value={"/grade-subjects"})
    public List<Map<String, Object>> listGradeSubjects(@RequestParam long academicTermId, @RequestParam int gradeLevel) {
        return this.subjectManagementService.listGradeSubjects(academicTermId, gradeLevel);
    }

    @PutMapping(value={"/grade-subjects"})
    public void saveGradeSubjects(@RequestBody Map<String, Object> request) {
        this.subjectManagementService.saveGradeSubjects(request);
    }

    @GetMapping(value={"/teachers/search"})
    public List<Map<String, Object>> searchTeachers(@RequestParam(required=false) String keyword) {
        return this.classManagementService.searchTeachers(keyword);
    }

    @GetMapping(value={"/menus"})
    public List<Map<String, Object>> listMenus() {
        return this.userManagementService.listMenus();
    }

    @GetMapping(value={"/class-subjects"})
    public List<Map<String, Object>> listClassSubjects(@RequestParam long academicTermId, @RequestParam long classId) {
        return this.classManagementService.listClassSubjects(academicTermId, classId);
    }

    @PutMapping(value={"/class-subjects/{id}/teacher"})
    public void saveClassTeacher(@PathVariable long id, @RequestBody Map<String, Object> request) {
        this.classManagementService.saveClassTeacher(id, ((Number)request.get("teacherUserId")).longValue());
    }

    @PostMapping(value={"/class-subjects"})
    public Map<String, Object> createClassSubject(@RequestBody Map<String, Object> request) {
        return this.classManagementService.createClassSubject(request);
    }

    @DeleteMapping(value={"/class-subjects/{id}"})
    public void deleteClassSubject(@PathVariable long id) {
        this.classManagementService.deleteClassSubject(id);
    }

    @PutMapping(value={"/class-subjects/teachers"})
    public void batchSaveClassTeachers(@RequestBody Map<String, Object> request) {
        this.classManagementService.batchSaveClassTeachers(request);
    }

    @GetMapping(value={"/students"})
    public PageResult<Map<String, Object>> listStudents(@RequestParam(required=false) Long classId, @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return this.studentManagementService.listStudents(classId, keyword, status, new PageRequest(page, size));
    }

    @GetMapping(value={"/students/{id}"})
    public Map<String, Object> getStudent(@PathVariable long id) {
        return this.studentManagementService.getStudent(id);
    }

    @PostMapping(value={"/students"})
    public Map<String, Object> createStudent(@RequestBody Map<String, Object> request) {
        return this.studentManagementService.createStudent(request);
    }

    @PutMapping(value={"/students/{id}"})
    public Map<String, Object> updateStudent(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.studentManagementService.updateStudent(id, request);
    }

    @DeleteMapping(value={"/students/{id}"})
    public void deleteStudent(@PathVariable long id) {
        this.studentManagementService.deleteStudent(id);
    }

    @PutMapping(value={"/students/{id}/parents"})
    public void bindParents(@PathVariable long id, @RequestBody Map<String, Object> request) {
        this.studentManagementService.bindParents(id, request);
    }

    @GetMapping(value={"/student-results"})
    public List<Map<String, Object>> listResults(@RequestParam(required=false) Long academicTermId, @RequestParam(required=false) Long classId, @RequestParam(required=false) Long studentId, @RequestParam(required=false) Long classSubjectId, @RequestParam(required=false) Long examTypeId) {
        return this.courseResultService.listStudentResults(academicTermId, classId, studentId, classSubjectId, examTypeId);
    }

    @GetMapping(value={"/teacher-score-entries"})
    public PageResult<Map<String, Object>> listTeacherScoreEntries(@RequestParam(required=false) Long academicTermId, @RequestParam(required=false) Long classId, @RequestParam(required=false) Long subjectId, @RequestParam(required=false) Long examTypeId, @RequestParam(required=false) String keyword, @RequestParam(required=false) String mode, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return this.courseResultService.listTeacherScoreEntries(academicTermId, classId, subjectId, examTypeId, keyword, mode, new PageRequest(page, size));
    }

    @GetMapping(value={"/student-results/{id}"})
    public Map<String, Object> getResult(@PathVariable long id) {
        return this.courseResultService.getResult(id);
    }

    @PostMapping(value={"/student-results"})
    public Map<String, Object> saveResult(@RequestBody Map<String, Object> request) {
        return this.courseResultService.saveStudentResult(request);
    }

    @PatchMapping(value={"/student-results/{id}/publish"})
    public Map<String, Object> publishResult(@PathVariable long id) {
        return this.courseResultService.publishStudentResult(id);
    }

    @DeleteMapping(value={"/student-results/{id}"})
    public void deleteResult(@PathVariable long id) {
        this.accessControlService.ensureTeacherCanWriteResults();
        this.accessControlService.ensureCanAccessResult(id);
        this.courseResultService.deleteStudentResult(id);
    }

    @GetMapping(value={"/student-overall-comments"})
    public List<Map<String, Object>> listOverallComments(@RequestParam(required=false) Long academicTermId, @RequestParam(required=false) Long classId, @RequestParam(required=false) Long studentId) {
        return this.overallCommentService.listOverallComments(academicTermId, classId, studentId);
    }

    @GetMapping(value={"/student-overall-comments/{id}"})
    public Map<String, Object> getOverallComment(@PathVariable long id) {
        return this.overallCommentService.getOverallComment(id);
    }

    @PostMapping(value={"/student-overall-comments"})
    public Map<String, Object> saveOverallComment(@RequestBody Map<String, Object> request) {
        return this.overallCommentService.saveOverallComment(request);
    }

    @GetMapping(value={"/transcripts"})
    public PageResult<Map<String, Object>> listTranscripts(@RequestParam(required=false) Long academicTermId, @RequestParam(required=false) Long classId, @RequestParam(required=false) Long studentId, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return this.transcriptService.listTranscripts(academicTermId, classId, studentId, new PageRequest(page, size));
    }

    @GetMapping(value={"/transcripts/{id}"})
    public Map<String, Object> getTranscript(@PathVariable long id) {
        return this.transcriptService.getTranscript(id);
    }

    @PostMapping(value={"/transcripts"})
    public Map<String, Object> generateTranscript(@RequestBody Map<String, Object> request) {
        return this.transcriptService.generateTranscript(request);
    }

    @PostMapping(value={"/transcripts/{id}/regenerate"})
    public Map<String, Object> regenerateTranscript(@PathVariable long id) {
        return this.transcriptService.regenerateTranscript(id);
    }

    @GetMapping(value={"/transcripts/{id}/download"})
    public ResponseEntity<Resource> downloadTranscript(@PathVariable long id) throws IOException {
        Path path = this.transcriptService.transcriptFilePath(id);
        if (!Files.exists(path, new LinkOption[0]) || !Files.isReadable(path)) {
            log.error("成绩单文件不存在或不可读: transcriptId={}, path={}", (Object)id, (Object)path.toAbsolutePath());
            throw new IllegalArgumentException("成绩单文件不存在或不可读");
        }
        FileSystemResource resource = new FileSystemResource(path);
        String fileName = path.getFileName().toString();
        log.info("下载成绩单: transcriptId={}, file={}, size={}", new Object[]{id, fileName, resource.contentLength()});
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"" + this.asciiFallbackFileName(fileName) + "\"; filename*=UTF-8''" + this.encodeDownloadFileName(fileName)).contentType(MediaType.APPLICATION_PDF).contentLength(resource.contentLength()).body(resource);
    }

    private String encodeDownloadFileName(String fileName) throws UnsupportedEncodingException {
        return URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
    }

    private String asciiFallbackFileName(String fileName) {
        String fallback = fileName == null ? "" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return fallback.isEmpty() ? "transcript.pdf" : fallback;
    }

    @PostMapping(value={"/comments/polish"})
    public Map<String, Object> polishComment(@RequestBody Map<String, Object> request) {
        String text = (String)request.get("text");
        String polishedText = this.commentPolishService.polish(text);
        return Collections.singletonMap("polishedText", polishedText);
    }

    @GetMapping(value={"/students/{id}/achievements"})
    public List<Map<String, Object>> listAchievements(@PathVariable long id, @RequestParam long academicTermId) {
        return this.achievementService.listAchievements(id, academicTermId);
    }

    @PostMapping(value={"/students/{id}/achievements"})
    public Map<String, Object> addAchievement(@PathVariable long id, @RequestBody Map<String, Object> request) {
        request.put("studentId", id);
        return this.achievementService.addAchievement(request);
    }

    @DeleteMapping(value={"/achievements/{id}"})
    public void deleteAchievement(@PathVariable long id) {
        this.achievementService.deleteAchievement(id);
    }

    @PutMapping(value={"/achievements/{id}"})
    public Map<String, Object> updateAchievement(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.achievementService.updateAchievement(id, request);
    }

    @GetMapping(value={"/exam-types"})
    public List<Map<String, Object>> listExamTypes() {
        return this.examTypeService.listExamTypes();
    }

    @PostMapping(value={"/exam-types"})
    public Map<String, Object> createExamType(@RequestBody Map<String, Object> request) {
        return this.examTypeService.createExamType(request);
    }

    @PutMapping(value={"/exam-types/{id}"})
    public Map<String, Object> updateExamType(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.examTypeService.updateExamType(id, request);
    }

    @GetMapping(value={"/honor-types"})
    public List<Map<String, Object>> listHonorTypes() {
        return this.honorTypeService.listAllHonorTypes();
    }

    @GetMapping(value={"/honor-types/enabled"})
    public List<Map<String, Object>> listEnabledHonorTypes() {
        return this.honorTypeService.listEnabledHonorTypes();
    }

    @PostMapping(value={"/honor-types"})
    public Map<String, Object> createHonorType(@RequestBody Map<String, Object> request) {
        return this.honorTypeService.createHonorType(request);
    }

    @PutMapping(value={"/honor-types/{id}"})
    public Map<String, Object> updateHonorType(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return this.honorTypeService.updateHonorType(id, request);
    }

    @GetMapping(value={"/enroll-grades"})
    public List<Map<String, Object>> listEnrollGrades() {
        return this.academicTermService.listEnrollGrades();
    }

    @PutMapping(value={"/enroll-grades"})
    public void saveEnrollGrades(@RequestBody Map<String, Object> request) {
        this.academicTermService.saveEnrollGrades(request);
    }

    // ==================== Excel 导入导出 ====================

    @GetMapping(value={"/course-results/export-template"})
    public ResponseEntity<Resource> exportCourseResultTemplate(
            @RequestParam long academicTermId,
            @RequestParam long classId,
            @RequestParam long subjectId,
            @RequestParam(required = false) Long examTypeId) throws IOException {
        Path path = this.gradeExcelService.exportCourseResultTemplate(academicTermId, classId, subjectId, examTypeId);
        return excelDownloadResponse(path);
    }

    @PostMapping(value={"/course-results/import"})
    public Map<String, Object> importCourseResults(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean enableAiPolish) {
        long evaluatorUserId = this.accessControlService.currentUserId();
        return this.gradeExcelService.importCourseResults(file, evaluatorUserId, enableAiPolish);
    }

    @GetMapping(value={"/head-teacher/export-template"})
    public ResponseEntity<Resource> exportHeadTeacherTemplate(
            @RequestParam long academicTermId,
            @RequestParam long classId) throws IOException {
        Path path = this.gradeExcelService.exportHeadTeacherTemplate(academicTermId, classId);
        return excelDownloadResponse(path);
    }

    @PostMapping(value={"/head-teacher/import"})
    public Map<String, Object> importHeadTeacherData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean enableAiPolish) {
        long evaluatorUserId = this.accessControlService.currentUserId();
        return this.gradeExcelService.importHeadTeacherData(file, evaluatorUserId, enableAiPolish);
    }

    private ResponseEntity<Resource> excelDownloadResponse(Path path) throws IOException {
        if (!Files.exists(path) || !Files.isReadable(path)) {
            log.error("Excel 文件不存在或不可读: path={}", path.toAbsolutePath());
            throw new IllegalArgumentException("文件生成失败，请稍后重试");
        }
        FileSystemResource resource = new FileSystemResource(path);
        String fileName = path.getFileName().toString();
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        return ResponseEntity.ok()
            .header("Content-Disposition",
                "attachment; filename=\"" + fileName.replaceAll("[^A-Za-z0-9._-]", "_") + "\"; filename*=UTF-8''" + encoded)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(resource.contentLength())
            .body(resource);
    }
}

