package in.eko.eloka.documents;

import java.util.UUID;

/**
 * A file to store.
 *
 * @param ownerUserId the user the file belongs to (e.g. the agent whose PAN this is)
 * @param uploadedBy who uploaded it (may differ for assisted onboarding)
 */
public record NewDocument(long orgId, UUID ownerUserId, UUID uploadedBy, DocumentPurpose purpose, String fileName,
		byte[] bytes) {
}
