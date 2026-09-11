SET NAMES utf8mb4;

CREATE TABLE school_internal_request_nonce (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_id VARCHAR(64) NOT NULL,
    nonce_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_internal_nonce (service_id, nonce_hash),
    KEY idx_internal_nonce_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
