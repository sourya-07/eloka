package in.eko.eloka.reference;

import java.util.Map;

/**
 * One dropdown option.
 *
 * @param extra additional values the client needs for this option (e.g. a bank's {@code bank_id})
 */
public record Option(String value, String label, Map<String, String> extra) {

	public static Option of(String value, String label) {
		return new Option(value, label, Map.of());
	}
}
