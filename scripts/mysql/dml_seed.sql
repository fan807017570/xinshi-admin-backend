-- ============================================================
-- DML: Seed data for the school class score management system
-- ============================================================
-- Run after ddl_init.sql.
-- This script is idempotent: DELETEs clean old seed rows before
-- INSERTs, and INSERTs use ON DUPLICATE KEY UPDATE where possible.
-- Merged from:
--   002_school_class_score_management_seed.sql
--   004_role_backend_driven.sql (DML portion)
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------
-- Clean up existing seed data (reverse dependency order)
-- ------------------------------
DELETE FROM school_student_achievement WHERE id IN (1);
DELETE FROM school_transcript WHERE id IN (1);
DELETE FROM school_honor_type WHERE id IN (1, 2, 3, 4, 5, 6, 7);
DELETE FROM school_exam_type WHERE id IN (1, 2, 3, 4, 5, 6);
DELETE FROM school_student_overall_comment WHERE id IN (1);
DELETE FROM school_student_course_result WHERE id IN (1, 2);
DELETE FROM school_student_parent WHERE id IN (1);
DELETE FROM school_student WHERE id IN (1);
DELETE FROM school_class_subject WHERE id IN (1, 2, 3);
DELETE FROM school_grade_subject WHERE id IN (1, 2, 3);
DELETE FROM school_subject WHERE id IN (1, 2, 3);
DELETE FROM school_class WHERE id IN (1);
DELETE FROM school_academic_term WHERE id IN (1);
DELETE FROM sys_role_menu WHERE role_code IN ('SUPER_ADMIN', 'HEAD_TEACHER', 'TEACHER', 'PARENT');
DELETE FROM sys_menu WHERE menu_code IN ('config', 'users', 'classes', 'students', 'scores', 'transcripts', 'parents');
DELETE FROM sys_user_role WHERE id IN (1, 2, 3, 4);
DELETE FROM sys_role WHERE id IN (1, 2, 3, 4);
DELETE FROM sys_user WHERE id IN (1, 2, 3, 4);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- System seed data
-- ============================================================

-- Roles
INSERT INTO sys_role (id, role_code, role_name, status)
VALUES
    (1, 'SUPER_ADMIN', '超级管理员', 1),
    (2, 'HEAD_TEACHER', '班主任', 1),
    (3, 'TEACHER', '任课教师', 1),
    (4, 'PARENT', '学生家长', 1);

-- Users
INSERT INTO sys_user (id, login_name, password_hash, real_name, mobile, email, status)
VALUES
    (1, 'admin', '{bcrypt}admin123', '系统管理员', '13800000001', 'admin@xinshi.edu.cn', 1),
    (2, 'head_teacher_zhang', '{bcrypt}head123', '张老师', '13800000002', 'zhang@example.com', 1),
    (3, 'teacher_li', '{bcrypt}teacher123', '李老师', '13800000003', 'li@example.com', 1),
    (4, 'parent_wang', '{bcrypt}parent123', '王家长', '13800000004', 'parent@example.com', 1);

-- User-role mappings
INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
    (1, 1, 1),
    (2, 2, 2),
    (3, 3, 3),
    (4, 4, 4);

-- Menus
INSERT INTO sys_menu (menu_code, menu_label, sort_order, status) VALUES
    ('config', '基础配置', 1, 1),
    ('users', '用户管理', 2, 1),
    ('classes', '班级管理', 3, 1),
    ('students', '学生管理', 4, 1),
    ('scores', '成绩管理', 5, 1),
    ('transcripts', '成绩单管理', 6, 1),
    ('parents', '家长管理', 7, 1)
ON DUPLICATE KEY UPDATE menu_label = VALUES(menu_label), sort_order = VALUES(sort_order), status = VALUES(status);

-- Role-menu mappings
INSERT INTO sys_role_menu (role_code, menu_code) VALUES
    ('SUPER_ADMIN', 'config'),
    ('SUPER_ADMIN', 'users'),
    ('SUPER_ADMIN', 'classes'),
    ('SUPER_ADMIN', 'students'),
    ('SUPER_ADMIN', 'scores'),
    ('SUPER_ADMIN', 'transcripts'),
    ('SUPER_ADMIN', 'parents'),
    ('HEAD_TEACHER', 'classes'),
    ('HEAD_TEACHER', 'students'),
    ('HEAD_TEACHER', 'scores'),
    ('HEAD_TEACHER', 'transcripts'),
    ('HEAD_TEACHER', 'parents'),
    ('TEACHER', 'scores'),
    ('PARENT', 'parents'),
    ('PARENT', 'transcripts')
ON DUPLICATE KEY UPDATE role_code = VALUES(role_code), menu_code = VALUES(menu_code);

-- Update role landing pages and protection flags
UPDATE sys_role SET landing_page = NULL, is_protected = 1 WHERE role_code = 'SUPER_ADMIN';
UPDATE sys_role SET landing_page = '/students', is_protected = 0 WHERE role_code = 'HEAD_TEACHER';
UPDATE sys_role SET landing_page = '/scores', is_protected = 0 WHERE role_code = 'TEACHER';
UPDATE sys_role SET landing_page = '/parents', is_protected = 0 WHERE role_code = 'PARENT';

-- Ensure HEAD_TEACHER has classes menu (idempotent)
INSERT IGNORE INTO sys_role_menu (role_code, menu_code) VALUES ('HEAD_TEACHER', 'classes');

-- ============================================================
-- Academic seed data
-- ============================================================

INSERT INTO school_academic_term (id, term_code, academic_year, term_name, start_date, end_date, status)
VALUES
    (1, '2025-2026-1', '2025-2026', '2025-2026 学年第一学期', '2025-09-01', '2026-01-31', 1);

INSERT INTO school_class (id, class_code, class_name, grade_session, grade_level, head_teacher_user_id, is_key_class, course_combination, foreign_language, ph_or_hi, elective_two, status)
VALUES
    (1, 'G2025-1-1', '2025届一年级1班', '2025届', 1, 2, 1, NULL, NULL, NULL, NULL, 1);

INSERT INTO school_subject (id, subject_code, subject_name, min_score, max_score, status)
VALUES
    (1, 'SUBJ-CHINESE', '语文', 0, 100, 1),
    (2, 'SUBJ-MATH', '数学', 0, 100, 1),
    (3, 'SUBJ-ENGLISH', '英语', 0, 100, 1);

INSERT INTO school_grade_subject (id, academic_term_id, grade_level, subject_id, is_required, sort_order, status)
VALUES
    (1, 1, 1, 1, 1, 1, 1),
    (2, 1, 1, 2, 1, 2, 1),
    (3, 1, 1, 3, 1, 3, 1);

INSERT INTO school_class_subject (id, academic_term_id, class_id, subject_id, source_grade_subject_id, teacher_user_id, status)
VALUES
    (1, 1, 1, 1, 1, 3, 1),
    (2, 1, 1, 2, 2, 3, 1),
    (3, 1, 1, 3, 3, 3, 1);

INSERT INTO school_honor_type (id, honor_type_code, honor_type_name, sort_order, status) VALUES
    (1, 'SPORTS', '体育比赛', 1, 1),
    (2, 'SINGING', '歌唱比赛', 2, 1),
    (3, 'SPEECH', '演讲比赛', 3, 1),
    (4, 'CLASS_EVAL', '班级评优', 4, 1),
    (5, 'SCHOOL_EVAL', '学校评优', 5, 1),
    (6, 'SCHOLARSHIP', '学校奖学金', 6, 1),
    (7, 'EXTRACURRICULAR', '课外活动', 7, 1);

INSERT INTO school_exam_type (id, exam_type_code, exam_type_name, sort_order, status) VALUES
    (1, 'MIDTERM', '期中考试', 1, 1),
    (2, 'FINAL', '期末考试', 2, 1),
    (3, 'MONTHLY', '月考', 3, 1),
    (4, 'JOINT', '联考', 4, 1),
    (5, 'MOCK', '模拟考试', 5, 1),
    (6, 'QUIZ', '课堂测验', 6, 1);

INSERT INTO school_student (id, student_no, student_name, gender, class_id, status, remark)
VALUES
    (1, 'S20250001', '小明', 1, 1, 1, '示例学生');

INSERT INTO school_student_parent (id, student_id, parent_user_id, relation_type, is_primary)
VALUES
    (1, 1, 4, 'father', 1);

INSERT INTO school_student_course_result (id, academic_term_id, class_subject_id, student_id, score, performance_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status)
VALUES
    (1, 1, 1, 1, 95.50, '课堂表现积极，作业完成度高。', '听讲专注，书写工整。', '表达可以更加主动。', 3, CURRENT_TIMESTAMP, 2),
    (2, 1, 2, 1, 93.00, '计算能力较强，思路清晰。', '思路清楚，分析能力较强。', '审题还可以更细致。', 3, CURRENT_TIMESTAMP, 2);

INSERT INTO school_student_overall_comment (id, academic_term_id, class_id, student_id, overall_comment, strengths, improvement_points, evaluator_user_id, evaluated_at, status)
VALUES
    (1, 1, 1, 1, '学习态度认真，综合表现良好，建议继续保持积极主动的学习习惯。', '课堂纪律好，合作意识强。', '课外阅读和表达能力还可以继续加强。', 2, CURRENT_TIMESTAMP, 2);

INSERT INTO school_transcript (id, transcript_no, academic_term_id, class_id, student_id, pdf_file_name, pdf_file_path, generated_by, generated_at, status)
VALUES
    (1, 'TR-2025-2026-1-0001', 1, 1, 1, 'TR-2025-2026-1-0001.pdf', '/files/transcripts/TR-2025-2026-1-0001.pdf', 2, CURRENT_TIMESTAMP, 1);
