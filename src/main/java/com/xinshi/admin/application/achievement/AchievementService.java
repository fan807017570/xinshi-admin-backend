/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.achievement;

import com.xinshi.admin.application.h5.ParentContentLifecycleService;
import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.domain.achievement.AchievementRepository;
import com.xinshi.admin.domain.achievement.StudentAchievement;
import com.xinshi.admin.domain.honortype.HonorType;
import com.xinshi.admin.domain.honortype.HonorTypeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AchievementService {
    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);
    private final AchievementRepository achievementRepository;
    private final HonorTypeRepository honorTypeRepository;
    private final AccessControlService accessControlService;
    private final ParentContentLifecycleService lifecycleService;

    public AchievementService(
            AchievementRepository achievementRepository,
            HonorTypeRepository honorTypeRepository,
            AccessControlService accessControlService,
            ParentContentLifecycleService lifecycleService) {
        this.achievementRepository = achievementRepository;
        this.honorTypeRepository = honorTypeRepository;
        this.accessControlService = accessControlService;
        this.lifecycleService = lifecycleService;
    }

    public List<Map<String, Object>> listAchievements(long studentId, long academicTermId) {
        this.accessControlService.ensureCanAccessStudent(studentId);
        List<StudentAchievement> achievements = this.achievementRepository.findByTermAndStudent(academicTermId, studentId);
        return achievements.stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> addAchievement(Map<String, Object> request) {
        long academicTermId = this.requiredLong(request, "academicTermId");
        long studentId = this.requiredLong(request, "studentId");
        String achievementText = this.requiredString(request, "achievementText");
        int sortOrder = this.optionalInt(request, "sortOrder", 0);
        Long honorTypeId = this.optionalLong(request, "honorTypeId");
        long id = this.lifecycleService.createAchievementDraft(
                academicTermId, studentId, honorTypeId, achievementText, sortOrder);
        log.info("Student achievement created, id={}, honorTypeId={}", id, honorTypeId);
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("academicTermId", academicTermId);
        result.put("studentId", studentId);
        result.put("honorTypeId", honorTypeId);
        result.put("honorTypeName", this.resolveHonorTypeName(honorTypeId));
        result.put("achievementText", achievementText.trim());
        result.put("sortOrder", Math.max(sortOrder, 0));
        return result;
    }

    public void deleteAchievement(long id) {
        this.lifecycleService.deleteAchievement(id);
    }

    public Map<String, Object> updateAchievement(long id, Map<String, Object> request) {
        String achievementText = this.requiredString(request, "achievementText");
        int sortOrder = this.optionalInt(request, "sortOrder", 0);
        Long honorTypeId = this.optionalLong(request, "honorTypeId");
        this.lifecycleService.updateAchievementDraft(
                id,
                honorTypeId,
                achievementText,
                sortOrder);
        log.info("Student achievement updated, id={}, honorTypeId={}", id, honorTypeId);
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("honorTypeId", honorTypeId);
        result.put("honorTypeName", this.resolveHonorTypeName(honorTypeId));
        result.put("achievementText", achievementText);
        result.put("sortOrder", Math.max(sortOrder, 0));
        return result;
    }

    private Map<String, Object> toMap(StudentAchievement a) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", a.getId());
        m.put("academicTermId", a.getAcademicTermId());
        m.put("studentId", a.getStudentId());
        m.put("honorTypeId", a.getHonorTypeId());
        m.put("honorTypeName", this.resolveHonorTypeName(a.getHonorTypeId()));
        m.put("achievementText", a.getAchievementText());
        m.put("sortOrder", a.getSortOrder());
        return m;
    }

    private String resolveHonorTypeName(Long honorTypeId) {
        if (honorTypeId == null) {
            return null;
        }
        Optional<HonorType> honorType = this.honorTypeRepository.findById(honorTypeId);
        return honorType.map(HonorType::getHonorTypeName).orElse(null);
    }

    private long requiredLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        if (value instanceof String && !((String)value).isEmpty()) {
            return Long.parseLong(((String)value).trim());
        }
        throw new IllegalArgumentException("缺少必要参数: " + key);
    }

    private Long optionalLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        if (value instanceof String && !((String)value).isEmpty()) {
            return Long.parseLong(((String)value).trim());
        }
        return null;
    }

    private String requiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value instanceof String && ((String)value).trim().isEmpty()) {
            throw new IllegalArgumentException("缺少必要参数: " + key);
        }
        return value.toString().trim();
    }

    private int optionalInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number)value).intValue();
        }
        if (value instanceof String && !((String)value).isEmpty()) {
            return Integer.parseInt(((String)value).trim());
        }
        return defaultValue;
    }
}
