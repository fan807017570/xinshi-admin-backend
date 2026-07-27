/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.classsubject;

import com.xinshi.admin.domain.class_.SchoolClassRepository;
import com.xinshi.admin.domain.classsubject.ClassSubject;
import com.xinshi.admin.domain.classsubject.ClassSubjectRepository;
import com.xinshi.admin.domain.courseresult.CourseResultRepository;
import com.xinshi.admin.domain.gradesubject.GradeSubject;
import com.xinshi.admin.domain.gradesubject.GradeSubjectRepository;
import com.xinshi.admin.domain.shared.AuthorizationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ClassSubjectApplicationService {
    private final ClassSubjectRepository classSubjectRepository;
    private final GradeSubjectRepository gradeSubjectRepository;
    private final SchoolClassRepository classRepository;
    private final CourseResultRepository courseResultRepository;
    private final AuthorizationService authorizationService;

    public ClassSubjectApplicationService(ClassSubjectRepository classSubjectRepository, GradeSubjectRepository gradeSubjectRepository, SchoolClassRepository classRepository, CourseResultRepository courseResultRepository, AuthorizationService authorizationService) {
        this.classSubjectRepository = classSubjectRepository;
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.classRepository = classRepository;
        this.courseResultRepository = courseResultRepository;
        this.authorizationService = authorizationService;
    }

    public List<ClassSubject> listClassSubjects(long academicTermId, long classId) {
        this.authorizationService.ensureCanAccessClass(classId);
        this.ensureClassSubjects(academicTermId, classId);
        return this.classSubjectRepository.findByTermAndClass(academicTermId, classId);
    }

    public ClassSubject createClassSubject(long academicTermId, long classId, long subjectId, Long teacherUserId) {
        this.authorizationService.ensureHeadTeacherOrAdmin();
        this.authorizationService.ensureCanAccessClass(classId);
        if (this.classSubjectRepository.findByTermClassSubject(academicTermId, classId, subjectId).isPresent()) {
            throw new IllegalArgumentException("该班级已配置此课程");
        }
        ClassSubject cs = ClassSubject.create(academicTermId, classId, subjectId, teacherUserId, 1);
        return this.classSubjectRepository.save(cs);
    }

    public void deleteClassSubject(long id) {
        this.authorizationService.ensureHeadTeacherOrAdmin();
        ClassSubject cs = this.classSubjectRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("班级课程不存在"));
        this.authorizationService.ensureCanAccessClass(cs.getClassId());
        if (this.courseResultRepository.countByClassSubjectId(id) > 0) {
            throw new IllegalArgumentException("该课程已有成绩记录，无法删除。如需删除，请先清空相关成绩记录。");
        }
        this.classSubjectRepository.delete(id);
    }

    public void saveClassTeacher(long classSubjectId, long teacherUserId) {
        this.authorizationService.ensureCanAccessClassSubject(classSubjectId);
        this.classSubjectRepository.updateTeacher(classSubjectId, teacherUserId);
    }

    public void batchSaveClassTeachers(List<Long> classSubjectIds, List<Long> teacherUserIds) {
        for (int i = 0; i < classSubjectIds.size(); ++i) {
            this.saveClassTeacher(classSubjectIds.get(i), teacherUserIds.get(i));
        }
    }

    void ensureClassSubjects(long academicTermId, long classId) {
        if (this.classSubjectRepository.countByTermAndClass(academicTermId, classId) > 0) {
            return;
        }
        this.classRepository.findById(classId).ifPresent(clazz -> {
            List<GradeSubject> gradeSubjects = this.gradeSubjectRepository.findActiveByTermAndGrade(academicTermId, clazz.getGradeLevel());
            for (GradeSubject gs : gradeSubjects) {
                this.classSubjectRepository.save(ClassSubject.create(academicTermId, classId, gs.getSubjectId(), null, gs.getStatus()));
            }
        });
    }
}

