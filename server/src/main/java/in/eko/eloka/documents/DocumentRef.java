package in.eko.eloka.documents;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a stored file (no content). */
public record DocumentRef(UUID id, long orgId, UUID ownerUserId, DocumentPurpose purpose, String fileName,
		String contentType, long size, Instant uploadedAt) {
}
