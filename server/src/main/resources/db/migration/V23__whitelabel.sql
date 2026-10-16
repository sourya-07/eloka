-- White label (P7): sub-domain reservations during onboarding and the branding draft.
-- A label is unique while RESERVED or BOUND (generated key is NULL once RELEASED).
CREATE TABLE domain_reservation
(
    id             BINARY(16)   NOT NULL,
    application_id BINARY(16)   NOT NULL,
    label          VARCHAR(63)  NOT NULL,
    hostname       VARCHAR(253) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    reserved_at    DATETIME(6)  NOT NULL,
    released_at    DATETIME(6)  NULL,
    version        BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    active_hostname_key VARCHAR(253) AS (IF(status IN ('RESERVED', 'BOUND'), hostname, NULL)) STORED,
    PRIMARY KEY (id),
    CONSTRAINT domain_reservation_active_uk UNIQUE (active_hostname_key),
    INDEX domain_reservation_application_idx (application_id, status),
    CONSTRAINT domain_reservation_status_chk CHECK (status IN ('RESERVED', 'BOUND', 'RELEASED'))
);

CREATE TABLE branding_draft
(
    id                BINARY(16)   NOT NULL,
    application_id    BINARY(16)   NOT NULL,
    app_name          VARCHAR(60)  NOT NULL,
    logo_document_id  BINARY(16)   NULL,
    theme             JSON         NULL,
    version           BIGINT       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT branding_draft_application_uk UNIQUE (application_id)
);

-- Labels nobody may take (platform hosts, brand names, confusing words).
CREATE TABLE reserved_label
(
    label VARCHAR(63) NOT NULL,
    PRIMARY KEY (label)
);

INSERT INTO reserved_label (label)
VALUES ('www'), ('api'), ('app'), ('admin'), ('staff'), ('partners'), ('partner'), ('mail'), ('smtp'), ('ftp'),
       ('connect'), ('eloka'), ('eko'), ('ekoindia'), ('support'), ('help'), ('status'), ('static'), ('cdn'),
       ('assets'), ('login'), ('auth'), ('secure'), ('pay'), ('bank'), ('test'), ('demo'), ('staging'), ('dev'),
       ('localhost'), ('root'), ('system'), ('dashboard'), ('docs'), ('blog');
