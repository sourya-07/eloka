-- Demo accounts created from "Onboard Agents" expire after a validity period.
ALTER TABLE app_user
    ADD COLUMN demo_expires_on DATE NULL;
