package in.eko.eloka.gateway.internal.dispatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import in.eko.eloka.gateway.internal.envelope.EpsRequest;
import in.eko.eloka.gateway.internal.envelope.TfRoute;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Collects every {@link EpsHandler} bean and finds the one for a request.
 * Startup fails on a duplicate route or a handler without an access policy, so mistakes
 * show up at boot, not in production traffic.
 */
@Component
public class HandlerRegistry {

	/** A matched handler plus the label used in logs. */
	public record Match(EpsHandler handler, String label) {
	}

	private record TfEntry(RouteKey.Tf key, PathPattern pattern, EpsHandler handler) {
	}

	private final Map<String, EpsHandler> paths = new HashMap<>();
	private final Map<String, EpsHandler> interactions = new HashMap<>();
	private final List<TfEntry> tfRoutes = new ArrayList<>();

	public HandlerRegistry(List<EpsHandler> handlers) {
		PathPatternParser parser = PathPatternParser.defaultInstance;
		for (EpsHandler handler : handlers) {
			if (handler.access() == null) {
				throw new IllegalStateException(handler.getClass().getSimpleName() + " has no AccessPolicy");
			}
			for (RouteKey key : handler.routes()) {
				switch (key) {
					case RouteKey.Path p -> register(paths, p.path(), handler, key);
					case RouteKey.Interaction i -> register(interactions, i.id(), handler, key);
					case RouteKey.Tf t -> {
						boolean duplicate = tfRoutes.stream().anyMatch(e -> e.key().equals(t));
						if (duplicate) {
							throw new IllegalStateException("Duplicate EPS route " + key);
						}
						tfRoutes.add(new TfEntry(t, parser.parse(t.uriPattern()), handler));
					}
				}
			}
		}
	}

	/** Order: fixed path, then tf route, then interaction id. */
	public Optional<Match> find(EpsRequest request) {
		EpsHandler byPath = paths.get(request.path());
		if (byPath != null) {
			return Optional.of(new Match(byPath, request.path()));
		}
		if (request.tf().isPresent()) {
			TfRoute tf = request.tf().get();
			PathContainer uri = PathContainer.parsePath(tf.uri());
			for (TfEntry entry : tfRoutes) {
				if (entry.key().root().equals(tf.root()) && entry.key().method().equals(tf.method())
						&& entry.pattern().matches(uri)) {
					return Optional.of(new Match(entry.handler(), "tf " + tf));
				}
			}
		}
		return request.interactionId()
			.map(id -> interactions.get(id) == null ? null : new Match(interactions.get(id), "#" + id));
	}

	/** Every registered route (used by the access-policy test). */
	public List<RouteKey> allRoutes() {
		List<RouteKey> all = new ArrayList<>();
		paths.keySet().forEach(p -> all.add(new RouteKey.Path(p)));
		interactions.keySet().forEach(i -> all.add(new RouteKey.Interaction(i)));
		tfRoutes.forEach(t -> all.add(t.key()));
		return all;
	}

	private static void register(Map<String, EpsHandler> map, String key, EpsHandler handler, RouteKey route) {
		if (map.putIfAbsent(key, handler) != null) {
			throw new IllegalStateException("Duplicate EPS route " + route);
		}
	}
}
