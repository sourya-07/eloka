package in.eko.eloka.agreement.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A signing session with the e-sign provider for one agreement version. */
@Entity
@Table(name = "esign_request")
class EsignRequest extends BaseEntity {

	@Column(name = "agreement_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID agreementId;

	@Column(name = "public_id", nullable = false, unique = true, length = 32)
	private String publicId;

	@Column(name = "provider_pipe", nullable = false)
	private int providerPipe;

	@Column(name = "provider_document_id", nullable = false, unique = true, length = 100)
	private String providerDocumentId;

	@Column(name = "signing_url", length = 500)
	private String signingUrl;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected EsignRequest() {
	}

	EsignRequest(UUID agreementId, String publicId, int providerPipe, String providerDocumentId, String signingUrl,
			Instant expiresAt) {
		this.agreementId = agreementId;
		this.publicId = publicId;
		this.providerPipe = providerPipe;
		this.providerDocumentId = providerDocumentId;
		this.signingUrl = signingUrl;
		this.expiresAt = expiresAt;
		this.status = "OPEN";
	}

	boolean isOpen(Instant now) {
		return "OPEN".equals(status) && now.isBefore(expiresAt);
	}

	void close(String finalStatus) {
		this.status = finalStatus;
	}

	String getPublicId() {
		return publicId;
	}

	int getProviderPipe() {
		return providerPipe;
	}

	String getProviderDocumentId() {
		return providerDocumentId;
	}

	String getSigningUrl() {
		return signingUrl;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}
}
