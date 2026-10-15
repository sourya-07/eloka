-- Users of a tenant. Mobile/email are encrypted; mobile_hash (HMAC) is the lookup key.
CREATE TABLE app_user
(
    id              BINARY(16)     NOT NULL,
    org_id          BIGINT         NOT NULL,
    user_code       VARCHAR(20)    NOT NULL,
    user_type       INT            NOT NULL,
    org_admin       BOOLEAN        NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    name            VARCHAR(150)   NOT NULL,
    mobile_enc      VARBINARY(128) NOT NULL,
    mobile_hash     BINARY(32)     NOT NULL,
    mobile_masked   VARCHAR(16)    NOT NULL,
    email_enc       VARBINARY(512) NULL,
    date_of_joining DATE           NOT NULL,
    demo            BOOLEAN        NOT NULL,
    version         BIGINT         NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT app_user_code_uk UNIQUE (user_code),
    CONSTRAINT app_user_org_mobile_uk UNIQUE (org_id, mobile_hash),
    CONSTRAINT app_user_org_fk FOREIGN KEY (org_id) REFERENCES org (id),
    CONSTRAINT app_user_status_chk CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

CREATE TABLE shop_profile
(
    id           BINARY(16)   NOT NULL,
    user_id      BINARY(16)   NOT NULL,
    shop_name    VARCHAR(150) NULL,
    shop_type    VARCHAR(20)  NULL,
    address_line VARCHAR(300) NULL,
    city         VARCHAR(80)  NULL,
    state        VARCHAR(80)  NULL,
    pincode      VARCHAR(6)   NULL,
    version      BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT shop_profile_user_uk UNIQUE (user_id),
    CONSTRAINT shop_profile_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- Human-facing counters (locked while incremented).
CREATE TABLE id_sequence
(
    name       VARCHAR(40) NOT NULL,
    next_value BIGINT      NOT NULL,
    PRIMARY KEY (name)
);

INSERT INTO id_sequence (name, next_value) VALUES ('user_code', 30000100);
