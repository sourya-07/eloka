package in.eko.eloka.identity.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Proof that the session owner re-entered an OTP a moment ago (step-up for sensitive actions:
 * bank account, e-sign, withdraw). Short-lived and single use; only the token hash is stored.
 */
@Entity
@Table(name = "reauth_grant")
class ReauthGrant extends BaseEntity {

	@Column(name = "session_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID sessionId;

	@Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected ReauthGrant() {
	}

	ReauthGrant(UUID sessionId, byte[] tokenHash, Instant expiresAt) {
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	/** Uses the grant for the given session. @return false when expired, used or for another session */
	boolean use(UUID session, Instant now) {
		if (usedAt != null || !now.isBefore(expiresAt) || !sessionId.equals(session)) {
			return false;
		}
		usedAt = now;
		return true;
	}
}
