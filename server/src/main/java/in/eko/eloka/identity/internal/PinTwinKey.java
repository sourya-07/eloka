package in.eko.eloka.identity.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A one-time digit substitution key, bound to one session. */
@Entity
@Table(name = "pintwin_key")
class PinTwinKey {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "session_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID sessionId;

	@Column(name = "key_chars", nullable = false, length = 10)
	private String keyChars;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected PinTwinKey() {
	}

	PinTwinKey(UUID sessionId, String keyChars, Instant expiresAt) {
		this.sessionId = sessionId;
		this.keyChars = keyChars;
		this.expiresAt = expiresAt;
	}

	Long getId() {
		return id;
	}

	String getKeyChars() {
		return keyChars;
	}

	boolean usableBy(UUID session, Instant now) {
		return usedAt == null && sessionId.equals(session) && now.isBefore(expiresAt);
	}

	/** Decodes an encoded PIN ({@code keyChars[digit]} per digit). @return digits, or null if invalid */
	String decode(String encoded) {
		StringBuilder pin = new StringBuilder();
		for (char c : encoded.toCharArray()) {
			int digit = keyChars.indexOf(c);
			if (digit < 0) {
				return null;
			}
			pin.append(digit);
		}
		return pin.toString();
	}

	void use(Instant now) {
		usedAt = now;
	}
}
