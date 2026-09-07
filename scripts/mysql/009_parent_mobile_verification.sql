SET NAMES utf8mb4;

CREATE TABLE school_mobile_verification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    h5_session_id BIGINT UNSIGNED NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    request_ip_hash CHAR(64) NOT NULL,
    code_hmac CHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    verify_attempts INT NOT NULL DEFAULT 0,
    sent_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    verified_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mobile_verification_mobile_time (mobile, sent_at),
    KEY idx_mobile_verification_session_time (h5_session_id, created_at),
    KEY idx_mobile_verification_ip_time (request_ip_hash, sent_at),
    KEY idx_mobile_verification_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE school_parent_agreement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_user_id BIGINT UNSIGNED NOT NULL,
    wechat_account_id BIGINT UNSIGNED NOT NULL,
    agreement_version VARCHAR(32) NOT NULL,
    agreed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parent_agreement (parent_user_id, agreement_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
