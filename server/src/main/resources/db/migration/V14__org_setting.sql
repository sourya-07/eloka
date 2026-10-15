-- Private per-org business settings (JSON values).
CREATE TABLE org_setting
(
    id            BINARY(16)    NOT NULL,
    org_id        BIGINT        NOT NULL,
    setting_key   VARCHAR(60)   NOT NULL,
    setting_value VARCHAR(4000) NOT NULL,
    version       BIGINT        NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT org_setting_key_uk UNIQUE (org_id, setting_key),
    CONSTRAINT org_setting_org_fk FOREIGN KEY (org_id) REFERENCES org (id)
);
