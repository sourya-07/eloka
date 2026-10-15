package in.eko.eloka.gateway.internal.envelope;

import org.springframework.http.HttpMethod;

/**
 * Target of a "tf-req" call: the EPS REST tunnel.
 *
 * @param root e.g. {@code /ekoicici/v1}
 * @param method HTTP method EPS would have used
 * @param uri path below the root, without query string, e.g. {@code /network/agents}
 */
public record TfRoute(String root, HttpMethod method, String uri) {

	@Override
	public String toString() {
		return method + " " + root + uri;
	}
}
