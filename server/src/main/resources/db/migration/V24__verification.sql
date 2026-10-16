-- Verification attempts of Local Admin applications (P7). One row per attempt; results hold
-- masked values only. Bank account numbers are stored encrypted with a blind index.
CREATE TABLE verification
(
    id               BINARY(16)   NOT NULL,
    application_id   BINARY(16)   NOT NULL,
    kind             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    subject_hash     BINARY(32)   NULL,
    provider_ref     VARCHAR(100) NULL,
    result           JSON         NULL,
    name_match_score INT          NULL,
    failure_code     VARCHAR(40)  NULL,
    version          BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX verification_application_idx (application_id, kind, created_at),
    INDEX verification_pending_idx (status, updated_at),
    CONSTRAINT verification_kind_chk CHECK (kind IN ('PAN', 'KYC', 'BANK', 'EMAIL')),
    CONSTRAINT verification_status_chk CHECK (status IN ('AWAITING_PROVIDER', 'SUCCEEDED', 'FAILED', 'MANUAL_REVIEW',
        'EXPIRED'))
);

CREATE TABLE bank_account
(
    id                  BINARY(16)     NOT NULL,
    application_id      BINARY(16)     NOT NULL,
    public_id           VARCHAR(32)    NOT NULL,
    bank_code           VARCHAR(20)    NOT NULL,
    bank_id             VARCHAR(20)    NULL,
    ifsc                CHAR(11)       NOT NULL,
    account_enc         VARBINARY(128) NOT NULL,
    account_hash        BINARY(32)     NOT NULL,
    account_masked      VARCHAR(20)    NOT NULL,
    proof_document_id   BINARY(16)     NULL,
    verification_id     BINARY(16)     NOT NULL,
    beneficiary_name    VARCHAR(150)   NULL,
    version             BIGINT         NOT NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT bank_account_public_uk UNIQUE (public_id),
    INDEX bank_account_application_idx (application_id, created_at),
    -- reuse across tenants is flagged for review, not blocked (OQ-B6)
    INDEX bank_account_reuse_idx (account_hash, ifsc)
);
