-- Local Admin agreements (P7): one row per rendered version, e-sign requests per version.
-- A signed agreement is never changed; new content supersedes it with a new version.
CREATE TABLE agreement
(
    id               BINARY(16)   NOT NULL,
    application_id   BINARY(16)   NOT NULL,
    public_id        VARCHAR(32)  NOT NULL,
    version_no       INT          NOT NULL,
    template_version VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    content_sha256   BINARY(32)   NOT NULL,
    document_id      BINARY(16)   NOT NULL,
    signed_at        DATETIME(6)  NULL,
    signer_name      VARCHAR(150) NULL,
    version          BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT agreement_public_uk UNIQUE (public_id),
    CONSTRAINT agreement_version_uk UNIQUE (application_id, version_no),
    CONSTRAINT agreement_status_chk CHECK (status IN ('READY', 'SIGNING', 'SIGNED', 'FAILED', 'EXPIRED', 'SUPERSEDED'))
);

CREATE TABLE esign_request
(
    id                   BINARY(16)   NOT NULL,
    agreement_id         BINARY(16)   NOT NULL,
    public_id            VARCHAR(32)  NOT NULL,
    provider_pipe        INT          NOT NULL,
    provider_document_id VARCHAR(100) NOT NULL,
    signing_url          VARCHAR(500) NULL,
    status               VARCHAR(20)  NOT NULL,
    expires_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT esign_request_public_uk UNIQUE (public_id),
    CONSTRAINT esign_request_provider_uk UNIQUE (provider_document_id),
    CONSTRAINT esign_request_agreement_fk FOREIGN KEY (agreement_id) REFERENCES agreement (id)
);
