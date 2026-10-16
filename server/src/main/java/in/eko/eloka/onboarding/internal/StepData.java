package in.eko.eloka.onboarding.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import in.eko.eloka.shared.error.ValidationException;
import tools.jackson.databind.JsonNode;

/**
 * Reads step data field by field and collects every problem, so the applicant sees all
 * mistakes at once ({@code 422} with {@code invalid_params}).
 */
final class StepData {

	/** Same rules as the web app (client/constants/validation.ts), so both sides agree. */
	static final Pattern COMPANY_NAME = Pattern.compile("^[-a-zA-Z0-9 ,./:&']{2,100}$");
	static final Pattern PERSON_NAME = Pattern.compile("^(?:[A-Za-z]+\\.? ){0,2}[A-Za-z]+$");
	static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");
	static final Pattern PINCODE = Pattern.compile("^[1-9]\\d{5}$");
	static final Pattern GSTIN = Pattern.compile("^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
	static final Pattern CIN = Pattern.compile("^[LU]\\d{5}[A-Z]{2}\\d{4}[A-Z]{3}\\d{6}$");

	private final JsonNode node;
	private final Map<String, String> errors = new LinkedHashMap<>();

	StepData(JsonNode node) {
		this.node = node;
	}

	String required(String field) {
		String value = optional(field);
		if (value == null) {
			errors.putIfAbsent(field, "Required");
		}
		return value;
	}

	String optional(String field) {
		JsonNode value = node == null ? null : node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.isValueNode() ? value.asString().trim() : null;
		return text == null || text.isEmpty() ? null : text;
	}

	String matching(String field, Pattern pattern, String message, boolean isRequired) {
		String value = isRequired ? required(field) : optional(field);
		if (value != null && !pattern.matcher(value).matches()) {
			errors.putIfAbsent(field, message);
		}
		return value;
	}

	String length(String field, int min, int max, boolean isRequired) {
		String value = isRequired ? required(field) : optional(field);
		if (value != null && (value.length() < min || value.length() > max)) {
			errors.putIfAbsent(field, "Must be " + min + " to " + max + " characters");
		}
		return value;
	}

	Integer integer(String field) {
		JsonNode value = node == null ? null : node.get(field);
		if (value == null || value.isNull()) {
			errors.putIfAbsent(field, "Required");
			return null;
		}
		if (value.canConvertToInt()) {
			return value.asInt();
		}
		try {
			return Integer.parseInt(value.asString().trim());
		}
		catch (NumberFormatException ex) {
			errors.putIfAbsent(field, "Must be a number");
			return null;
		}
	}

	boolean isTrue(String field) {
		JsonNode value = node == null ? null : node.get(field);
		boolean accepted = value != null && (value.isBoolean() ? value.asBoolean() : "true".equalsIgnoreCase(value.asString()));
		if (!accepted) {
			errors.putIfAbsent(field, "Must be accepted");
		}
		return accepted;
	}

	List<String> strings(String field) {
		JsonNode value = node == null ? null : node.get(field);
		List<String> out = new ArrayList<>();
		if (value != null && value.isArray()) {
			value.forEach(item -> out.add(item.asString()));
		}
		return out;
	}

	JsonNode object(String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value != null && value.isObject() ? value : null;
	}

	/** A public document id ({@code doc_…}); resolution happens in the caller. */
	String documentId(String field, boolean isRequired) {
		String value = isRequired ? required(field) : optional(field);
		if (value != null && !value.startsWith("doc_")) {
			errors.putIfAbsent(field, "Upload the file first and send its document_id");
		}
		return value;
	}

	void error(String field, String message) {
		errors.putIfAbsent(field, message);
	}

	/** Throws {@code 422 VALIDATION_FAILED} when any field was wrong. */
	void validate() {
		if (!errors.isEmpty()) {
			throw new ValidationException("Please correct the highlighted fields", errors);
		}
	}
}
