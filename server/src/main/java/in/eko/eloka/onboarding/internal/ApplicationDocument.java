package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A file the applicant uploaded for review (state machine doc §5.4). The encrypted content is in
 * the {@code documents} module; this row tracks type, review status and versions.
 */
@Entity
@Table(name = "application_document")
class ApplicationDocument extends BaseEntity {

	static final String PENDING_REVIEW = "PENDING_REVIEW";
	static final String SUPERSEDED = "SUPERSEDED";

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "public_id", nullable = false, unique = true, length = 32)
	private String publicId;

	@Column(name = "doc_type", nullable = false, length = 40)
	private String docType;

	@Column(name = "stored_document_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID storedDocumentId;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false, columnDefinition = "BINARY(32)")
	private byte[] sha256;

	@Column(name = "file_name", nullable = false, length = 200)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "reject_reason_code", length = 40)
	private String rejectReasonCode;

	@Column(name = "superseded_at")
	private Instant supersededAt;

	@Column(name = "reviewed_by", columnDefinition = "BINARY(16)")
	private UUID reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	protected ApplicationDocument() {
	}

	ApplicationDocument(UUID applicationId, String publicId, String docType, UUID storedDocumentId, byte[] sha256,
			String fileName, String contentType, long sizeBytes) {
		this.applicationId = applicationId;
		this.publicId = publicId;
		this.docType = docType;
		this.storedDocumentId = storedDocumentId;
		this.sha256 = sha256;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		// Content was type-checked and encrypted on upload: ready for the reviewer.
		this.status = PENDING_REVIEW;
	}

	/** S5 */
	void verify(UUID staffId, Instant now) {
		review("VERIFIED", null, staffId, now);
	}

	/** S6: the applicant must upload a new version (through a correction request). */
	void reject(String reasonCode, UUID staffId, Instant now) {
		review("REJECTED", reasonCode, staffId, now);
	}

	private void review(String newStatus, String reason, UUID staffId, Instant now) {
		if (!isCurrent()) {
			throw new in.eko.eloka.shared.error.ConflictException("DOCUMENT_SUPERSEDED", "This document was replaced");
		}
		this.status = newStatus;
		this.rejectReasonCode = reason;
		this.reviewedBy = staffId;
		this.reviewedAt = now;
	}

	void supersede(Instant now) {
		this.status = SUPERSEDED;
		this.supersededAt = now;
	}

	boolean isCurrent() {
		return !SUPERSEDED.equals(status);
	}

	UUID getApplicationId() {
		return applicationId;
	}

	String getPublicId() {
		return publicId;
	}

	String getDocType() {
		return docType;
	}

	UUID getStoredDocumentId() {
		return storedDocumentId;
	}

	String getStatus() {
		return status;
	}

	byte[] getSha256() {
		return sha256;
	}

	String getRejectReasonCode() {
		return rejectReasonCode;
	}

	Instant getUploadedAt() {
		return getCreatedAt();
	}
}
