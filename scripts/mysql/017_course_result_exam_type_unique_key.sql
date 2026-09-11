-- 成绩唯一键增量迁移：同一学生、课程可按考试类型保存多条成绩。
-- 脚本发现重复业务键时立即停止，不会自动删除或合并业务数据。

SET NAMES utf8mb4;

SELECT academic_term_id, class_subject_id, student_id, exam_type_id, COUNT(*) AS duplicate_count
FROM school_student_course_result
GROUP BY academic_term_id, class_subject_id, student_id, exam_type_id
HAVING COUNT(*) > 1;

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_course_result_unique_key$$
CREATE PROCEDURE migrate_course_result_unique_key()
BEGIN
    DECLARE duplicate_rows BIGINT DEFAULT 0;
    DECLARE exam_type_column_count INT DEFAULT 0;
    DECLARE unique_index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO exam_type_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'school_student_course_result'
      AND COLUMN_NAME = 'exam_type_id';

    IF exam_type_column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration stopped: exam_type_id column does not exist';
    END IF;

    SELECT COUNT(*) INTO duplicate_rows
    FROM (
        SELECT 1
        FROM school_student_course_result
        GROUP BY academic_term_id, class_subject_id, student_id, exam_type_id
        HAVING COUNT(*) > 1
    ) duplicated_business_keys;

    IF duplicate_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration stopped: duplicate course result business keys exist';
    END IF;

    SELECT COUNT(*) INTO unique_index_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'school_student_course_result'
      AND INDEX_NAME = 'uk_school_student_course_result';

    IF unique_index_count > 0 THEN
        ALTER TABLE school_student_course_result
            DROP INDEX uk_school_student_course_result;
    END IF;

    ALTER TABLE school_student_course_result
        ADD UNIQUE KEY uk_school_student_course_result
            (academic_term_id, class_subject_id, student_id, exam_type_id);
END$$

CALL migrate_course_result_unique_key()$$
DROP PROCEDURE migrate_course_result_unique_key$$

DELIMITER ;

