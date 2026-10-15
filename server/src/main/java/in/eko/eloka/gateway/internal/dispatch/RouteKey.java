package in.eko.eloka.gateway.internal.dispatch;

import org.springframework.http.HttpMethod;

/** How a request finds its handler. */
public sealed interface RouteKey {

	/** {@code interaction_type_id} in the body. */
	record Interaction(String id) implements RouteKey {

		@Override
		public String toString() {
			return "#" + id;
		}
	}

	/** tf-req tunnel call. {@code uriPattern} may use {@code {var}} segments. */
	record Tf(String root, HttpMethod method, String uriPattern) implements RouteKey {

		@Override
		public String toString() {
			return "tf " + method + " " + root + uriPattern;
		}
	}

	/** A fixed URL path such as {@code /authentication/revoke}. */
	record Path(String path) implements RouteKey {

		@Override
		public String toString() {
			return path;
		}
	}

	static RouteKey interaction(int id) {
		return new Interaction(Integer.toString(id));
	}

	static RouteKey tf(String root, HttpMethod method, String uriPattern) {
		return new Tf(root, method, uriPattern);
	}

	static RouteKey path(String path) {
		return new Path(path);
	}
}
