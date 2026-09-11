-- Normalize legacy middle/high-school grade encoding to the system-wide 1-12 encoding.
-- Legacy mapping: 1-3 = junior grades 1-3, 4-6 = senior grades 1-3.
-- Target mapping: 7-9 = junior grades 1-3, 10-12 = senior grades 1-3.
--
-- Safety rules:
-- 1. A legacy level is migrated only when it is completely absent from enrollment-grade configuration.
-- 2. The target level must be an enabled enrollment grade.
-- 3. Conflicting grade-subject rows are merged before legacy rows are deleted.
-- 4. The script is idempotent and must be run in one MySQL session.

SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_grade_level_mapping;
CREATE TEMPORARY TABLE tmp_grade_level_mapping (
    legacy_level INT NOT NULL PRIMARY KEY,
    target_level INT NOT NULL UNIQUE
);

INSERT INTO tmp_grade_level_mapping (legacy_level, target_level)
VALUES (1, 7), (2, 8), (3, 9), (4, 10), (5, 11), (6, 12);

-- Pre-check: these are the rows eligible for migration.
SELECT c.id, c.class_code, c.class_name, c.grade_session,
       c.grade_level AS legacy_grade_level,
       mapping.target_level,
       target_grade.grade_name AS target_grade_name
FROM school_class c
JOIN tmp_grade_level_mapping mapping
  ON mapping.legacy_level = c.grade_level
JOIN school_enroll_grade target_grade
  ON target_grade.grade_level = mapping.target_level
 AND target_grade.status = 1
LEFT JOIN school_enroll_grade legacy_grade
  ON legacy_grade.grade_level = mapping.legacy_level
WHERE c.is_deleted = 0
  AND legacy_grade.grade_level IS NULL
ORDER BY c.id;

START TRANSACTION;

-- When target grade-subject rows already exist, keep the target rows and
-- repoint class-subject foreign keys before removing duplicate legacy rows.
UPDATE school_class_subject class_subject
JOIN school_grade_subject legacy_subject
  ON legacy_subject.id = class_subject.source_grade_subject_id
JOIN tmp_grade_level_mapping mapping
  ON mapping.legacy_level = legacy_subject.grade_level
JOIN school_enroll_grade target_grade
  ON target_grade.grade_level = mapping.target_level
 AND target_grade.status = 1
LEFT JOIN school_enroll_grade legacy_grade
  ON legacy_grade.grade_level = mapping.legacy_level
JOIN school_grade_subject target_subject
  ON target_subject.academic_term_id = legacy_subject.academic_term_id
 AND target_subject.grade_level = mapping.target_level
 AND target_subject.subject_id = legacy_subject.subject_id
SET class_subject.source_grade_subject_id = target_subject.id,
    class_subject.updated_at = CURRENT_TIMESTAMP
WHERE legacy_grade.grade_level IS NULL;

DELETE legacy_subject
FROM school_grade_subject legacy_subject
JOIN tmp_grade_level_mapping mapping
  ON mapping.legacy_level = legacy_subject.grade_level
JOIN school_enroll_grade target_grade
  ON target_grade.grade_level = mapping.target_level
 AND target_grade.status = 1
LEFT JOIN school_enroll_grade legacy_grade
  ON legacy_grade.grade_level = mapping.legacy_level
JOIN school_grade_subject target_subject
  ON target_subject.academic_term_id = legacy_subject.academic_term_id
 AND target_subject.grade_level = mapping.target_level
 AND target_subject.subject_id = legacy_subject.subject_id
WHERE legacy_grade.grade_level IS NULL;

-- Non-conflicting grade-subject rows keep their IDs, so existing references remain valid.
UPDATE school_grade_subject grade_subject
JOIN tmp_grade_level_mapping mapping
  ON mapping.legacy_level = grade_subject.grade_level
JOIN school_enroll_grade target_grade
  ON target_grade.grade_level = mapping.target_level
 AND target_grade.status = 1
LEFT JOIN school_enroll_grade legacy_grade
  ON legacy_grade.grade_level = mapping.legacy_level
SET grade_subject.grade_level = mapping.target_level,
    grade_subject.updated_at = CURRENT_TIMESTAMP
WHERE legacy_grade.grade_level IS NULL;

UPDATE school_class c
JOIN tmp_grade_level_mapping mapping
  ON mapping.legacy_level = c.grade_level
JOIN school_enroll_grade target_grade
  ON target_grade.grade_level = mapping.target_level
 AND target_grade.status = 1
LEFT JOIN school_enroll_grade legacy_grade
  ON legacy_grade.grade_level = mapping.legacy_level
SET c.grade_level = mapping.target_level,
    c.updated_at = CURRENT_TIMESTAMP
WHERE legacy_grade.grade_level IS NULL;

-- grade_session stores the numeric cohort; the UI appends “届”.
UPDATE school_class
SET grade_session = LEFT(TRIM(grade_session), CHAR_LENGTH(TRIM(grade_session)) - 1),
    updated_at = CURRENT_TIMESTAMP
WHERE RIGHT(TRIM(grade_session), 1) = '届';

COMMIT;

-- Post-check: both result sets should be empty.
SELECT c.id, c.class_name, c.grade_session, c.grade_level
FROM school_class c
JOIN tmp_grade_level_mapping mapping
  ON mapping.legacy_level = c.grade_level
LEFT JOIN school_enroll_grade legacy_grade
  ON legacy_grade.grade_level = c.grade_level
WHERE c.is_deleted = 0
  AND legacy_grade.grade_level IS NULL;

SELECT c.id, c.class_name, c.grade_level
FROM school_class c
LEFT JOIN school_enroll_grade grade_config
  ON grade_config.grade_level = c.grade_level
 AND grade_config.status = 1
WHERE c.is_deleted = 0
  AND grade_config.grade_level IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_grade_level_mapping;
