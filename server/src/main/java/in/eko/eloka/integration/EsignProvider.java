package in.eko.eloka.integration;

import java.util.UUID;

/** Electronic signature of the agent agreement. */
public interface EsignProvider {

	/**
	 * Prepares signing for a user.
	 *
	 * @throws in.eko.eloka.shared.error.ConflictException when no provider is configured
	 */
	EsignSession prepare(UUID userId, String agreementId);

	/** @return true when the provider confirms the document is signed */
	boolean isSigned(String documentId);

	/**
	 * @param alreadySigned the provider already holds a signature (client shows "Proceed")
	 * @param signingUrl where the user signs, when not yet signed
	 * @param pipe client SDK to load: 0 Digio, 1 Karza, 2 Signzy, 3 Leegality
	 */
	record EsignSession(String documentId, boolean alreadySigned, String signingUrl, int pipe) {
	}
}
