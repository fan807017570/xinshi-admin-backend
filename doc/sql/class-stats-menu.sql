-- ============================================================================
-- 班级成绩统计 — 菜单权限 DML
-- 版本: v1.0 | 日期: 2026-08-04
-- 说明: 为 classStats 菜单新增 sys_menu 记录 + 各角色授权
-- 注意: sort_order 值需根据现有数据调整，当前假设:
--       scores=60, classStats=70, transcripts=80
--       请在执行前核对: SELECT menu_code, sort_order FROM sys_menu ORDER BY sort_order;
-- ============================================================================

-- Step 1: 新增菜单项
-- 如果 sys_menu 已有 classStats 记录则跳过（幂等）
INSERT IGNORE INTO sys_menu (menu_code, menu_label, sort_order, status)
VALUES ('classStats', '班级成绩统计', 70, 1);

-- Step 2: 为各角色授权（sys_role_menu）
-- SUPER_ADMIN: 全部菜单
INSERT IGNORE INTO sys_role_menu (role_code, menu_code)
SELECT 'SUPER_ADMIN', 'classStats'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN' AND status = 1);

-- HEAD_TEACHER: 班主任需要班级统计
INSERT IGNORE INTO sys_role_menu (role_code, menu_code)
SELECT 'HEAD_TEACHER', 'classStats'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'HEAD_TEACHER' AND status = 1);

-- TEACHER: 任课老师可查看任教班级统计
INSERT IGNORE INTO sys_role_menu (role_code, menu_code)
SELECT 'TEACHER', 'classStats'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'TEACHER' AND status = 1);

-- PARENT 不授权 classStats（家长只看个人成绩单）

-- ============================================================================
-- 验证
-- ============================================================================

-- 确认菜单已插入:
-- SELECT * FROM sys_menu WHERE menu_code = 'classStats';

-- 确认角色授权:
-- SELECT rm.role_code, rm.menu_code, m.menu_label, m.sort_order
-- FROM sys_role_menu rm
-- JOIN sys_menu m ON m.menu_code = rm.menu_code AND m.status = 1
-- WHERE rm.menu_code = 'classStats';
