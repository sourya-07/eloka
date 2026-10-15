-- Uploaded files: metadata here, encrypted bytes in document storage (filesystem volume).
CREATE TABLE stored_document
(
    id            BINARY(16)   NOT NULL,
    org_id        BIGINT       NOT NULL,
    owner_user_id BINARY(16)   NULL,
    uploaded_by   BINARY(16)   NULL,
    purpose       VARCHAR(30)  NOT NULL,
    file_name     VARCHAR(200) NULL,
    content_type  VARCHAR(50)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        BINARY(32)   NOT NULL,
    storage_key   VARCHAR(100) NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT stored_document_key_uk UNIQUE (storage_key),
    INDEX stored_document_owner_idx (org_id, owner_user_id, purpose, created_at)
);
