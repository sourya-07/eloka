package in.eko.eloka.whitelabel;

import java.util.Map;
import java.util.UUID;

/**
 * @param logoDocumentId stored document id of the logo, or null for the default branding (OQ-P8)
 * @param theme colours by key ({@code primary}, {@code primary_light}, …, {@code navstyle}), may be empty
 */
public record BrandingDraftInput(String appName, UUID logoDocumentId, Map<String, String> theme) {

	public BrandingDraftInput {
		theme = theme == null ? Map.of() : Map.copyOf(theme);
	}
}
