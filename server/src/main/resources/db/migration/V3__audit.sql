-- Append-only, hash-chained audit trail. audit_chain_head is locked while appending.
CREATE TABLE audit_event
(
    id          BINARY(16)    NOT NULL,
    seq         BIGINT        NOT NULL,
    occurred_at DATETIME(6)   NOT NULL,
    org_id      BIGINT        NULL,
    actor_type  VARCHAR(20)   NOT NULL,
    actor_id    VARCHAR(64)   NULL,
    action      VARCHAR(64)   NOT NULL,
    target_type VARCHAR(40)   NULL,
    target_id   VARCHAR(64)   NULL,
    details     VARCHAR(2000) NULL,
    prev_hash   BINARY(32)    NOT NULL,
    hash        BINARY(32)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT audit_event_seq_uk UNIQUE (seq),
    INDEX audit_event_org_time_idx (org_id, occurred_at)
);

CREATE TABLE audit_chain_head
(
    id        INT        NOT NULL,
    last_seq  BIGINT     NOT NULL,
    last_hash BINARY(32) NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO audit_chain_head (id, last_seq, last_hash) VALUES (1, 0, UNHEX(REPEAT('00', 32)));
