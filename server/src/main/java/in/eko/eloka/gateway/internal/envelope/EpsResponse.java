package in.eko.eloka.gateway.internal.envelope;

/** What a handler returns. The writer turns it into the HTTP response. */
public sealed interface EpsResponse {

	/** Any JSON body (an envelope, or a bare array/object for the few calls that need one). */
	record Json(Object body, int httpStatus) implements EpsResponse {

		public static Json ok(Object body) {
			return new Json(body, 200);
		}
	}

	/** A downloadable file wrapped as {@code {file:{blob, name, content-type}}}. */
	record File(byte[] bytes, String name, String contentType) implements EpsResponse {
	}

	/** Raw bytes (only for the report endpoint that the client saves as a blob). */
	record Binary(byte[] bytes, String name, String contentType) implements EpsResponse {
	}
}
