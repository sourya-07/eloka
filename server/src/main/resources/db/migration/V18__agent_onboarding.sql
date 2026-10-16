-- Agent onboarding: plan + progress per user, and the KYC data the steps collect.
-- PAN, phone numbers and bank account are encrypted (with keyed hashes for uniqueness);
-- images are documents. The Aadhaar number is never stored.
CREATE TABLE agent_onboarding
(
    id           BINARY(16)  NOT NULL,
    org_id       BIGINT      NOT NULL,
    user_id      BINARY(16)  NOT NULL,
    completed_at DATETIME(6) NULL,
    version      BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT agent_onboarding_user_uk UNIQUE (user_id),
    CONSTRAINT agent_onboarding_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id),
    INDEX agent_onboarding_org_idx (org_id, completed_at)
);

CREATE TABLE agent_onboarding_step
(
    id            BINARY(16)  NOT NULL,
    onboarding_id BINARY(16)  NOT NULL,
    step          VARCHAR(20) NOT NULL,
    sort_order    INT         NOT NULL,
    completed_at  DATETIME(6) NULL,
    version       BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT agent_onboarding_step_uk UNIQUE (onboarding_id, step),
    CONSTRAINT agent_onboarding_step_fk FOREIGN KEY (onboarding_id) REFERENCES agent_onboarding (id),
    CONSTRAINT agent_onboarding_step_chk CHECK (step IN ('AADHAAR', 'DIGILOCKER', 'PAN', 'SELFIE', 'BUSINESS', 'BANK',
                                                         'SECRET_PIN', 'AGREEMENT'))
);

CREATE TABLE agent_kyc
(
    id                        BINARY(16)     NOT NULL,
    org_id                    BIGINT         NOT NULL,
    user_id                   BINARY(16)     NOT NULL,
    pan_enc                   VARBINARY(128) NULL,
    pan_hash                  BINARY(32)     NULL,
    pan_masked                VARCHAR(10)    NULL,
    pan_document_id           BINARY(16)     NULL,
    aadhaar_front_document_id BINARY(16)     NULL,
    aadhaar_back_document_id  BINARY(16)     NULL,
    selfie_document_id        BINARY(16)     NULL,
    latlong                   VARCHAR(60)    NULL,
    company_type              VARCHAR(40)    NULL,
    authorized_signatory      VARCHAR(150)   NULL,
    contact_mobile_enc        VARBINARY(128) NULL,
    alternate_mobile_enc      VARBINARY(128) NULL,
    bank_code                 VARCHAR(20)    NULL,
    bank_id                   VARCHAR(20)    NULL,
    bank_ifsc                 VARCHAR(11)    NULL,
    bank_account_enc          VARBINARY(128) NULL,
    bank_account_hash         BINARY(32)     NULL,
    bank_account_masked       VARCHAR(24)    NULL,
    bank_proof_document_id    BINARY(16)     NULL,
    digilocker_request_id     VARCHAR(80)    NULL,
    agreement_document_id     VARCHAR(80)    NULL,
    agreement_signed_at       DATETIME(6)    NULL,
    version                   BIGINT         NOT NULL,
    created_at                DATETIME(6)    NOT NULL,
    updated_at                DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT agent_kyc_user_uk UNIQUE (user_id),
    CONSTRAINT agent_kyc_org_pan_uk UNIQUE (org_id, pan_hash),
    CONSTRAINT agent_kyc_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id)
);
