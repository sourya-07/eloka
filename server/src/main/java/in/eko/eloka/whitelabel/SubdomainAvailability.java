package in.eko.eloka.whitelabel;

import java.util.List;

/**
 * @param reason {@code INVALID_FORMAT}, {@code RESERVED_WORD}, {@code TAKEN} or null when available
 * @param defaultUrl the site address the label would give, e.g. {@code https://shlok.eloka.in}
 */
public record SubdomainAvailability(String label, boolean available, String reason, List<String> suggestions,
		String defaultUrl) {
}
