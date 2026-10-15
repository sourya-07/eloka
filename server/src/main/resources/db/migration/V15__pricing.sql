-- Pricing and commission rules set by org admins. Superseded rules stay (active = false) as history.
CREATE TABLE pricing_rule
(
    id               BINARY(16)     NOT NULL,
    org_id           BIGINT         NOT NULL,
    product_id       INT            NOT NULL,
    scope            VARCHAR(10)    NOT NULL,
    target_user_id   BINARY(16)     NULL,
    target_user_code VARCHAR(20)    NULL,
    percent          BOOLEAN        NOT NULL,
    value            DECIMAL(12, 4) NOT NULL,
    slab_min         DECIMAL(18, 2) NOT NULL,
    slab_max         DECIMAL(18, 2) NOT NULL,
    active           BOOLEAN        NOT NULL,
    set_by           VARCHAR(20)    NULL,
    version          BIGINT         NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    INDEX pricing_rule_lookup_idx (org_id, product_id, scope, active),
    CONSTRAINT pricing_rule_scope_chk CHECK (scope IN ('ALL', 'NETWORK', 'USER')),
    CONSTRAINT pricing_rule_value_chk CHECK (value >= 0)
);
