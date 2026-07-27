/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.examtype;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.domain.examtype.ExamType;
import com.xinshi.admin.domain.examtype.ExamTypeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExamTypeService
extends SchoolBaseService {
    private static final Logger log = LoggerFactory.getLogger(ExamTypeService.class);
    private final ExamTypeRepository examTypeRepository;
    private final AccessControlService accessControlService;

    public ExamTypeService(JdbcTemplate jdbcTemplate, ExamTypeRepository examTypeRepository, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.examTypeRepository = examTypeRepository;
        this.accessControlService = accessControlService;
    }

    @PostConstruct
    public void ensureDefaultExamTypes() {
        try {
            Integer count;
            Integer tableExists = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, new Object[]{"school_exam_type"});
            if (tableExists == null || tableExists == 0) {
                log.info("考试类型表不存在，正在创建...");
                this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS school_exam_type (  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',  exam_type_code VARCHAR(32) NOT NULL COMMENT 'Exam type code',  exam_type_name VARCHAR(64) NOT NULL COMMENT 'Exam type name',  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',  status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1-enabled, 0-disabled',  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,  PRIMARY KEY (id),  UNIQUE KEY uk_exam_type_code (exam_type_code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Exam types'");
            }
            if ((count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_exam_type", Integer.class)) != null && count == 0) {
                log.info("考试类型表为空，正在插入默认数据...");
                this.jdbcTemplate.update("INSERT INTO school_exam_type (exam_type_code, exam_type_name, sort_order, status) VALUES ('MIDTERM','期中考试',1,1),('FINAL','期末考试',2,1),('MONTHLY','月考',3,1),('JOINT','联考',4,1),('MOCK','模拟考试',5,1),('QUIZ','课堂测验',6,1)");
            }
        }
        catch (Exception e) {
            log.error("初始化考试类型默认数据失败: {}", (Object)e.getMessage());
        }
    }

    public List<Map<String, Object>> listExamTypes() {
        return this.examTypeRepository.findAll().stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> createExamType(Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        String examTypeCode = this.requiredString(request, "examTypeCode");
        String examTypeName = this.requiredString(request, "examTypeName");
        int sortOrder = this.optionalInteger(request, "sortOrder", 0);
        if (this.examTypeRepository.findByCode(examTypeCode.toUpperCase().trim()).isPresent()) {
            throw new IllegalArgumentException("考试类型编码已存在: " + examTypeCode);
        }
        ExamType examType = ExamType.create(examTypeCode, examTypeName, sortOrder);
        ExamType saved = this.examTypeRepository.save(examType);
        log.info("考试类型已创建: code={}, name={}, id={}", new Object[]{saved.getExamTypeCode(), saved.getExamTypeName(), saved.getId()});
        return this.toMap(saved);
    }

    public Map<String, Object> updateExamType(long id, Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        ExamType examType = this.examTypeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("考试类型不存在: " + id));
        String examTypeName = this.requiredString(request, "examTypeName");
        int sortOrder = this.optionalInteger(request, "sortOrder", examType.getSortOrder());
        int status = this.optionalInteger(request, "status", examType.getStatus());
        examType.updateInfo(examTypeName, sortOrder, status);
        this.examTypeRepository.update(examType);
        log.info("考试类型已更新: id={}, name={}", (Object)id, (Object)examTypeName);
        return this.toMap(this.examTypeRepository.findById(id).orElseThrow(() -> new IllegalStateException("更新后考试类型未找到")));
    }

    private Map<String, Object> toMap(ExamType examType) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", examType.getId());
        map.put("examTypeCode", examType.getExamTypeCode());
        map.put("examTypeName", examType.getExamTypeName());
        map.put("sortOrder", examType.getSortOrder());
        map.put("status", examType.getStatus());
        map.put("createdAt", examType.getCreatedAt());
        return map;
    }
}

