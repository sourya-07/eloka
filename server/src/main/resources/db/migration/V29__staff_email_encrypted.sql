-- staff_user.email held work emails in clear text. Personal data is stored encrypted + blind index
-- + masked copy (server/CLAUDE.md). P8 never shipped: the only rows are local dummy accounts, which
-- the local seed recreates on start (production accounts come from the staff-bootstrap command).
DELETE FROM staff_role_grant;
DELETE FROM staff_user;
ALTER TABLE staff_user DROP INDEX staff_user_email_uk;
ALTER TABLE staff_user DROP COLUMN email;
ALTER TABLE staff_user
    ADD COLUMN email_enc    VARBINARY(512) NOT NULL AFTER id,
    ADD COLUMN email_hash   BINARY(32)     NOT NULL AFTER email_enc,
    ADD COLUMN email_masked VARCHAR(254)   NOT NULL AFTER email_hash,
    ADD CONSTRAINT staff_user_email_uk UNIQUE (email_hash);
