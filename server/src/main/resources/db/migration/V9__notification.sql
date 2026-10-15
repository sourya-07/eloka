-- In-app notifications (per user, or org-wide when user_id is NULL) and per-user receipts.
CREATE TABLE notification
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    org_id      BIGINT        NOT NULL,
    user_id     BINARY(16)    NULL,
    type        VARCHAR(10)   NOT NULL,
    title       VARCHAR(150)  NOT NULL,
    body        VARCHAR(1000) NULL,
    image_url   VARCHAR(500)  NULL,
    link        VARCHAR(500)  NULL,
    link_label  VARCHAR(60)   NULL,
    priority    INT           NOT NULL,
    notify_time DATETIME(6)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX notification_inbox_idx (org_id, user_id, notify_time),
    CONSTRAINT notification_type_chk CHECK (type IN ('NORMAL', 'AD'))
);

CREATE TABLE notification_receipt
(
    id              BINARY(16)  NOT NULL,
    notification_id BIGINT      NOT NULL,
    user_id         BINARY(16)  NOT NULL,
    delivered       BOOLEAN     NOT NULL,
    read_status     INT         NOT NULL,
    version         BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT notification_receipt_uk UNIQUE (notification_id, user_id),
    CONSTRAINT notification_receipt_fk FOREIGN KEY (notification_id) REFERENCES notification (id)
);
