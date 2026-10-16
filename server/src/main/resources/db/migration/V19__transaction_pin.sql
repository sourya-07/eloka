-- Transaction PIN (BCrypt hash only) and one-time PinTwin keys used to encode it in transit.
CREATE TABLE transaction_pin
(
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    pin_hash   VARCHAR(72) NOT NULL,
    version    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT transaction_pin_user_uk UNIQUE (user_id),
    CONSTRAINT transaction_pin_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE pintwin_key
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id BINARY(16)  NOT NULL,
    key_chars  VARCHAR(10) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX pintwin_key_session_idx (session_id)
);
