package in.eko.eloka.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import in.eko.eloka.shared.security.SessionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A login session. Holds only hashes of its tokens. Owns its lifetime rules: sliding idle
 * timeout, absolute lifetime, revocation, and token rotation.
 */
@Entity
@Table(name = "auth_session")
class AuthSession extends BaseEntity {

	/** Do not rewrite last_seen_at more often than this (keeps reads cheap). */
	private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SessionKind kind;

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_id", columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "mobile_enc", nullable = false, columnDefinition = "VARBINARY(128)")
	private byte[] mobileEnc;

	@Column(name = "access_token_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] accessTokenHash;

	@Column(name = "refresh_token_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] refreshTokenHash;

	@Column(name = "previous_refresh_hash", columnDefinition = "BINARY(32)")
	private byte[] previousRefreshHash;

	@Column(name = "idle_expires_at", nullable = false)
	private Instant idleExpiresAt;

	@Column(name = "absolute_expires_at", nullable = false)
	private Instant absoluteExpiresAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoke_reason", length = 40)
	private String revokeReason;

	protected AuthSession() {
	}

	AuthSession(SessionKind kind, long orgId, UUID userId, byte[] mobileEnc, byte[] accessTokenHash,
			byte[] refreshTokenHash, Instant now, Duration idleTimeout, Duration absoluteTtl) {
		this.kind = kind;
		this.orgId = orgId;
		this.userId = userId;
		this.mobileEnc = mobileEnc;
		this.accessTokenHash = accessTokenHash;
		this.refreshTokenHash = refreshTokenHash;
		this.lastSeenAt = now;
		this.idleExpiresAt = now.plus(idleTimeout);
		this.absoluteExpiresAt = now.plus(absoluteTtl);
	}

	boolean isActive(Instant now) {
		return revokedAt == null && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
	}

	/** Extends the idle window on use. @return true when the row changed */
	boolean touch(Instant now, Duration idleTimeout) {
		if (lastSeenAt.plus(TOUCH_INTERVAL).isAfter(now)) {
			return false;
		}
		lastSeenAt = now;
		Instant extended = now.plus(idleTimeout);
		idleExpiresAt = extended.isBefore(absoluteExpiresAt) ? extended : absoluteExpiresAt;
		return true;
	}

	void rotate(byte[] newAccessHash, byte[] newRefreshHash, Instant now, Duration idleTimeout) {
		this.previousRefreshHash = this.refreshTokenHash;
		this.accessTokenHash = newAccessHash;
		this.refreshTokenHash = newRefreshHash;
		this.lastSeenAt = now;
		Instant extended = now.plus(idleTimeout);
		this.idleExpiresAt = extended.isBefore(absoluteExpiresAt) ? extended : absoluteExpiresAt;
	}

	void revoke(Instant now, String reason) {
		if (revokedAt == null) {
			revokedAt = now;
			revokeReason = reason;
		}
	}

	void attachUser(UUID newUserId) {
		this.userId = newUserId;
		this.kind = SessionKind.USER;
	}

	SessionKind getKind() {
		return kind;
	}

	long getOrgId() {
		return orgId;
	}

	UUID getUserId() {
		return userId;
	}

	byte[] getMobileEnc() {
		return mobileEnc;
	}

	Duration remaining(Instant now) {
		Instant end = idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
		return Duration.between(now, end);
	}
}
