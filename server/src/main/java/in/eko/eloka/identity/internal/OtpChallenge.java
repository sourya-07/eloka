package in.eko.eloka.identity.internal;

import java.time.Instant;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One OTP sent to one mobile. Stores only {@code HMAC(pepper, salt ‖ code)}. Knows its own
 * rules: expiry, attempt limit, single use.
 */
@Entity
@Table(name = "otp_challenge")
class OtpChallenge extends BaseEntity {

	static final String PURPOSE_LOGIN = "LOGIN";
	static final String PURPOSE_EMAIL = "EMAIL";
	/** An agent's consent to be onboarded by a distributor/admin (assisted onboarding). */
	static final String PURPOSE_CONSENT = "AGENT_CONSENT";
	/** A Local Admin applicant's login (platform level, no tenant yet). */
	static final String PURPOSE_APPLICANT = "APPLICANT_LOGIN";
	/** Step-up before a sensitive action, bound to one session. */
	static final String PURPOSE_REAUTH = "REAUTH";
	/** Eko staff sign-in by OTP (local/test only). */
	static final String PURPOSE_STAFF = "STAFF_LOGIN";

	@Column(name = "org_id", nullable = false)
	private long orgId;

	/** Blind index of the target (mobile for login, email for email verification). */
	@Column(name = "mobile_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] mobileHash;

	@Column(nullable = false, length = 20)
	private String purpose;

	@Column(nullable = false, columnDefinition = "BINARY(16)")
	private byte[] salt;

	@Column(name = "code_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] codeHash;

	@Column(name = "client_ip_hash", columnDefinition = "BINARY(32)")
	private byte[] clientIpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected OtpChallenge() {
	}

	OtpChallenge(long orgId, byte[] mobileHash, String purpose, byte[] salt, byte[] codeHash, byte[] clientIpHash,
			Instant expiresAt, int maxAttempts) {
		this.orgId = orgId;
		this.mobileHash = mobileHash;
		this.purpose = purpose;
		this.salt = salt;
		this.codeHash = codeHash;
		this.clientIpHash = clientIpHash;
		this.expiresAt = expiresAt;
		this.maxAttempts = maxAttempts;
	}

	boolean isLive(Instant now) {
		return consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts;
	}

	byte[] getSalt() {
		return salt;
	}

	byte[] getCodeHash() {
		return codeHash;
	}

	/** Records a wrong guess. @return attempts left */
	int recordFailure() {
		attempts++;
		return Math.max(0, maxAttempts - attempts);
	}

	/** True when this OTP was used successfully within the window ending now. */
	boolean consumedWithin(Instant now, java.time.Duration window) {
		return consumedAt != null && !consumedAt.isBefore(now.minus(window));
	}

	void consume(Instant now) {
		this.consumedAt = now;
	}
}
