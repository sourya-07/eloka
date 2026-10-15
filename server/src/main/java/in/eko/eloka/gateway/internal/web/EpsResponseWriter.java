package in.eko.eloka.gateway.internal.web;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.shared.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Turns an {@link EpsResponse} into the HTTP response. */
@Component
class EpsResponseWriter {

	ResponseEntity<?> write(EpsResponse response, HttpServletRequest request) {
		return switch (response) {
			case EpsResponse.Json json -> {
				recordOutcome(json.body(), request);
				yield ResponseEntity.status(json.httpStatus()).contentType(MediaType.APPLICATION_JSON).body(json.body());
			}
			case EpsResponse.File file -> {
				Map<String, Object> wrapped = new LinkedHashMap<>();
				wrapped.put("blob", Base64.getEncoder().encodeToString(file.bytes()));
				wrapped.put("name", file.name());
				wrapped.put("content-type", file.contentType());
				request.setAttribute(RequestIdFilter.OUTCOME_ATTRIBUTE, 0);
				yield ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("file", wrapped));
			}
			case EpsResponse.Binary binary -> {
				request.setAttribute(RequestIdFilter.OUTCOME_ATTRIBUTE, 0);
				yield ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(binary.name()).build().toString())
					.contentType(MediaType.parseMediaType(binary.contentType()))
					.body(binary.bytes());
			}
		};
	}

	private static void recordOutcome(Object body, HttpServletRequest request) {
		if (body instanceof Map<?, ?> map && map.containsKey("status")) {
			request.setAttribute(RequestIdFilter.OUTCOME_ATTRIBUTE, map.get("status"));
		}
	}
}
