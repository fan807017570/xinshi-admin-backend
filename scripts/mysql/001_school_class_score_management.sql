-- MySQL 8.x initialization script for the school class score management system
-- This script is idempotent and will recreate the schema objects from scratch.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS school_transcript;
DROP TABLE IF EXISTS sys_user_session;
DROP TABLE IF EXISTS school_student_overall_comment;
DROP TABLE IF EXISTS school_student_course_result;
DROP TABLE IF EXISTS school_student_parent;
DROP TABLE IF EXISTS school_student;
DROP TABLE IF EXISTS school_class_subject;
DROP TABLE IF EXISTS school_grade_subject;
DROP TABLE IF EXISTS school_subject;
DROP TABLE IF EXISTS school_class;
DROP TABLE IF EXISTS school_academic_term;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    login_name VARCHAR(64) NOT NULL COMMENT 'Login account',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Password hash',
    real_name VARCHAR(64) NOT NULL COMMENT 'Display name',
    mobile VARCHAR(32) DEFAULT NULL COMMENT 'Mobile number',
    email VARCHAR(128) DEFAULT NULL COMMENT 'Email address',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'User status: 1-enabled, 0-disabled',
    last_login_at DATETIME DEFAULT NULL COMMENT 'Last login time',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_login_name (login_name),
    KEY idx_sys_user_mobile (mobile),
    KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System users';

CREATE TABLE sys_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    role_code VARCHAR(32) NOT NULL COMMENT 'Role code, e.g. SUPER_ADMIN',
    role_name VARCHAR(64) NOT NULL COMMENT 'Role name',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Role status: 1-enabled, 0-disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System roles';

CREATE TABLE sys_user_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT 'Role ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role_id (role_id),
    CONSTRAINT fk_sys_user_role_user_id FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User role mapping';

CREATE TABLE sys_user_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    token VARCHAR(64) NOT NULL COMMENT 'Session token',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    login_name VARCHAR(64) NOT NULL COMMENT 'Login account',
    real_name VARCHAR(64) NOT NULL COMMENT 'Real name',
    role_codes VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Role code list',
    menus VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'Menu code list',
    is_active TINYINT NOT NULL DEFAULT 1 COMMENT 'Active flag',
    last_access_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last access time',
    expires_at DATETIME NOT NULL COMMENT 'Expiration time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_session_token (token),
    KEY idx_sys_user_session_user_id (user_id),
    KEY idx_sys_user_session_expires_at (expires_at),
    CONSTRAINT fk_sys_user_session_user_id FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Login sessions';

CREATE TABLE school_academic_term (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    term_code VARCHAR(32) NOT NULL COMMENT 'Term code, e.g. 2025-2026-1',
    academic_year VARCHAR(16) NOT NULL COMMENT 'Academic year, e.g. 2025-2026',
    term_name VARCHAR(64) NOT NULL COMMENT 'Term name, e.g. 2025-2026 first term',
    start_date DATE DEFAULT NULL COMMENT 'Term start date',
    end_date DATE DEFAULT NULL COMMENT 'Term end date',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Term status: 1-active, 0-inactive',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_academic_term_code (term_code),
    KEY idx_school_academic_term_year (academic_year),
    KEY idx_school_academic_term_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Academic terms';

CREATE TABLE school_class (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    class_code VARCHAR(32) NOT NULL COMMENT 'Class code',
    class_name VARCHAR(64) NOT NULL COMMENT 'Class name, e.g. Grade 1 Class 1',
    grade_session VARCHAR(32) NOT NULL COMMENT 'Grade session, e.g. 2025 cohort',
    grade_level TINYINT NOT NULL COMMENT 'Grade level',
    head_teacher_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Head teacher user ID',
    is_key_class TINYINT NOT NULL DEFAULT 0 COMMENT 'Key class flag',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Class status: 1-active, 0-inactive',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_class_code (class_code),
    KEY idx_school_class_grade_level (grade_level),
    KEY idx_school_class_head_teacher_user_id (head_teacher_user_id),
    KEY idx_school_class_status (status),
    CONSTRAINT fk_school_class_head_teacher_user_id FOREIGN KEY (head_teacher_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Classes';

CREATE TABLE school_subject (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    subject_code VARCHAR(32) NOT NULL COMMENT 'Subject code',
    subject_name VARCHAR(64) NOT NULL COMMENT 'Subject name',
    min_score DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT 'Minimum valid score',
    max_score DECIMAL(8,2) NOT NULL DEFAULT 100 COMMENT 'Maximum valid score',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Subject status: 1-enabled, 0-disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_subject_code (subject_code),
    UNIQUE KEY uk_school_subject_name (subject_name),
    KEY idx_school_subject_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Subjects';

CREATE TABLE school_grade_subject (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    academic_term_id BIGINT UNSIGNED NOT NULL COMMENT 'Academic term ID',
    grade_level TINYINT NOT NULL COMMENT 'Grade level',
    subject_id BIGINT UNSIGNED NOT NULL COMMENT 'Subject ID',
    is_required TINYINT NOT NULL DEFAULT 1 COMMENT 'Required course flag',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Mapping status: 1-enabled, 0-disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_grade_subject (academic_term_id, grade_level, subject_id),
    KEY idx_school_grade_subject_term_level (academic_term_id, grade_level),
    KEY idx_school_grade_subject_subject_id (subject_id),
    CONSTRAINT fk_school_grade_subject_term_id FOREIGN KEY (academic_term_id) REFERENCES school_academic_term (id),
    CONSTRAINT fk_school_grade_subject_subject_id FOREIGN KEY (subject_id) REFERENCES school_subject (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Grade subject configuration';

CREATE TABLE school_class_subject (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    academic_term_id BIGINT UNSIGNED NOT NULL COMMENT 'Academic term ID',
    class_id BIGINT UNSIGNED NOT NULL COMMENT 'Class ID',
    subject_id BIGINT UNSIGNED NOT NULL COMMENT 'Subject ID',
    source_grade_subject_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Source grade subject config ID',
    teacher_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Teacher user ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Assignment status: 1-enabled, 0-disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_class_subject (academic_term_id, class_id, subject_id),
    KEY idx_school_class_subject_class_id (class_id),
    KEY idx_school_class_subject_subject_id (subject_id),
    KEY idx_school_class_subject_teacher_user_id (teacher_user_id),
    CONSTRAINT fk_school_class_subject_term_id FOREIGN KEY (academic_term_id) REFERENCES school_academic_term (id),
    CONSTRAINT fk_school_class_subject_class_id FOREIGN KEY (class_id) REFERENCES school_class (id),
    CONSTRAINT fk_school_class_subject_subject_id FOREIGN KEY (subject_id) REFERENCES school_subject (id),
    CONSTRAINT fk_school_class_subject_source_grade_subject_id FOREIGN KEY (source_grade_subject_id) REFERENCES school_grade_subject (id),
    CONSTRAINT fk_school_class_subject_teacher_user_id FOREIGN KEY (teacher_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Class subject assignment';

CREATE TABLE school_student (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    student_no VARCHAR(32) NOT NULL COMMENT 'Student number',
    student_name VARCHAR(64) NOT NULL COMMENT 'Student name',
    gender TINYINT NOT NULL DEFAULT 0 COMMENT 'Gender: 0-unknown, 1-male, 2-female',
    class_id BIGINT UNSIGNED NOT NULL COMMENT 'Class ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Student status: 1-active, 0-inactive',
    remark VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_student_no (student_no),
    KEY idx_school_student_class_id (class_id),
    KEY idx_school_student_status (status),
    CONSTRAINT fk_school_student_class_id FOREIGN KEY (class_id) REFERENCES school_class (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Students';

CREATE TABLE school_student_parent (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    student_id BIGINT UNSIGNED NOT NULL COMMENT 'Student ID',
    parent_user_id BIGINT UNSIGNED NOT NULL COMMENT 'Parent user ID',
    relation_type VARCHAR(16) NOT NULL COMMENT 'Relation type, e.g. father/mother/guardian',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT 'Primary guardian flag',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_student_parent (student_id, parent_user_id),
    KEY idx_school_student_parent_parent_user_id (parent_user_id),
    CONSTRAINT fk_school_student_parent_student_id FOREIGN KEY (student_id) REFERENCES school_student (id),
    CONSTRAINT fk_school_student_parent_parent_user_id FOREIGN KEY (parent_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Student parent relations';

CREATE TABLE school_student_course_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    academic_term_id BIGINT UNSIGNED NOT NULL COMMENT 'Academic term ID',
    class_subject_id BIGINT UNSIGNED NOT NULL COMMENT 'Class subject ID',
    student_id BIGINT UNSIGNED NOT NULL COMMENT 'Student ID',
    score DECIMAL(5,2) NOT NULL COMMENT 'Score',
    performance_comment VARCHAR(1000) DEFAULT NULL COMMENT 'Course performance comment',
    strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Course strengths',
    improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Course improvement points',
    evaluator_user_id BIGINT UNSIGNED NOT NULL COMMENT 'Evaluator user ID',
    evaluated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Evaluation time',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Result status: 1-draft, 2-published',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_student_course_result (academic_term_id, class_subject_id, student_id),
    KEY idx_school_student_course_result_student_id (student_id),
    KEY idx_school_student_course_result_class_subject_id (class_subject_id),
    KEY idx_school_student_course_result_evaluator_user_id (evaluator_user_id),
    CONSTRAINT fk_school_student_course_result_term_id FOREIGN KEY (academic_term_id) REFERENCES school_academic_term (id),
    CONSTRAINT fk_school_student_course_result_class_subject_id FOREIGN KEY (class_subject_id) REFERENCES school_class_subject (id),
    CONSTRAINT fk_school_student_course_result_student_id FOREIGN KEY (student_id) REFERENCES school_student (id),
    CONSTRAINT fk_school_student_course_result_evaluator_user_id FOREIGN KEY (evaluator_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Student course results';

CREATE TABLE school_student_overall_comment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    academic_term_id BIGINT UNSIGNED NOT NULL COMMENT 'Academic term ID',
    class_id BIGINT UNSIGNED NOT NULL COMMENT 'Class ID',
    student_id BIGINT UNSIGNED NOT NULL COMMENT 'Student ID',
    overall_comment VARCHAR(1000) NOT NULL COMMENT 'Overall comment',
    strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Overall strengths',
    improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Overall improvement points',
    evaluator_user_id BIGINT UNSIGNED NOT NULL COMMENT 'Evaluator user ID',
    evaluated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Evaluation time',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Comment status: 1-draft, 2-published',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_student_overall_comment (academic_term_id, student_id),
    KEY idx_school_student_overall_comment_class_id (class_id),
    KEY idx_school_student_overall_comment_evaluator_user_id (evaluator_user_id),
    CONSTRAINT fk_school_student_overall_comment_term_id FOREIGN KEY (academic_term_id) REFERENCES school_academic_term (id),
    CONSTRAINT fk_school_student_overall_comment_class_id FOREIGN KEY (class_id) REFERENCES school_class (id),
    CONSTRAINT fk_school_student_overall_comment_student_id FOREIGN KEY (student_id) REFERENCES school_student (id),
    CONSTRAINT fk_school_student_overall_comment_evaluator_user_id FOREIGN KEY (evaluator_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Student overall comments';

CREATE TABLE school_transcript (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    transcript_no VARCHAR(32) NOT NULL COMMENT 'Transcript number',
    academic_term_id BIGINT UNSIGNED NOT NULL COMMENT 'Academic term ID',
    class_id BIGINT UNSIGNED NOT NULL COMMENT 'Class ID',
    student_id BIGINT UNSIGNED NOT NULL COMMENT 'Student ID',
    pdf_file_name VARCHAR(255) DEFAULT NULL COMMENT 'PDF file name',
    pdf_file_path VARCHAR(512) DEFAULT NULL COMMENT 'PDF file path or storage URL',
    generated_by BIGINT UNSIGNED DEFAULT NULL COMMENT 'Generated by user ID',
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Generation time',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Transcript status: 1-generated, 0-invalidated',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_school_transcript_no (transcript_no),
    UNIQUE KEY uk_school_transcript_term_student (academic_term_id, student_id),
    KEY idx_school_transcript_class_id (class_id),
    KEY idx_school_transcript_generated_by (generated_by),
    CONSTRAINT fk_school_transcript_term_id FOREIGN KEY (academic_term_id) REFERENCES school_academic_term (id),
    CONSTRAINT fk_school_transcript_class_id FOREIGN KEY (class_id) REFERENCES school_class (id),
    CONSTRAINT fk_school_transcript_student_id FOREIGN KEY (student_id) REFERENCES school_student (id),
    CONSTRAINT fk_school_transcript_generated_by FOREIGN KEY (generated_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Generated transcripts';
