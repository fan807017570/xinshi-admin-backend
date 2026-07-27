/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.honortype;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import com.xinshi.admin.domain.honortype.HonorType;
import com.xinshi.admin.domain.honortype.HonorTypeRepository;
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
public class HonorTypeService
extends SchoolBaseService {
    private static final Logger log = LoggerFactory.getLogger(HonorTypeService.class);
    private final HonorTypeRepository honorTypeRepository;
    private final AccessControlService accessControlService;

    public HonorTypeService(JdbcTemplate jdbcTemplate, HonorTypeRepository honorTypeRepository, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.honorTypeRepository = honorTypeRepository;
        this.accessControlService = accessControlService;
    }

    @PostConstruct
    public void ensureDefaultHonorTypes() {
        try {
            Integer count;
            Integer tableExists = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, new Object[]{"school_honor_type"});
            if (tableExists == null || tableExists == 0) {
                log.info("荣誉类型表不存在，正在创建...");
                this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS school_honor_type (  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',  honor_type_code VARCHAR(32) NOT NULL COMMENT 'Honor type code',  honor_type_name VARCHAR(64) NOT NULL COMMENT 'Honor type name',  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',  status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1-enabled, 0-disabled',  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,  PRIMARY KEY (id),  UNIQUE KEY uk_honor_type_code (honor_type_code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Honor types'");
            }
            if ((count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM school_honor_type", Integer.class)) != null && count == 0) {
                log.info("荣誉类型表为空，正在插入默认数据...");
                this.jdbcTemplate.update("INSERT INTO school_honor_type (honor_type_code, honor_type_name, sort_order, status) VALUES ('SPORTS','体育比赛',1,1),('SINGING','歌唱比赛',2,1),('SPEECH','演讲比赛',3,1),('CLASS_EVAL','班级评优',4,1),('SCHOOL_EVAL','学校评优',5,1),('SCHOLARSHIP','学校奖学金',6,1),('EXTRACURRICULAR','课外活动',7,1)");
            }
        }
        catch (Exception e) {
            log.error("初始化荣誉类型默认数据失败: {}", (Object)e.getMessage());
        }
    }

    public List<Map<String, Object>> listEnabledHonorTypes() {
        return this.honorTypeRepository.findAllEnabled().stream().map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> listAllHonorTypes() {
        this.accessControlService.ensureSuperAdmin();
        return this.honorTypeRepository.findAll().stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> createHonorType(Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        String honorTypeCode = this.requiredString(request, "honorTypeCode");
        String honorTypeName = this.requiredString(request, "honorTypeName");
        int sortOrder = this.optionalInteger(request, "sortOrder", 0);
        if (this.honorTypeRepository.findByCode(honorTypeCode.toUpperCase().trim()).isPresent()) {
            throw new IllegalArgumentException("荣誉类型编码已存在: " + honorTypeCode);
        }
        HonorType honorType = HonorType.create(honorTypeCode, honorTypeName, sortOrder);
        HonorType saved = this.honorTypeRepository.save(honorType);
        log.info("荣誉类型已创建: code={}, name={}, id={}", new Object[]{saved.getHonorTypeCode(), saved.getHonorTypeName(), saved.getId()});
        return this.toMap(saved);
    }

    public Map<String, Object> updateHonorType(long id, Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        HonorType honorType = this.honorTypeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("荣誉类型不存在: " + id));
        String honorTypeName = this.requiredString(request, "honorTypeName");
        int sortOrder = this.optionalInteger(request, "sortOrder", honorType.getSortOrder());
        int status = this.optionalInteger(request, "status", honorType.getStatus());
        honorType.updateInfo(honorTypeName, sortOrder, status);
        this.honorTypeRepository.update(honorType);
        log.info("荣誉类型已更新: id={}, name={}", (Object)id, (Object)honorTypeName);
        return this.toMap(this.honorTypeRepository.findById(id).orElseThrow(() -> new IllegalStateException("更新后荣誉类型未找到")));
    }

    private Map<String, Object> toMap(HonorType honorType) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", honorType.getId());
        map.put("honorTypeCode", honorType.getHonorTypeCode());
        map.put("honorTypeName", honorType.getHonorTypeName());
        map.put("sortOrder", honorType.getSortOrder());
        map.put("status", honorType.getStatus());
        map.put("createdAt", honorType.getCreatedAt());
        return map;
    }
}

