-- Login OTPs (only salted HMACs are stored) and sessions (only token hashes are stored).
CREATE TABLE otp_challenge
(
    id             BINARY(16)  NOT NULL,
    org_id         BIGINT      NOT NULL,
    mobile_hash    BINARY(32)  NOT NULL,
    purpose        VARCHAR(20) NOT NULL,
    salt           BINARY(16)  NOT NULL,
    code_hash      BINARY(32)  NOT NULL,
    client_ip_hash BINARY(32)  NULL,
    expires_at     DATETIME(6) NOT NULL,
    attempts       INT         NOT NULL,
    max_attempts   INT         NOT NULL,
    consumed_at    DATETIME(6) NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX otp_challenge_mobile_idx (mobile_hash, created_at),
    INDEX otp_challenge_ip_idx (client_ip_hash, created_at)
);

CREATE TABLE auth_session
(
    id                    BINARY(16)     NOT NULL,
    kind                  VARCHAR(20)    NOT NULL,
    org_id                BIGINT         NOT NULL,
    user_id               BINARY(16)     NULL,
    mobile_enc            VARBINARY(128) NOT NULL,
    access_token_hash     BINARY(32)     NOT NULL,
    refresh_token_hash    BINARY(32)     NOT NULL,
    previous_refresh_hash BINARY(32)     NULL,
    idle_expires_at       DATETIME(6)    NOT NULL,
    absolute_expires_at   DATETIME(6)    NOT NULL,
    last_seen_at          DATETIME(6)    NOT NULL,
    revoked_at            DATETIME(6)    NULL,
    revoke_reason         VARCHAR(40)    NULL,
    version               BIGINT         NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT auth_session_access_uk UNIQUE (access_token_hash),
    CONSTRAINT auth_session_refresh_uk UNIQUE (refresh_token_hash),
    INDEX auth_session_previous_refresh_idx (previous_refresh_hash),
    INDEX auth_session_user_idx (user_id),
    CONSTRAINT auth_session_kind_chk CHECK (kind IN ('USER', 'PRE_REGISTRATION', 'LA_APPLICANT', 'STAFF'))
);
