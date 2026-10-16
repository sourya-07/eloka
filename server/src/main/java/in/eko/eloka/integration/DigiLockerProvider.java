package in.eko.eloka.integration;

import java.util.UUID;

/** Aadhaar verification through DigiLocker (redirect + consent). */
public interface DigiLockerProvider {

	/**
	 * Starts a consent session for a user.
	 *
	 * @param publicBaseUrl this server's public base URL (fakes serve their page from it)
	 * @throws in.eko.eloka.shared.error.ConflictException when no provider is configured
	 */
	DigiLockerSession start(UUID userId, String publicBaseUrl);

	/** @return true when the user completed consent for this request */
	boolean isCompleted(String requestId);

	/**
	 * Identity the user shared in DigiLocker, once consent is complete.
	 *
	 * @param hintName expected name; real providers ignore it, fakes echo it so local demos match
	 */
	default java.util.Optional<KycIdentity> identity(String requestId, String hintName) {
		return java.util.Optional.empty();
	}

	record DigiLockerSession(String link, String requestId) {
	}

	/** @param aadhaarMasked {@code XXXX-XXXX-1234}; the full number is never returned or stored */
	record KycIdentity(String name, String aadhaarMasked) {
	}
}
