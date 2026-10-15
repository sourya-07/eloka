package in.eko.eloka.documents.internal;

import java.util.UUID;
import in.eko.eloka.documents.DocumentPurpose;
import in.eko.eloka.documents.DocumentRef;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Metadata of a stored file. The bytes live in {@link in.eko.eloka.documents.DocumentStorage}, encrypted. */
@Entity
@Table(name = "stored_document")
class StoredDocument extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "owner_user_id", columnDefinition = "BINARY(16)")
	private UUID ownerUserId;

	@Column(name = "uploaded_by", columnDefinition = "BINARY(16)")
	private UUID uploadedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocumentPurpose purpose;

	@Column(name = "file_name", length = 200)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(nullable = false, columnDefinition = "BINARY(32)")
	private byte[] sha256;

	@Column(name = "storage_key", nullable = false, unique = true, length = 100)
	private String storageKey;

	protected StoredDocument() {
	}

	StoredDocument(long orgId, UUID ownerUserId, UUID uploadedBy, DocumentPurpose purpose, String fileName,
			String contentType, long sizeBytes, byte[] sha256, String storageKey) {
		this.orgId = orgId;
		this.ownerUserId = ownerUserId;
		this.uploadedBy = uploadedBy;
		this.purpose = purpose;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.storageKey = storageKey;
	}

	String getStorageKey() {
		return storageKey;
	}

	DocumentRef toRef() {
		return new DocumentRef(getId(), orgId, ownerUserId, purpose, fileName, contentType, sizeBytes, getCreatedAt());
	}
}
