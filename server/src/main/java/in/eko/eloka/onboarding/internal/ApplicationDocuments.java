package in.eko.eloka.onboarding.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import in.eko.eloka.documents.DocumentApi;
import in.eko.eloka.documents.DocumentContent;
import in.eko.eloka.documents.DocumentPurpose;
import in.eko.eloka.documents.DocumentRef;
import in.eko.eloka.documents.NewDocument;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.persistence.PublicIds;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files of an application (A21–A25). Content goes to the {@code documents} module (type-checked,
 * encrypted at rest); this keeps the type, review status and versions. Uploading the same file
 * again for the same type returns the existing document instead of a copy.
 */
@Service
class ApplicationDocuments {

	/** Document types an applicant may upload. */
	static final Set<String> TYPES = Set.of("SIGNATURE", "LIVE_PHOTO", "LOGO", "PAN_CARD", "ADDRESS_PROOF",
			"INCORPORATION_CERTIFICATE", "PARTNERSHIP_DEED", "GST_CERTIFICATE", "BOARD_RESOLUTION", "BANK_PROOF",
			"OTHER");

	/** Types of which only the latest upload counts (a new one supersedes the old one). */
	private static final Set<String> SINGLE = Set.of("SIGNATURE", "LIVE_PHOTO", "LOGO", "PAN_CARD", "ADDRESS_PROOF",
			"INCORPORATION_CERTIFICATE", "PARTNERSHIP_DEED", "GST_CERTIFICATE", "BOARD_RESOLUTION", "BANK_PROOF");

	private final ApplicationDocumentRepository rows;
	private final DocumentApi store;
	private final Clock clock;

	ApplicationDocuments(ApplicationDocumentRepository rows, DocumentApi store, Clock clock) {
		this.rows = rows;
		this.store = store;
		this.clock = clock;
	}

	/** A21: stores a file for the application (called inside {@link ApplicationService#write}). */
	ApplicationDocument upload(StepContext context, UUID applicantId, String docType, String fileName, byte[] bytes) {
		String type = docType == null ? "" : docType.trim().toUpperCase(java.util.Locale.ROOT);
		if (!TYPES.contains(type)) {
			throw ValidationException.field("doc_type", "Unknown document type");
		}
		byte[] hash = sha256(bytes);
		List<ApplicationDocument> existing = rows.findByApplicationIdOrderByCreatedAtAsc(context.applicationId());
		for (ApplicationDocument document : existing) {
			if (document.isCurrent() && document.getDocType().equals(type) && MessageDigest.isEqual(document.getSha256(), hash)) {
				return document;
			}
		}
		DocumentRef stored = store.store(new NewDocument(IdentityApi.PLATFORM_ORG_ID, applicantId, applicantId,
				DocumentPurpose.LOCAL_ADMIN_APPLICATION, fileName == null ? type.toLowerCase() : fileName, bytes));
		Instant now = Instant.now(clock);
		if (SINGLE.contains(type)) {
			existing.stream()
				.filter(document -> document.isCurrent() && document.getDocType().equals(type))
				.forEach(document -> document.supersede(now));
		}
		ApplicationDocument document = rows.save(new ApplicationDocument(context.applicationId(),
				PublicIds.next("doc", now), type, stored.id(), hash, stored.fileName(), stored.contentType(), stored.size()));
		context.record("DOCUMENT_UPLOADED", null);
		return document;
	}

	/** A23 */
	List<ApplicationDocument> list(UUID applicationId) {
		return rows.findByApplicationIdOrderByCreatedAtAsc(applicationId);
	}

	/** A24: content of a current document of this application. */
	@Transactional(readOnly = true)
	DocumentContent download(UUID applicationId, String publicId) {
		ApplicationDocument document = rows.findByPublicIdAndApplicationId(publicId, applicationId)
			.orElseThrow(ApplicationDocuments::notFound);
		return store.load(IdentityApi.PLATFORM_ORG_ID, document.getStoredDocumentId());
	}

	/** A25: soft delete (superseded) while the application is editable. */
	void delete(StepContext context, String publicId) {
		ApplicationDocument document = rows.findByPublicIdAndApplicationId(publicId, context.applicationId())
			.orElseThrow(ApplicationDocuments::notFound);
		document.supersede(Instant.now(clock));
	}

	/** A current document of the expected type, or {@code 422} naming the field to fix. */
	ApplicationDocument requireCurrent(UUID applicationId, String publicId, String expectedType) {
		ApplicationDocument document = rows.findByPublicIdAndApplicationId(publicId, applicationId)
			.filter(ApplicationDocument::isCurrent)
			.orElseThrow(() -> new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED",
					"Upload the file first", Map.of("invalid_params", Map.of("document_id", "Unknown or replaced document"))));
		if (!document.getDocType().equals(expectedType)) {
			throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED",
					"This document is a " + document.getDocType() + ", expected " + expectedType,
					Map.of("invalid_params", Map.of("document_id", "Expected a " + expectedType + " document")));
		}
		return document;
	}

	Set<String> currentTypes(UUID applicationId) {
		return rows.findByApplicationIdOrderByCreatedAtAsc(applicationId)
			.stream()
			.filter(ApplicationDocument::isCurrent)
			.filter(document -> !"REJECTED".equals(document.getStatus())) // a rejected file must be replaced
			.map(ApplicationDocument::getDocType)
			.collect(Collectors.toSet());
	}

	private static NotFoundException notFound() {
		return new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found");
	}

	static byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
