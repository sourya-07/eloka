-- Tenants (orgs), the host names that serve them, and their public metadata (theme etc.).
CREATE TABLE org
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(150) NOT NULL,
    app_name   VARCHAR(80)  NOT NULL,
    logo_url   VARCHAR(500) NULL,
    status     VARCHAR(20)  NOT NULL,
    version    BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT org_status_chk CHECK (status IN ('INACTIVE', 'ACTIVE', 'SUSPENDED'))
);

CREATE TABLE org_domain
(
    id         BINARY(16)   NOT NULL,
    org_id     BIGINT       NOT NULL,
    hostname   VARCHAR(253) NOT NULL,
    active     BOOLEAN      NOT NULL,
    version    BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT org_domain_hostname_uk UNIQUE (hostname),
    CONSTRAINT org_domain_org_fk FOREIGN KEY (org_id) REFERENCES org (id)
);

CREATE TABLE org_metadata
(
    id         BINARY(16)  NOT NULL,
    org_id     BIGINT      NOT NULL,
    meta_key   VARCHAR(60) NOT NULL,
    meta_value TEXT        NOT NULL,
    version    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT org_metadata_key_uk UNIQUE (org_id, meta_key),
    CONSTRAINT org_metadata_org_fk FOREIGN KEY (org_id) REFERENCES org (id)
);
