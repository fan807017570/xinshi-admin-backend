
package com.xinshi.admin.application.academicterm;

import com.xinshi.admin.application.school.AccessControlService;
import com.xinshi.admin.application.school.SchoolBaseService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AcademicTermService
extends SchoolBaseService {
    private final AccessControlService accessControlService;
    private static final Map<Integer, String> GRADE_LEVEL_NAMES = new LinkedHashMap<Integer, String>();

    public AcademicTermService(JdbcTemplate jdbcTemplate, AccessControlService accessControlService) {
        super(jdbcTemplate);
        this.accessControlService = accessControlService;
    }

    @PostConstruct
    public void ensureEnrollGradeTable() {
        Integer count = (Integer)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, new Object[]{"school_enroll_grade"});
        if (count == null || count == 0) {
            this.jdbcTemplate.execute("CREATE TABLE school_enroll_grade (  id BIGINT AUTO_INCREMENT PRIMARY KEY,  grade_level INT NOT NULL UNIQUE COMMENT '年级(1-12)',  grade_name VARCHAR(32) NOT NULL COMMENT '年级中文名',  status INT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学校招收年级配置'");
        }
    }

    public List<Map<String, Object>> listAcademicTerms() {
        return this.jdbcTemplate.queryForList("SELECT id, term_code AS termCode, academic_year AS academicYear, term_name AS termName, start_date AS startDate, end_date AS endDate, status, created_at AS createdAt FROM school_academic_term ORDER BY id DESC");
    }

    public Map<String, Object> createAcademicTerm(Map<String, Object> request) {
        String termCode = this.requiredString(request, "termCode");
        String academicYear = this.requiredString(request, "academicYear");
        String termName = this.requiredString(request, "termName");
        LocalDate startDate = this.optionalLocalDate(request.get("startDate"));
        LocalDate endDate = this.optionalLocalDate(request.get("endDate"));
        Integer status = this.optionalInteger(request, "status", 1);
        if (this.exists("SELECT COUNT(1) FROM school_academic_term WHERE term_code = ?", termCode) > 0) {
            throw new IllegalArgumentException("学期编码已存在");
        }
        long id = this.insert("school_academic_term", "INSERT INTO school_academic_term (term_code, academic_year, term_name, start_date, end_date, status) VALUES (?, ?, ?, ?, ?, ?)", termCode, academicYear, termName, startDate, endDate, status);
        return this.first(this.jdbcTemplate.queryForList("SELECT id, term_code AS termCode, academic_year AS academicYear, term_name AS termName, start_date AS startDate, end_date AS endDate, status, created_at AS createdAt FROM school_academic_term WHERE id = ?", new Object[]{id}));
    }

    public Map<String, Object> updateAcademicTerm(long id, Map<String, Object> request) {
        String termName = this.optionalString(request, "termName", null);
        LocalDate startDate = request.containsKey("startDate") ? this.optionalLocalDate(request.get("startDate")) : null;
        LocalDate endDate = request.containsKey("endDate") ? this.optionalLocalDate(request.get("endDate")) : null;
        Integer status = request.containsKey("status") ? this.optionalInteger(request, "status", null) : null;
        ArrayList<Object> args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE school_academic_term SET ");
        boolean first = true;
        if (termName != null) {
            sql.append("term_name = ?");
            args.add(termName);
            first = false;
        }
        if (request.containsKey("startDate")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("start_date = ?");
            args.add(startDate);
            first = false;
        }
        if (request.containsKey("endDate")) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("end_date = ?");
            args.add(endDate);
            first = false;
        }
        if (status != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("status = ?");
            args.add(status);
        }
        if (args.isEmpty()) {
            return this.getAcademicTerm(id);
        }
        sql.append(", updated_at = CURRENT_TIMESTAMP WHERE id = ?");
        args.add(id);
        this.jdbcTemplate.update(sql.toString(), args.toArray());
        return this.getAcademicTerm(id);
    }

    public Map<String, Object> getAcademicTerm(long id) {
        return this.first(this.jdbcTemplate.queryForList("SELECT id, term_code AS termCode, academic_year AS academicYear, term_name AS termName, start_date AS startDate, end_date AS endDate, status, created_at AS createdAt FROM school_academic_term WHERE id = ?", new Object[]{id}));
    }

    public List<Map<String, Object>> listEnrollGrades() {
        List<Map<String, Object>> existing = this.jdbcTemplate.queryForList("SELECT grade_level AS gradeLevel, grade_name AS gradeName, status FROM school_enroll_grade ORDER BY grade_level");
        Set<Integer> existingLevels = existing.stream().map(row -> this.optionalInteger(row, "gradeLevel", 0)).collect(Collectors.toSet());
        ArrayList<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map.Entry<Integer, String> entry : GRADE_LEVEL_NAMES.entrySet()) {
            Integer level = entry.getKey();
            LinkedHashMap<String, Object> grade = new LinkedHashMap<String, Object>();
            grade.put("gradeLevel", level);
            grade.put("gradeName", entry.getValue());
            grade.put("status", existingLevels.contains(level) ? 1 : 0);
            result.add(grade);
        }
        return result;
    }

    public void saveEnrollGrades(Map<String, Object> request) {
        this.accessControlService.ensureSuperAdmin();
        List<Integer> gradeLevels = ((List<?>)request.get("gradeLevels")).stream().map(obj -> ((Number)obj).intValue()).collect(Collectors.toList());
        for (Integer level : gradeLevels) {
            if (level >= 1 && level <= 12) continue;
            throw new IllegalArgumentException("年级值必须在 1-12 之间");
        }
        this.jdbcTemplate.update("DELETE FROM school_enroll_grade");
        for (Integer level : gradeLevels) {
            String gradeName = GRADE_LEVEL_NAMES.getOrDefault(level, level + "年级");
            this.insert("school_enroll_grade", "INSERT INTO school_enroll_grade (grade_level, grade_name, status) VALUES (?, ?, 1)", level, gradeName);
        }
    }

    static {
        GRADE_LEVEL_NAMES.put(1, "小学一年级");
        GRADE_LEVEL_NAMES.put(2, "小学二年级");
        GRADE_LEVEL_NAMES.put(3, "小学三年级");
        GRADE_LEVEL_NAMES.put(4, "小学四年级");
        GRADE_LEVEL_NAMES.put(5, "小学五年级");
        GRADE_LEVEL_NAMES.put(6, "小学六年级");
        GRADE_LEVEL_NAMES.put(7, "初中一年级");
        GRADE_LEVEL_NAMES.put(8, "初中二年级");
        GRADE_LEVEL_NAMES.put(9, "初中三年级");
        GRADE_LEVEL_NAMES.put(10, "高中一年级");
        GRADE_LEVEL_NAMES.put(11, "高中二年级");
        GRADE_LEVEL_NAMES.put(12, "高中三年级");
    }
}

