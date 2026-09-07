SET NAMES utf8mb4;

CREATE TABLE school_wechat_app (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appid VARCHAR(64) NOT NULL,
    app_secret_ref VARCHAR(255) NOT NULL,
    oauth_callback_url VARCHAR(512) NOT NULL,
    h5_entry_url VARCHAR(512) NOT NULL,
    score_query_enabled TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_app_appid (appid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE school_wechat_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appid VARCHAR(64) NOT NULL,
    openid_hmac CHAR(64) NOT NULL,
    openid_ciphertext VARBINARY(1024) NOT NULL,
    nickname_ciphertext VARBINARY(2048) DEFAULT NULL,
    parent_user_id BIGINT UNSIGNED DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    nickname_updated_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_account_appid_openid_hmac (appid, openid_hmac),
    KEY idx_wechat_account_parent (parent_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE school_h5_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    csrf_token_hash CHAR(64) NOT NULL,
    wechat_account_id BIGINT UNSIGNED NOT NULL,
    parent_user_id BIGINT UNSIGNED DEFAULT NULL,
    appid VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    identity_fail_count INT NOT NULL DEFAULT 0,
    identity_locked_until DATETIME DEFAULT NULL,
    last_access_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_h5_session_token_hash (token_hash),
    KEY idx_h5_session_wechat (wechat_account_id),
    KEY idx_h5_session_parent (parent_user_id),
    KEY idx_h5_session_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE school_wechat_oauth_state (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    state_hash CHAR(64) DEFAULT NULL,
    login_confirmation_token_hash CHAR(64) NOT NULL,
    preauth_cookie_hash CHAR(64) NOT NULL,
    appid VARCHAR(64) NOT NULL,
    return_path VARCHAR(255) NOT NULL,
    confirmation_status VARCHAR(16) NOT NULL,
    expires_at DATETIME NOT NULL,
    confirmed_at DATETIME DEFAULT NULL,
    consumed_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_state_hash (state_hash),
    UNIQUE KEY uk_oauth_confirmation_token (login_confirmation_token_hash),
    KEY idx_oauth_state_appid_created (appid, created_at),
    KEY idx_oauth_state_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
