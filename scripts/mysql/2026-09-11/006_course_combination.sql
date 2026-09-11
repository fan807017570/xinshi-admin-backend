-- ============================================================
-- Migration: Add course combination fields to school_class
-- ============================================================
-- Run once per environment. If columns already exist, the
-- statements will fail harmlessly — skip and continue.
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE school_class
ADD COLUMN course_combination VARCHAR(32) DEFAULT NULL
COMMENT '组合编码，如 EN-PH-CH-BI，NULL 表示不区分选课';

ALTER TABLE school_class
ADD COLUMN foreign_language VARCHAR(16) DEFAULT NULL
COMMENT '外语选择：EN=英语, JA=日语';

ALTER TABLE school_class
ADD COLUMN ph_or_hi VARCHAR(16) DEFAULT NULL
COMMENT '物理/历史选择：PH=物理, HI=历史';

ALTER TABLE school_class
ADD COLUMN elective_two VARCHAR(64) DEFAULT NULL
COMMENT '四选二科目，逗号分隔排序，如 CH,BI';
