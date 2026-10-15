package in.eko.eloka.gateway.internal.envelope;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads every request shape the client produces into one {@link EpsRequest}:
 * <ul>
 * <li>JSON or URL-encoded bodies;</li>
 * <li>multipart with a URL-encoded {@code form-data} (upload proxy) or {@code formdata}
 * (direct) field plus files;</li>
 * <li>tf-req headers, or a real request to {@code /ekoicici/...} made by the client's proxy
 * (which turns body fields into query parameters).</li>
 * </ul>
 * Precedence: query string, then the tf URI's own query string, then the body.
 */
@Component
public class EpsRequestParser {

	/** Known tunnel roots, longest first so {@code /v1/marketuat} wins over {@code /v1}. */
	static final List<String> TF_ROOTS = List.of("/ekoicici/v1/marketuat", "/ekoicici/v1", "/ekoicici/v2",
			"/ekoicici/v3");

	private static final List<String> FORM_FIELDS = List.of("form-data", "formdata");

	private final JsonMapper json;

	public EpsRequestParser(JsonMapper json) {
		this.json = json;
	}

	public EpsRequest parse(HttpServletRequest request) throws IOException {
		ObjectNode params = json.createObjectNode();
		putQuery(params, request.getQueryString());

		Optional<TfRoute> tf = tfRoute(request, params);
		List<UploadedFile> files = new ArrayList<>();
		String contentType = Optional.ofNullable(request.getContentType()).orElse("").toLowerCase();

		if (request instanceof MultipartHttpServletRequest multipart) {
			readFormParameters(params, multipart.getParameterMap());
			for (Map.Entry<String, List<MultipartFile>> entry : multipart.getMultiFileMap().entrySet()) {
				for (MultipartFile file : entry.getValue()) {
					files.add(new UploadedFile(entry.getKey(), file.getOriginalFilename(), file.getContentType(), file.getBytes()));
				}
			}
		}
		else if (contentType.startsWith("application/x-www-form-urlencoded")) {
			readFormParameters(params, request.getParameterMap());
		}
		else {
			byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
			if (body.length > 0) {
				readJsonOrForm(params, body);
			}
		}

		return new EpsRequest(request.getRequestURI(), HttpMethod.valueOf(request.getMethod()), new EpsParams(params),
				List.copyOf(files), tf, bearer(request), request.getRemoteAddr());
	}

	private Optional<TfRoute> tfRoute(HttpServletRequest request, ObjectNode params) {
		String headerUri = request.getHeader("tf-req-uri");
		if (headerUri != null && !headerUri.isBlank()) {
			String root = Optional.ofNullable(request.getHeader("tf-req-uri-root-path")).filter(r -> !r.isBlank())
				.orElse("/ekoicici/v1");
			String method = Optional.ofNullable(request.getHeader("tf-req-method")).filter(m -> !m.isBlank()).orElse("GET");
			int query = headerUri.indexOf('?');
			if (query >= 0) {
				putQuery(params, headerUri.substring(query + 1));
				headerUri = headerUri.substring(0, query);
			}
			return Optional.of(new TfRoute(trimSlash(root), HttpMethod.valueOf(method.toUpperCase()), headerUri));
		}
		String path = request.getRequestURI();
		for (String root : TF_ROOTS) {
			if (path.startsWith(root + "/")) {
				return Optional.of(new TfRoute(root, HttpMethod.valueOf(request.getMethod()), path.substring(root.length())));
			}
		}
		return Optional.empty();
	}

	private void readFormParameters(ObjectNode params, Map<String, String[]> form) {
		form.forEach((name, values) -> {
			String value = values.length == 0 ? "" : values[values.length - 1];
			if (FORM_FIELDS.contains(name)) {
				putQuery(params, value);
			}
			else {
				params.put(name, value);
			}
		});
	}

	private void readJsonOrForm(ObjectNode params, byte[] body) {
		try {
			JsonNode node = json.readTree(body);
			if (node instanceof ObjectNode object) {
				params.setAll(object);
				return;
			}
		}
		catch (JacksonException ignored) {
			// Not JSON: fall back to URL-encoded.
		}
		putQuery(params, new String(body, StandardCharsets.UTF_8));
	}

	private static void putQuery(ObjectNode params, String query) {
		if (query == null || query.isBlank()) {
			return;
		}
		for (String pair : query.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String name = decode(eq < 0 ? pair : pair.substring(0, eq));
			String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
			params.put(name, value);
		}
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private static String trimSlash(String root) {
		return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
	}

	private static Optional<String> bearer(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			String token = header.substring(7).trim();
			return token.isEmpty() ? Optional.empty() : Optional.of(token);
		}
		return Optional.empty();
	}
}
