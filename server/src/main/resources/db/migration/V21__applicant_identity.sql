-- Local Admin applicants (P7) log in at platform level (org id 0, see IdentityApi.PLATFORM_ORG_ID).
-- Step-up re-auth for sensitive applicant actions: a short-lived, single-use token per session.
CREATE TABLE reauth_grant
(
    id         BINARY(16)  NOT NULL,
    session_id BINARY(16)  NOT NULL,
    token_hash BINARY(32)  NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    version    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT reauth_grant_token_uk UNIQUE (token_hash),
    INDEX reauth_grant_session_idx (session_id)
);
