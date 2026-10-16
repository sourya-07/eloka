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

	record DigiLockerSession(String link, String requestId) {
	}
}
