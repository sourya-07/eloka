-- Local Admin onboarding (P7): applicants, applications, steps and their data.
-- Design: docs/onboarding/DATABASE_DESIGN.md and docs/onboarding/P7_APPLICANT_API.md.
-- "Unique while active" rules use stored generated columns that are NULL once the row is
-- no longer active (MySQL allows many NULLs in a unique index).

CREATE TABLE applicant
(
    id            BINARY(16)     NOT NULL,
    mobile_enc    VARBINARY(128) NOT NULL,
    mobile_hash   BINARY(32)     NOT NULL,
    mobile_masked VARCHAR(10)    NOT NULL,
    version       BIGINT         NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT applicant_mobile_uk UNIQUE (mobile_hash)
);

CREATE TABLE onboarding_application
(
    id                         BINARY(16)  NOT NULL,
    public_id                  VARCHAR(32) NOT NULL,
    applicant_id               BINARY(16)  NOT NULL,
    status                     VARCHAR(30) NOT NULL,
    applicant_category         VARCHAR(20) NULL,
    product_type               VARCHAR(20) NULL,
    entity_type                VARCHAR(30) NULL,
    pan_hash                   BINARY(32)  NULL,
    last_applicant_activity_at DATETIME(6) NOT NULL,
    expires_at                 DATETIME(6) NOT NULL,
    status_changed_at          DATETIME(6) NOT NULL,
    submitted_at               DATETIME(6) NULL,
    closed_at                  DATETIME(6) NULL,
    version                    BIGINT      NOT NULL,
    created_at                 DATETIME(6) NOT NULL,
    updated_at                 DATETIME(6) NOT NULL,
    -- one non-terminal application per applicant (resume instead of a second one)
    active_applicant_key       BINARY(16) AS (IF(status IN ('REJECTED', 'WITHDRAWN', 'EXPIRED', 'ACTIVE'), NULL,
                                                 applicant_id)) STORED,
    -- one live application per business PAN (an ACTIVE tenant keeps its PAN)
    active_pan_key             BINARY(32) AS (IF(status IN ('REJECTED', 'WITHDRAWN', 'EXPIRED'), NULL, pan_hash)) STORED,
    PRIMARY KEY (id),
    CONSTRAINT onboarding_application_public_uk UNIQUE (public_id),
    CONSTRAINT onboarding_application_active_applicant_uk UNIQUE (active_applicant_key),
    CONSTRAINT onboarding_application_active_pan_uk UNIQUE (active_pan_key),
    CONSTRAINT onboarding_application_applicant_fk FOREIGN KEY (applicant_id) REFERENCES applicant (id),
    INDEX onboarding_application_queue_idx (status, submitted_at),
    INDEX onboarding_application_expiry_idx (status, last_applicant_activity_at),
    CONSTRAINT onboarding_application_status_chk CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'UNDER_REVIEW',
        'CORRECTION_REQUIRED', 'ON_HOLD', 'APPROVED', 'REJECTED', 'PROVISIONING', 'PROVISIONING_FAILED',
        'GO_LIVE_PENDING', 'ACTIVE', 'WITHDRAWN', 'EXPIRED'))
);

CREATE TABLE onboarding_step
(
    id                 BINARY(16)   NOT NULL,
    application_id     BINARY(16)   NOT NULL,
    step_key           VARCHAR(30)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    required           BOOLEAN      NOT NULL,
    attempt_count      INT          NOT NULL,
    max_attempts       INT          NOT NULL,
    last_error_code    VARCHAR(40)  NULL,
    draft_data         JSON         NULL,
    summary            JSON         NULL,
    reopen_reason_code VARCHAR(40)  NULL,
    reviewer_message   VARCHAR(500) NULL,
    completed_at       DATETIME(6)  NULL,
    version            BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT onboarding_step_uk UNIQUE (application_id, step_key),
    CONSTRAINT onboarding_step_application_fk FOREIGN KEY (application_id) REFERENCES onboarding_application (id),
    CONSTRAINT onboarding_step_status_chk CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'PENDING_EXTERNAL',
        'COMPLETED', 'FAILED', 'BLOCKED', 'SKIPPED', 'LOCKED', 'REOPENED', 'NOT_APPLICABLE'))
);

-- Verified business data of an application (one row each). Personal data: _enc + _hash + _masked.
CREATE TABLE business_profile
(
    id                    BINARY(16)     NOT NULL,
    application_id        BINARY(16)     NOT NULL,
    legal_name            VARCHAR(150)   NULL,
    company_type          VARCHAR(40)    NULL,
    signatory_name        VARCHAR(150)   NULL,
    contact_mobile_enc    VARBINARY(128) NULL,
    contact_mobile_masked VARCHAR(10)    NULL,
    alternate_mobile_enc  VARBINARY(128) NULL,
    address_line1         VARCHAR(200)   NULL,
    address_line2         VARCHAR(200)   NULL,
    district              VARCHAR(100)   NULL,
    state                 VARCHAR(100)   NULL,
    pincode               CHAR(6)        NULL,
    gstin                 VARCHAR(15)    NULL,
    cin                   VARCHAR(21)    NULL,
    signature_document_id BINARY(16)     NULL,
    pan_enc               VARBINARY(128) NULL,
    pan_hash              BINARY(32)     NULL,
    pan_masked            VARCHAR(10)    NULL,
    pan_holder_type       CHAR(1)        NULL,
    name_as_per_pan       VARCHAR(150)   NULL,
    email_enc             VARBINARY(512) NULL,
    email_hash            BINARY(32)     NULL,
    email_masked          VARCHAR(254)   NULL,
    kyc_name              VARCHAR(150)   NULL,
    aadhaar_masked        VARCHAR(14)    NULL,
    photo_document_id     BINARY(16)     NULL,
    photo_latlong         VARCHAR(60)    NULL,
    version               BIGINT         NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT business_profile_application_uk UNIQUE (application_id),
    CONSTRAINT business_profile_application_fk FOREIGN KEY (application_id) REFERENCES onboarding_application (id)
);

CREATE TABLE consent_record
(
    id              BINARY(16)  NOT NULL,
    application_id  BINARY(16)  NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    terms_version   VARCHAR(20) NOT NULL,
    purposes        JSON        NOT NULL,
    accepted_at     DATETIME(6) NOT NULL,
    version         BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT consent_record_application_fk FOREIGN KEY (application_id) REFERENCES onboarding_application (id),
    INDEX consent_record_application_idx (application_id, accepted_at)
);

-- Files of an application (content lives encrypted in stored_document).
CREATE TABLE application_document
(
    id                 BINARY(16)   NOT NULL,
    application_id     BINARY(16)   NOT NULL,
    public_id          VARCHAR(32)  NOT NULL,
    doc_type           VARCHAR(40)  NOT NULL,
    stored_document_id BINARY(16)   NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    sha256             BINARY(32)   NOT NULL,
    file_name          VARCHAR(200) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT       NOT NULL,
    reject_reason_code VARCHAR(40)  NULL,
    superseded_at      DATETIME(6)  NULL,
    version            BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT application_document_public_uk UNIQUE (public_id),
    CONSTRAINT application_document_application_fk FOREIGN KEY (application_id) REFERENCES onboarding_application (id),
    INDEX application_document_type_idx (application_id, doc_type),
    CONSTRAINT application_document_status_chk CHECK (status IN ('UPLOAD_PENDING', 'SCANNING', 'QUARANTINED',
        'PENDING_REVIEW', 'VERIFIED', 'REJECTED', 'SUPERSEDED', 'ABANDONED')),
    CONSTRAINT application_document_size_chk CHECK (size_bytes BETWEEN 1 AND 10485760)
);

-- Applicant-safe history (A30 timeline). The full trail is in audit_event.
CREATE TABLE application_event
(
    id             BINARY(16)  NOT NULL,
    application_id BINARY(16)  NOT NULL,
    event_type     VARCHAR(40) NOT NULL,
    step_key       VARCHAR(30) NULL,
    from_status    VARCHAR(30) NULL,
    to_status      VARCHAR(30) NULL,
    actor          VARCHAR(20) NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT application_event_application_fk FOREIGN KEY (application_id) REFERENCES onboarding_application (id),
    INDEX application_event_timeline_idx (application_id, occurred_at)
);

-- Idempotency-Key records for side-effect POSTs (24 h). response_status NULL = still running.
CREATE TABLE idempotency_record
(
    id              BINARY(16)  NOT NULL,
    scope_id        BINARY(16)  NOT NULL,
    idem_key        VARCHAR(80) NOT NULL,
    request_hash    BINARY(32)  NOT NULL,
    response_status INT         NULL,
    response_etag   VARCHAR(40) NULL,
    response_body   MEDIUMTEXT  NULL,
    expires_at      DATETIME(6) NOT NULL,
    version         BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idempotency_record_uk UNIQUE (scope_id, idem_key),
    INDEX idempotency_record_expiry_idx (expires_at)
);
