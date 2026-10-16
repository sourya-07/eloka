-- P8 staff review: Eko staff accounts and roles, review history (maker-checker), correction
-- requests, and the case assignment on the application.
CREATE TABLE staff_user
(
    id           BINARY(16)   NOT NULL,
    email        VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    mobile_hash  BINARY(32)   NULL,
    status       VARCHAR(20)  NOT NULL,
    version      BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT staff_user_email_uk UNIQUE (email),
    CONSTRAINT staff_user_mobile_uk UNIQUE (mobile_hash),
    CONSTRAINT staff_user_status_chk CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE staff_role_grant
(
    staff_id BINARY(16)  NOT NULL,
    role     VARCHAR(20) NOT NULL,
    PRIMARY KEY (staff_id, role),
    CONSTRAINT staff_role_grant_staff_fk FOREIGN KEY (staff_id) REFERENCES staff_user (id),
    CONSTRAINT staff_role_grant_role_chk CHECK (role IN ('REVIEWER', 'APPROVER', 'AUDITOR', 'ADMIN'))
);

CREATE TABLE review_action
(
    id             BINARY(16)   NOT NULL,
    application_id BINARY(16)   NOT NULL,
    staff_id       BINARY(16)   NOT NULL,
    action         VARCHAR(30)  NOT NULL,
    decision       VARCHAR(20)  NULL,
    reason_code    VARCHAR(40)  NULL,
    note           VARCHAR(1000) NULL,
    target_ref     VARCHAR(40)  NULL,
    occurred_at    DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT review_action_application_fk FOREIGN KEY (application_id) REFERENCES onboarding_application (id),
    INDEX review_action_application_idx (application_id, occurred_at)
);

CREATE TABLE correction_request
(
    id             BINARY(16)    NOT NULL,
    application_id BINARY(16)    NOT NULL,
    staff_id       BINARY(16)    NOT NULL,
    step_keys      JSON          NOT NULL,
    reason_code    VARCHAR(40)   NOT NULL,
    message        VARCHAR(1000) NOT NULL,
    due_at         DATETIME(6)   NOT NULL,
    resolved_at    DATETIME(6)   NULL,
    version        BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT correction_request_application_fk FOREIGN KEY (application_id) REFERENCES onboarding_application (id),
    INDEX correction_request_application_idx (application_id, created_at)
);

ALTER TABLE onboarding_application
    ADD COLUMN assigned_staff_id BINARY(16)  NULL AFTER pan_hash,
    ADD COLUMN correction_due_at DATETIME(6) NULL AFTER submitted_at,
    ADD INDEX onboarding_application_assignee_idx (assigned_staff_id, status);

ALTER TABLE application_document
    ADD COLUMN reviewed_by BINARY(16)  NULL AFTER reject_reason_code,
    ADD COLUMN reviewed_at DATETIME(6) NULL AFTER reviewed_by;

ALTER TABLE verification
    ADD COLUMN decided_by BINARY(16) NULL AFTER failure_code;

-- S21: audit trail of one application
CREATE INDEX audit_event_target_idx ON audit_event (target_type, target_id, occurred_at);
