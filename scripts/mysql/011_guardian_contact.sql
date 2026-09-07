SET NAMES utf8mb4;

CREATE TABLE school_student_guardian_contact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id BIGINT UNSIGNED NOT NULL,
    mobile_hmac CHAR(64) NOT NULL,
    mobile_masked VARCHAR(32) NOT NULL,
    source VARCHAR(16) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    verified_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_guardian_contact_student_mobile (student_id, mobile_hmac),
    KEY idx_guardian_contact_mobile (mobile_hmac, status),
    KEY idx_guardian_contact_student (student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
