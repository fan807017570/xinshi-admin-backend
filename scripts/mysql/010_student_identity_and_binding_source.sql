ALTER TABLE school_student
    ADD COLUMN id_card_last4_hmac CHAR(64) DEFAULT NULL
    COMMENT 'HMAC of student number and ID card last four characters';

ALTER TABLE school_student_parent
    ADD COLUMN binding_source VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    ADD COLUMN verified_at DATETIME DEFAULT NULL;
