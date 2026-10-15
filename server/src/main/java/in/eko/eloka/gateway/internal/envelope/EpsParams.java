package in.eko.eloka.gateway.internal.envelope;

import java.util.Optional;
import in.eko.eloka.shared.error.ValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * All request fields merged into one object (query, tf URI query, then body), with typed
 * accessors. EPS sends numbers as strings and vice versa, so accessors convert leniently.
 */
public final class EpsParams {

	private final ObjectNode fields;

	public EpsParams(ObjectNode fields) {
		this.fields = fields;
	}

	public Optional<String> string(String name) {
		JsonNode node = fields.get(name);
		if (node == null || node.isNull() || node.isMissingNode()) {
			return Optional.empty();
		}
		String text = node.isValueNode() ? node.asString() : node.toString();
		text = text.trim();
		return text.isEmpty() || "null".equals(text) || "undefined".equals(text) ? Optional.empty() : Optional.of(text);
	}

	public String requireString(String name) {
		return string(name).orElseThrow(() -> ValidationException.field(name, name + " is required"));
	}

	public Optional<Integer> integer(String name) {
		return string(name).map(value -> {
			try {
				return Integer.valueOf(value);
			}
			catch (NumberFormatException ex) {
				throw ValidationException.field(name, name + " must be a number");
			}
		});
	}

	public Optional<Long> longValue(String name) {
		return string(name).map(value -> {
			try {
				return Long.valueOf(value);
			}
			catch (NumberFormatException ex) {
				throw ValidationException.field(name, name + " must be a number");
			}
		});
	}

	public Optional<java.math.BigDecimal> decimal(String name) {
		return string(name).map(value -> {
			try {
				return new java.math.BigDecimal(value);
			}
			catch (NumberFormatException ex) {
				throw ValidationException.field(name, name + " must be an amount");
			}
		});
	}

	public int integer(String name, int fallback) {
		return integer(name).orElse(fallback);
	}

	public Optional<JsonNode> node(String name) {
		JsonNode node = fields.get(name);
		return node == null || node.isNull() ? Optional.empty() : Optional.of(node);
	}

	public ObjectNode raw() {
		return fields;
	}
}
