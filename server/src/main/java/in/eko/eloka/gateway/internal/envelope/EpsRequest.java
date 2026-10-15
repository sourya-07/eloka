package in.eko.eloka.gateway.internal.envelope;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpMethod;

/**
 * A parsed EPS-style request.
 *
 * @param path request path, e.g. {@code /transactions/do}
 * @param method real HTTP method
 * @param params merged fields
 * @param files uploaded files (multipart only)
 * @param tf tf-req target when this is a REST-tunnel call
 * @param bearerToken raw bearer token if sent (for logout)
 * @param clientIp caller IP as seen by the server (proxies resolved by Tomcat)
 */
public record EpsRequest(String path, HttpMethod method, EpsParams params, List<UploadedFile> files,
		Optional<TfRoute> tf, Optional<String> bearerToken, String clientIp) {

	/** {@code interaction_type_id} as text, if present. */
	public Optional<String> interactionId() {
		return params.string("interaction_type_id");
	}
}
