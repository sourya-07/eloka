package in.eko.eloka.documents;

import java.util.Optional;
import java.util.UUID;

/** Public API of the documents module. Callers decide who may read which document. */
public interface DocumentApi {

	/**
	 * @throws in.eko.eloka.shared.error.ValidationException when the file is empty, too big or not JPEG/PNG/PDF
	 */
	DocumentRef store(NewDocument document);

	/** @throws in.eko.eloka.shared.error.NotFoundException when missing or in another org */
	DocumentContent load(long orgId, UUID documentId);

	/** Most recent document of this purpose for a user. */
	Optional<DocumentRef> latest(long orgId, UUID ownerUserId, DocumentPurpose purpose);
}
