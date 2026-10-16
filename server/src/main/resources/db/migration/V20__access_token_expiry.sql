-- Access tokens get their own short lifetime (eloka.identity.access-token-ttl); the session
-- (refresh token) keeps the idle and absolute limits. Existing sessions keep their current end.
ALTER TABLE auth_session ADD COLUMN access_expires_at DATETIME(6) NULL AFTER previous_refresh_hash;
UPDATE auth_session SET access_expires_at = LEAST(idle_expires_at, absolute_expires_at);
ALTER TABLE auth_session MODIFY access_expires_at DATETIME(6) NOT NULL;
