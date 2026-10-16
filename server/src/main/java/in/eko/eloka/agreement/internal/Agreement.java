package in.eko.eloka.agreement.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One rendered version of an application's agreement (state machine doc §5.3). */
@Entity
@Table(name = "agreement")
class Agreement extends BaseEntity {

	static final String READY = "READY";
	static final String SIGNING = "SIGNING";
	static final String SIGNED = "SIGNED";
	static final String SUPERSEDED = "SUPERSEDED";

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "public_id", nullable = false, unique = true, length = 32)
	private String publicId;

	@Column(name = "version_no", nullable = false)
	private int versionNo;

	@Column(name = "template_version", nullable = false, length = 20)
	private String templateVersion;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "content_sha256", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] contentSha256;

	@Column(name = "document_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID documentId;

	@Column(name = "signed_at")
	private Instant signedAt;

	@Column(name = "signer_name", length = 150)
	private String signerName;

	protected Agreement() {
	}

	Agreement(UUID applicationId, String publicId, int versionNo, String templateVersion, byte[] contentSha256,
			UUID documentId) {
		this.applicationId = applicationId;
		this.publicId = publicId;
		this.versionNo = versionNo;
		this.templateVersion = templateVersion;
		this.contentSha256 = contentSha256;
		this.documentId = documentId;
		this.status = READY;
	}

	void signing() {
		if (READY.equals(status)) {
			status = SIGNING;
		}
	}

	/** Only after the provider confirmed the signature. */
	void signed(Instant at, String signer) {
		if (!SIGNED.equals(status)) {
			status = SIGNED;
			signedAt = at;
			signerName = signer;
		}
	}

	void supersede() {
		status = SUPERSEDED;
	}

	boolean isLive() {
		return !SUPERSEDED.equals(status);
	}

	boolean isSigned() {
		return SIGNED.equals(status);
	}

	String getPublicId() {
		return publicId;
	}

	int getVersionNo() {
		return versionNo;
	}

	String getTemplateVersion() {
		return templateVersion;
	}

	String getStatus() {
		return status;
	}

	byte[] getContentSha256() {
		return contentSha256;
	}

	UUID getDocumentId() {
		return documentId;
	}

	Instant getSignedAt() {
		return signedAt;
	}

	String getSignerName() {
		return signerName;
	}
}
