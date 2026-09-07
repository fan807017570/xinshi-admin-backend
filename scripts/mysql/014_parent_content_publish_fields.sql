ALTER TABLE school_student_overall_comment
    ADD COLUMN teacher_name_snapshot VARCHAR(128) DEFAULT NULL,
    ADD COLUMN published_at DATETIME DEFAULT NULL,
    ADD KEY idx_overall_comment_h5 (student_id, academic_term_id, status, published_at);

ALTER TABLE school_student_achievement
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1,
    ADD COLUMN awarded_on DATE DEFAULT NULL,
    ADD COLUMN published_at DATETIME DEFAULT NULL,
    ADD KEY idx_achievement_h5 (student_id, academic_term_id, status, awarded_on, id);
