ALTER TABLE school_student_overall_comment
    ADD COLUMN teacher_name_snapshot VARCHAR(128) DEFAULT NULL COMMENT 'Teacher name snapshot shown to parents',
    ADD COLUMN published_at DATETIME DEFAULT NULL COMMENT 'First published time for parents',
    ADD KEY idx_overall_comment_h5 (student_id, academic_term_id, status, published_at);

ALTER TABLE school_student_achievement
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT 'Achievement status: 1-draft, 2-published',
    ADD COLUMN awarded_on DATE DEFAULT NULL COMMENT 'Award date',
    ADD COLUMN published_at DATETIME DEFAULT NULL COMMENT 'First published time for parents',
    ADD KEY idx_achievement_h5 (student_id, academic_term_id, status, awarded_on, id);
