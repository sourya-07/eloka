package in.eko.eloka.documents.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.documents.DocumentApi;
import in.eko.eloka.documents.DocumentContent;
import in.eko.eloka.documents.DocumentProperties;
import in.eko.eloka.documents.DocumentPurpose;
import in.eko.eloka.documents.DocumentRef;
import in.eko.eloka.documents.DocumentStorage;
import in.eko.eloka.documents.NewDocument;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

	Optional<StoredDocument> findByIdAndOrgId(UUID id, long orgId);

	Optional<StoredDocument> findFirstByOrgIdAndOwnerUserIdAndPurposeOrderByCreatedAtDesc(long orgId, UUID ownerUserId,
			DocumentPurpose purpose);
}

@Service
@Transactional(readOnly = true)
class DocumentService implements DocumentApi {

	private final StoredDocumentRepository documents;
	private final DocumentStorage storage;
	private final FieldCipher cipher;
	private final DocumentProperties properties;

	DocumentService(StoredDocumentRepository documents, DocumentStorage storage, FieldCipher cipher,
			DocumentProperties properties) {
		this.documents = documents;
		this.storage = storage;
		this.cipher = cipher;
		this.properties = properties;
	}

	@Override
	@Transactional
	public DocumentRef store(NewDocument document) {
		byte[] bytes = document.bytes();
		if (bytes == null || bytes.length == 0) {
			throw ValidationException.field("file", "The file is empty");
		}
		if (bytes.length > properties.maxSizeBytes()) {
			throw ValidationException.field("file", "The file is larger than " + properties.maxSizeBytes() / (1024 * 1024) + " MB");
		}
		FileType type = FileType.sniff(bytes)
			.orElseThrow(() -> ValidationException.field("file", "Only JPG, PNG or PDF files are accepted"));
		String key = UUID.randomUUID().toString();
		storage.put(key, cipher.encryptBytes(bytes));
		String name = document.fileName() == null ? "file" : document.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
		return documents.save(new StoredDocument(document.orgId(), document.ownerUserId(), document.uploadedBy(),
				document.purpose(), name.length() > 200 ? name.substring(0, 200) : name, type.contentType(), bytes.length,
				sha256(bytes), key)).toRef();
	}

	@Override
	public DocumentContent load(long orgId, UUID documentId) {
		StoredDocument doc = documents.findByIdAndOrgId(documentId, orgId)
			.orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
		return new DocumentContent(doc.toRef(), cipher.decryptBytes(storage.get(doc.getStorageKey())));
	}

	@Override
	public Optional<DocumentRef> latest(long orgId, UUID ownerUserId, DocumentPurpose purpose) {
		return documents.findFirstByOrgIdAndOwnerUserIdAndPurposeOrderByCreatedAtDesc(orgId, ownerUserId, purpose)
			.map(StoredDocument::toRef);
	}

	private static byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
