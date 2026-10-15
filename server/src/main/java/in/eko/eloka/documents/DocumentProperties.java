package in.eko.eloka.documents;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param storagePath directory for the filesystem storage (mount a volume there in production)
 * @param maxSizeBytes largest accepted file
 */
@ConfigurationProperties("eloka.documents")
public record DocumentProperties(String storagePath, long maxSizeBytes) {

	public DocumentProperties {
		storagePath = storagePath == null || storagePath.isBlank() ? "data/files" : storagePath;
		maxSizeBytes = maxSizeBytes <= 0 ? 10L * 1024 * 1024 : maxSizeBytes;
	}
}
