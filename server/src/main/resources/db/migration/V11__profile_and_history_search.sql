-- P3: personal details, full shop address, GSTIN, DELETED status (demo users), and a blind index
-- on customer mobiles so history can be searched by mobile without storing it in clear.
ALTER TABLE app_user
    ADD COLUMN dob            DATE        NULL,
    ADD COLUMN gender         VARCHAR(10) NULL,
    ADD COLUMN marital_status VARCHAR(10) NULL,
    ADD COLUMN qualification  VARCHAR(30) NULL,
    ADD COLUMN gstin          VARCHAR(15) NULL,
    DROP CHECK app_user_status_chk,
    ADD CONSTRAINT app_user_status_chk CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'DELETED'));

ALTER TABLE shop_profile
    ADD COLUMN address_line2  VARCHAR(300)  NULL,
    ADD COLUMN country        VARCHAR(60)   NULL,
    ADD COLUMN ownership_type VARCHAR(20)   NULL,
    ADD COLUMN latitude       DECIMAL(9, 6) NULL,
    ADD COLUMN longitude      DECIMAL(9, 6) NULL;

ALTER TABLE txn_record
    ADD COLUMN customer_mobile_hash BINARY(32) NULL,
    ADD INDEX txn_record_customer_idx (org_id, customer_mobile_hash);
