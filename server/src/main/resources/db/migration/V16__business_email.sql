-- Business email verified during agreement signing (encrypted).
ALTER TABLE app_user ADD COLUMN business_email_enc VARBINARY(512) NULL;
