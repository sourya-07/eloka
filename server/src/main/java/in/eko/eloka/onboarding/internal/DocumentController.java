package in.eko.eloka.onboarding.internal;

import java.io.IOException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.documents.DocumentContent;
import in.eko.eloka.shared.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application documents (A21–A25). Deviation from the blueprint: A21 is a direct multipart upload
 * (no object store yet, see P7_APPLICANT_API.md §2); A22 is kept so two-step clients still work.
 */
@RestController
@RequestMapping("/v1/onboarding/applications/{id}/documents")
class DocumentController {

	private final ApplicationService applications;
	private final ApplicationDocuments documents;
	private final Idempotency idempotency;

	DocumentController(ApplicationService applications, ApplicationDocuments documents, Idempotency idempotency) {
		this.applications = applications;
		this.documents = documents;
		this.idempotency = idempotency;
	}

	/** A21: multipart {@code doc_type} + {@code file}. Same file + type again → the same document. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<?> upload(@PathVariable String id, @RequestParam("doc_type") String docType,
			@RequestParam("file") MultipartFile file, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			HttpServletRequest request) {
		UUID applicantId = WebSupport.applicantId();
		byte[] bytes = read(file);
		String fingerprint = WebSupport.fingerprint(request, docType + ":" + HexFormat.of().formatHex(ApplicationDocuments.sha256(bytes)));
		return idempotency.run(applicantId, key, fingerprint, () -> {
			ApplicationDocument document = applications.write(applicantId, id, null, null,
					context -> documents.upload(context, applicantId, docType, file.getOriginalFilename(), bytes));
			return ResponseEntity.status(HttpStatus.CREATED).body(view(document));
		});
	}

	/** A22: uploads are complete as soon as A21 returns; answers with the current status. */
	@PostMapping("/{documentId}/complete")
	ResponseEntity<Map<String, Object>> complete(@PathVariable String id, @PathVariable String documentId) {
		UUID applicationId = applications.find(WebSupport.applicantId(), id).getId();
		ApplicationDocument document = documents.list(applicationId)
			.stream()
			.filter(d -> d.getPublicId().equals(documentId))
			.findFirst()
			.orElseThrow(() -> new in.eko.eloka.shared.error.NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
		return ResponseEntity.accepted().body(Map.of("document_id", documentId, "status", document.getStatus()));
	}

	/** A23 */
	@GetMapping
	Map<String, Object> list(@PathVariable String id) {
		UUID applicationId = applications.find(WebSupport.applicantId(), id).getId();
		List<Map<String, Object>> items = documents.list(applicationId).stream().map(DocumentController::view).toList();
		return Map.of("items", items);
	}

	/** A24: the file itself (the blueprint's presigned redirect is not needed without an object store). */
	@GetMapping("/{documentId}/download")
	ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable String documentId) {
		UUID applicationId = applications.find(WebSupport.applicantId(), id).getId();
		DocumentContent content = documents.download(applicationId, documentId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(content.ref().contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(content.ref().fileName()).build().toString())
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(content.bytes());
	}

	/** A25: remove (soft) a document while the application is editable. */
	@DeleteMapping("/{documentId}")
	ResponseEntity<Void> delete(@PathVariable String id, @PathVariable String documentId,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		long version = WebSupport.expectedVersion(ifMatch);
		applications.write(WebSupport.applicantId(), id, version, null, context -> {
			documents.delete(context, documentId);
			context.record("DOCUMENT_REMOVED", null);
			return null;
		});
		return ResponseEntity.noContent().build();
	}

	private static Map<String, Object> view(ApplicationDocument document) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("document_id", document.getPublicId());
		item.put("doc_type", document.getDocType());
		item.put("status", document.getStatus());
		if (document.getRejectReasonCode() != null) {
			item.put("reject_reason_code", document.getRejectReasonCode());
		}
		item.put("uploaded_at", document.getUploadedAt());
		return item;
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw ValidationException.field("file", "Could not read the file");
		}
	}
}
