package in.eko.eloka.verification;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares two names the way a reviewer would: case, punctuation, honorifics and legal-form
 * words (PVT, LTD, LLP…) are ignored, then the share of common words is scored 0–100.
 * Thresholds are policy (OQ-C3); a score below them sends the case to manual review.
 */
public final class NameMatch {

	/** At or above this score two names are treated as the same (working assumption, OQ-C3). */
	public static final int MATCH_THRESHOLD = 80;

	private static final Set<String> IGNORED = Set.of("PVT", "PRIVATE", "LTD", "LIMITED", "LLP", "CO", "COMPANY",
			"AND", "THE", "M/S", "MS", "MR", "MRS", "SHRI", "SMT", "DR");

	private NameMatch() {
	}

	/** @return 0 (nothing in common) to 100 (same words) */
	public static int score(String first, String second) {
		Set<String> a = words(first);
		Set<String> b = words(second);
		if (a.isEmpty() || b.isEmpty()) {
			return 0;
		}
		Set<String> common = new HashSet<>(a);
		common.retainAll(b);
		Set<String> all = new HashSet<>(a);
		all.addAll(b);
		return (int) Math.round(100.0 * common.size() / all.size());
	}

	public static boolean matches(String first, String second) {
		return score(first, second) >= MATCH_THRESHOLD;
	}

	private static Set<String> words(String name) {
		if (name == null) {
			return Set.of();
		}
		String plain = Normalizer.normalize(name, Normalizer.Form.NFKD)
			.replaceAll("\\p{M}", "")
			.toUpperCase(Locale.ROOT)
			.replaceAll("\\bM/S\\.?", " ")
			.replaceAll("[^A-Z0-9 ]", " ");
		return Arrays.stream(plain.split("\\s+"))
			.filter(word -> !word.isBlank() && !IGNORED.contains(word))
			.collect(Collectors.toSet());
	}
}
