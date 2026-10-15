package in.eko.eloka.gateway.internal.envelope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for the EPS response envelope:
 * {@code {status, response_status_id, response_type_id, message, data, ...}}.
 * {@code status 0} means success; the client also checks {@code response_type_id} per screen.
 */
public final class EpsEnvelope {

	private final Map<String, Object> body = new LinkedHashMap<>();
	private int httpStatus = 200;

	private EpsEnvelope(int status, String message) {
		body.put("status", status);
		body.put("response_status_id", status);
		body.put("response_type_id", status == 0 ? 0 : -1);
		body.put("message", message);
		body.put("data", Map.of());
	}

	public static EpsEnvelope ok() {
		return new EpsEnvelope(0, "Success");
	}

	public static EpsEnvelope fail(String message) {
		return new EpsEnvelope(1, message);
	}

	/** Shortcut for dropdown lookups: {@code param_attributes.list_elements}. */
	public static EpsResponse list(List<?> elements) {
		return ok().put("param_attributes", Map.of("list_elements", elements)).build();
	}

	public EpsEnvelope data(Object data) {
		body.put("data", data == null ? Map.of() : data);
		return this;
	}

	public EpsEnvelope type(int responseTypeId) {
		body.put("response_type_id", responseTypeId);
		return this;
	}

	public EpsEnvelope message(String message) {
		body.put("message", message);
		return this;
	}

	/** Adds a top-level field (e.g. {@code otpFailed}, {@code org_token}). */
	public EpsEnvelope put(String field, Object value) {
		body.put(field, value);
		return this;
	}

	public EpsEnvelope httpStatus(int status) {
		this.httpStatus = status;
		return this;
	}

	public EpsResponse build() {
		return new EpsResponse.Json(body, httpStatus);
	}
}
