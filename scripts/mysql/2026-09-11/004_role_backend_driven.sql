-- ============================================================
-- Migration 004: Backend-driven role configuration
-- Adds sys_menu, sys_role_menu tables and extends sys_role
-- and sys_user_session to support configurable role permissions.
-- ============================================================

-- 1. Menu catalog table
CREATE TABLE IF NOT EXISTS sys_menu (
    menu_code VARCHAR(32) NOT NULL COMMENT 'Menu code',
    menu_label VARCHAR(64) NOT NULL COMMENT 'Menu display label',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1-enabled, 0-disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (menu_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System menu definitions';

-- 2. Role-menu mapping table
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    role_code VARCHAR(32) NOT NULL COMMENT 'Role code',
    menu_code VARCHAR(32) NOT NULL COMMENT 'Menu code',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_menu (role_code, menu_code),
    KEY idx_sys_role_menu_role_code (role_code),
    CONSTRAINT fk_sys_role_menu_role_code FOREIGN KEY (role_code) REFERENCES sys_role(role_code),
    CONSTRAINT fk_sys_role_menu_menu_code FOREIGN KEY (menu_code) REFERENCES sys_menu(menu_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role to menu assignments';

-- 3. Extend sys_role with landing page and protection flag
-- (MySQL does not support ADD COLUMN IF NOT EXISTS, so use prepared statement)
SET @db = (SELECT DATABASE());

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'landing_page';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN landing_page VARCHAR(64) DEFAULT NULL COMMENT ''Default landing page path'' AFTER role_name',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'is_protected';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN is_protected TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Protected role flag: 1-protected, cannot be assigned via API'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Extend sys_user_session with landing page
SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user_session' AND COLUMN_NAME = 'landing_page';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sys_user_session ADD COLUMN landing_page VARCHAR(64) DEFAULT NULL COMMENT ''Landing page path'' AFTER menus',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Seed menu data
INSERT INTO sys_menu (menu_code, menu_label, sort_order, status) VALUES
    ('config', '基础配置', 1, 1),
    ('users', '用户管理', 2, 1),
    ('classes', '班级管理', 3, 1),
    ('students', '学生管理', 4, 1),
    ('scores', '成绩管理', 5, 1),
    ('transcripts', '成绩单管理', 6, 1),
    ('parents', '家长管理', 7, 1)
ON DUPLICATE KEY UPDATE menu_label = VALUES(menu_label), sort_order = VALUES(sort_order), status = VALUES(status);

-- 6. Seed role-menu mappings (mirrors current hardcoded menusForRoles logic)
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

-- 7. Update sys_role with landing page and protection flags
UPDATE sys_role SET landing_page = NULL, is_protected = 1 WHERE role_code = 'SUPER_ADMIN';
UPDATE sys_role SET landing_page = '/students', is_protected = 0 WHERE role_code = 'HEAD_TEACHER';
UPDATE sys_role SET landing_page = '/scores', is_protected = 0 WHERE role_code = 'TEACHER';
UPDATE sys_role SET landing_page = '/parents', is_protected = 0 WHERE role_code = 'PARENT';
INSERT IGNORE INTO sys_role_menu (role_code, menu_code) VALUES ('HEAD_TEACHER', 'classes');
