-- Wallet accounts (balance read model) and their append-only ledger.
CREATE TABLE wallet_account
(
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    org_id         BIGINT         NOT NULL,
    user_id        BINARY(16)     NOT NULL,
    type_id        INT            NOT NULL,
    account_number VARCHAR(30)    NOT NULL,
    balance        DECIMAL(18, 2) NOT NULL,
    version        BIGINT         NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT wallet_account_number_uk UNIQUE (account_number),
    CONSTRAINT wallet_account_owner_uk UNIQUE (org_id, user_id, type_id),
    CONSTRAINT wallet_account_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT wallet_account_balance_chk CHECK (balance >= 0)
);

CREATE TABLE ledger_entry
(
    id            BINARY(16)     NOT NULL,
    account_id    BIGINT         NOT NULL,
    direction     CHAR(2)        NOT NULL,
    amount        DECIMAL(18, 2) NOT NULL,
    balance_after DECIMAL(18, 2) NOT NULL,
    reference     VARCHAR(64)    NOT NULL,
    narration     VARCHAR(200)   NULL,
    posted_at     DATETIME(6)    NOT NULL,
    version       BIGINT         NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ledger_entry_reference_uk UNIQUE (account_id, reference),
    CONSTRAINT ledger_entry_account_fk FOREIGN KEY (account_id) REFERENCES wallet_account (id),
    CONSTRAINT ledger_entry_direction_chk CHECK (direction IN ('CR', 'DR')),
    CONSTRAINT ledger_entry_amount_chk CHECK (amount > 0)
);
