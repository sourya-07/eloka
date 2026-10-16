package in.eko.eloka.agreement;

import java.util.Optional;
import java.util.UUID;

/** Public API of the agreement module: render, e-sign and confirm the Local Admin agreement. */
public interface AgreementApi {

	/**
	 * A27: the agreement for this content. Unchanged content returns the current agreement;
	 * changed content creates a new version and supersedes the old one (even a signed one).
	 *
	 * @param newSignature a reviewer asked for the agreement to be signed again: a signed current
	 *     version is superseded by a new unsigned one even when the content is unchanged
	 */
	AgreementView prepare(UUID applicationId, UUID ownerId, AgreementContent content, boolean newSignature);

	/** A28: opens (or reuses) an e-sign session for an agreement that is not signed yet. */
	EsignStart startEsign(UUID applicationId, String agreementId, UUID signerId);

	/** A29: current status; asks the provider and marks {@code SIGNED} only when it confirms. */
	AgreementView status(UUID applicationId, String agreementId);

	Optional<AgreementView> current(UUID applicationId);

	/** The rendered PDF (preview or signed copy). */
	byte[] pdf(UUID applicationId, String agreementId);

	/**
	 * @param status {@code READY}, {@code SIGNING}, {@code SIGNED}, {@code FAILED}, {@code EXPIRED} or {@code SUPERSEDED}
	 * @param contentSha256 hex SHA-256 of the agreement text (what was signed)
	 */
	record AgreementView(String agreementId, int versionNo, String templateVersion, String status, String contentSha256,
			java.time.Instant signedAt, String signerName) {

		public boolean isSigned() {
			return "SIGNED".equals(status);
		}
	}

	/** @param providerPipe client SDK: 0 Digio, 1 Karza, 2 Signzy, 3 Leegality (as the web app's getProvider) */
	record EsignStart(String esignRequestId, int providerPipe, String signingUrl, String documentId,
			java.time.Instant expiresAt, boolean alreadySigned) {
	}
}
