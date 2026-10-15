/**
 * Uploaded files (KYC images, signatures, ticket attachments). Files are checked by content
 * (JPEG, PNG or PDF only, at most 10 MB), encrypted at rest and stored through a
 * {@link in.eko.eloka.documents.DocumentStorage} port (local filesystem today, object storage later).
 *
 * <p>Public API: {@link in.eko.eloka.documents.DocumentApi}.
 */
@ApplicationModule(displayName = "Documents")
package in.eko.eloka.documents;

import org.springframework.modulith.ApplicationModule;
