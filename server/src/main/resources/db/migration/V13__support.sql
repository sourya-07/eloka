-- Support tickets, their comments and attachments (documents).
CREATE TABLE support_ticket
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    org_id       BIGINT        NOT NULL,
    user_id      BINARY(16)    NOT NULL,
    user_code    VARCHAR(20)   NOT NULL,
    user_name    VARCHAR(150)  NULL,
    summary      VARCHAR(200)  NOT NULL,
    description  VARCHAR(4000) NULL,
    category     VARCHAR(80)   NULL,
    sub_category VARCHAR(80)   NULL,
    issue_type   VARCHAR(40)   NULL,
    tid          BIGINT        NULL,
    tx_typeid    INT           NULL,
    priority     VARCHAR(20)   NULL,
    tat_hours    INT           NULL,
    origin       VARCHAR(40)   NULL,
    status       VARCHAR(20)   NOT NULL,
    version      BIGINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX support_ticket_org_idx (org_id, created_at),
    INDEX support_ticket_user_idx (org_id, user_id, created_at),
    CONSTRAINT support_ticket_status_chk CHECK (status IN ('OPEN', 'ESCALATED', 'CLOSED'))
);

CREATE TABLE support_ticket_comment
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT        NOT NULL,
    author_id   BINARY(16)    NULL,
    author_role VARCHAR(10)   NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT support_ticket_comment_fk FOREIGN KEY (ticket_id) REFERENCES support_ticket (id)
);

CREATE TABLE support_ticket_attachment
(
    id          BIGINT     NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT     NOT NULL,
    document_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT support_ticket_attachment_fk FOREIGN KEY (ticket_id) REFERENCES support_ticket (id)
);
